#!/usr/bin/env node
import { spawn } from 'node:child_process';
import { createHash, timingSafeEqual } from 'node:crypto';
import { readFileSync, realpathSync } from 'node:fs';
import https from 'node:https';
import readline from 'node:readline';
import { fileURLToPath } from 'node:url';
import { WebSocket, WebSocketServer } from 'ws';
import { createExtensions, isExtensionMethod } from './extensions.js';
import { parseAllowedRoots } from './workspace.js';

const USAGE = `Usage: lakasir-acp-bridge [--host 0.0.0.0] [--port 8767] [--path /acp] [--allowed-roots a:b]
       [--token <secret>] [--tls-cert cert.pem --tls-key key.pem] -- <agent command> [args...]

Example: lakasir-acp-bridge --port 8767 -- pi-acp
Each WebSocket client gets its own agent process. Messages whose method starts with
"_lakasir/" are answered by the bridge; everything else is piped to the agent's stdio.`;

export function parseArgs(argv, env = process.env) {
  const options = {
    host: '0.0.0.0',
    port: 8767,
    path: '/acp',
    allowedRoots: parseAllowedRoots(env.LAKASIR_ALLOWED_ROOTS),
    token: env.LAKASIR_TOKEN || null,
    tlsCert: env.LAKASIR_TLS_CERT || null,
    tlsKey: env.LAKASIR_TLS_KEY || null,
    command: [],
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--') {
      options.command = argv.slice(i + 1);
      break;
    }
    const value = argv[i + 1];
    switch (arg) {
      case '--host': options.host = value; i++; break;
      case '--port': options.port = Number(value); i++; break;
      case '--path': options.path = value; i++; break;
      case '--allowed-roots': options.allowedRoots = parseAllowedRoots(value); i++; break;
      case '--token': options.token = value; i++; break;
      case '--tls-cert': options.tlsCert = value; i++; break;
      case '--tls-key': options.tlsKey = value; i++; break;
      case '-h':
      case '--help': options.help = true; break;
      default: throw new Error(`Unknown option: ${arg}`);
    }
  }
  if (!options.help) {
    if (options.command.length === 0) throw new Error('Missing agent command after --');
    if (!Number.isInteger(options.port) || options.port <= 0) throw new Error('Invalid --port');
    if (options.token !== null && options.token.trim().length < 16) throw new Error('--token must be at least 16 characters');
    if (Boolean(options.tlsCert) !== Boolean(options.tlsKey)) throw new Error('--tls-cert and --tls-key must be used together');
  }
  return options;
}

const log = (...parts) => console.error(new Date().toISOString(), ...parts);

const digest = (value) => createHash('sha256').update(value, 'utf8').digest();

export function tokenMatches(expected, header) {
  if (!expected) return true;
  const match = /^Bearer\s+(.+)$/i.exec(header ?? '');
  if (!match) return false;
  return timingSafeEqual(digest(match[1].trim()), digest(expected));
}

export function isPublicHost(host) {
  return !['127.0.0.1', 'localhost', '::1'].includes(host);
}

export function startBridge(options) {
  const extensions = createExtensions({ allowedRoots: options.allowedRoots });
  const token = options.token ?? null;
  const verifyClient = (info, done) => {
    if (tokenMatches(token, info.req.headers.authorization)) {
      done(true);
      return;
    }
    log(`rejected ${info.req.socket.remoteAddress}: invalid or missing token`);
    done(false, 401, 'Unauthorized', { 'WWW-Authenticate': 'Bearer' });
  };
  const tls = options.tlsCert && options.tlsKey
    ? https.createServer({ cert: readFileSync(options.tlsCert), key: readFileSync(options.tlsKey) })
    : null;
  const server = tls
    ? new WebSocketServer({ server: tls, path: options.path, verifyClient })
    : new WebSocketServer({ host: options.host, port: options.port, path: options.path, verifyClient });
  if (tls) {
    tls.listen(options.port, options.host, () => server.emit('listening'));
  }
  const agents = new Set();
  let spawned = 0;

  server.on('connection', (socket, request) => {
    const peer = request.socket.remoteAddress;
    log(`client ${peer} connected, starting: ${options.command.join(' ')}`);
    const agent = spawn(options.command[0], options.command.slice(1), { stdio: ['pipe', 'pipe', 'inherit'] });
    agents.add(agent);
    spawned++;
    agent.stdin.on('error', (error) => log(`agent stdin: ${error.message}`));

    const lines = readline.createInterface({ input: agent.stdout, crlfDelay: Infinity });
    lines.on('line', (line) => {
      if (line.trim() !== '' && socket.readyState === WebSocket.OPEN) socket.send(line);
    });

    agent.on('error', (error) => {
      log(`agent failed to start: ${error.message}`);
      socket.close(1011, 'Agent failed to start');
    });
    agent.on('exit', (code, signal) => {
      agents.delete(agent);
      log(`agent for ${peer} exited (${signal ?? code})`);
      if (socket.readyState === WebSocket.OPEN) socket.close(1011, 'Agent exited');
    });

    socket.on('message', async (data) => {
      const text = data.toString('utf8');
      let message = null;
      try {
        message = JSON.parse(text);
      } catch {
        message = null;
      }
      if (message !== null && isExtensionMethod(message.method)) {
        if (message.id === undefined || message.id === null) return;
        const response = await extensions.handle(message);
        if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify(response));
        return;
      }
      if (agent.stdin.writable) agent.stdin.write(`${text.replace(/\r?\n/g, ' ')}\n`);
    });

    socket.on('close', () => {
      log(`client ${peer} disconnected`);
      lines.close();
      if (agent.exitCode === null && agent.signalCode === null) agent.kill('SIGTERM');
    });
  });

  server.on('listening', () => {
    const roots = options.allowedRoots.length > 0 ? options.allowedRoots.join(', ') : 'any';
    const scheme = tls ? 'wss' : 'ws';
    log(`listening on ${scheme}://${options.host}:${options.port}${options.path} (allowed roots: ${roots}, token: ${token ? 'required' : 'none'})`);
    if (!token && isPublicHost(options.host)) {
      log('WARNING: no --token set and the bridge listens beyond localhost. Anyone who can reach this port can run the agent and edit files.');
    }
    if (options.allowedRoots.length === 0 && isPublicHost(options.host)) {
      log('WARNING: no --allowed-roots set. Clients can open any folder this user can read.');
    }
  });

  return {
    server,
    address() {
      return (tls ?? server).address();
    },
    spawnCount() {
      return spawned;
    },
    close() {
      agents.forEach((agent) => agent.kill('SIGTERM'));
      server.close();
      tls?.close();
    },
  };
}

const isMain = process.argv[1] !== undefined && realpathSync(process.argv[1]) === fileURLToPath(import.meta.url);

if (isMain) {
  let options;
  try {
    options = parseArgs(process.argv.slice(2));
  } catch (error) {
    console.error(`${error.message}\n\n${USAGE}`);
    process.exit(2);
  }
  if (options.help) {
    console.log(USAGE);
    process.exit(0);
  }
  const bridge = startBridge(options);
  const shutdown = () => {
    bridge.close();
    process.exit(0);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}
