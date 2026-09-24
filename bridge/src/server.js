#!/usr/bin/env node
import { spawn } from 'node:child_process';
import { realpathSync } from 'node:fs';
import readline from 'node:readline';
import { fileURLToPath } from 'node:url';
import { WebSocket, WebSocketServer } from 'ws';
import { createExtensions, isExtensionMethod } from './extensions.js';
import { parseAllowedRoots } from './workspace.js';

const USAGE = `Usage: lakasir-acp-bridge [--host 0.0.0.0] [--port 8767] [--path /acp] [--allowed-roots a:b] -- <agent command> [args...]

Example: lakasir-acp-bridge --port 8767 -- pi-acp
Each WebSocket client gets its own agent process. Messages whose method starts with
"_lakasir/" are answered by the bridge; everything else is piped to the agent's stdio.`;

export function parseArgs(argv, env = process.env) {
  const options = {
    host: '0.0.0.0',
    port: 8767,
    path: '/acp',
    allowedRoots: parseAllowedRoots(env.LAKASIR_ALLOWED_ROOTS),
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
      case '-h':
      case '--help': options.help = true; break;
      default: throw new Error(`Unknown option: ${arg}`);
    }
  }
  if (!options.help) {
    if (options.command.length === 0) throw new Error('Missing agent command after --');
    if (!Number.isInteger(options.port) || options.port <= 0) throw new Error('Invalid --port');
  }
  return options;
}

const log = (...parts) => console.error(new Date().toISOString(), ...parts);

export function startBridge(options) {
  const extensions = createExtensions({ allowedRoots: options.allowedRoots });
  const server = new WebSocketServer({ host: options.host, port: options.port, path: options.path });
  const agents = new Set();

  server.on('connection', (socket, request) => {
    const peer = request.socket.remoteAddress;
    log(`client ${peer} connected, starting: ${options.command.join(' ')}`);
    const agent = spawn(options.command[0], options.command.slice(1), { stdio: ['pipe', 'pipe', 'inherit'] });
    agents.add(agent);
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
    log(`listening on ws://${options.host}:${options.port}${options.path} (allowed roots: ${roots})`);
  });

  return {
    server,
    close() {
      agents.forEach((agent) => agent.kill('SIGTERM'));
      server.close();
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
