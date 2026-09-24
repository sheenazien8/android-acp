import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { after, before, test } from 'node:test';
import { WebSocket } from 'ws';
import { parseArgs, startBridge, tokenMatches } from '../src/server.js';

const ECHO_AGENT = `
const rl = require('node:readline').createInterface({ input: process.stdin });
rl.on('line', (line) => {
  const message = JSON.parse(line);
  process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: message.id, result: { echoed: message.method } }) + '\\n');
});
`;

let bridge;
let url;
let cwd;

before(async () => {
  cwd = await fs.realpath(await fs.mkdtemp(path.join(os.tmpdir(), 'lakasir-server-')));
  await fs.writeFile(path.join(cwd, 'a.txt'), 'A');
  bridge = startBridge({ host: '127.0.0.1', port: 0, path: '/acp', allowedRoots: [], command: [process.execPath, '-e', ECHO_AGENT] });
  await new Promise((resolve) => bridge.server.on('listening', resolve));
  url = `ws://127.0.0.1:${bridge.address().port}/acp`;
});

after(async () => {
  bridge.close();
  await fs.rm(cwd, { recursive: true, force: true });
});

function connect() {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    const replies = new Map();
    socket.on('message', (data) => {
      const message = JSON.parse(data.toString());
      replies.get(message.id)?.(message);
    });
    socket.on('open', () => resolve({
      socket,
      request(id, method, params) {
        return new Promise((done) => {
          replies.set(id, done);
          socket.send(JSON.stringify({ jsonrpc: '2.0', id, method, params }));
        });
      },
    }));
    socket.on('error', reject);
  });
}

test('extension methods are answered by the bridge, the rest goes to the agent', async () => {
  const client = await connect();
  const agent = await client.request(1, 'initialize', {});
  assert.deepEqual(agent.result, { echoed: 'initialize' });
  const hello = await client.request(2, '_lakasir/hello', {});
  assert.equal(hello.result.fs, true);
  const list = await client.request(3, '_lakasir/fs/list', { cwd, path: '' });
  assert.deepEqual(list.result.entries.map((it) => it.name), ['a.txt']);
  client.socket.close();
});

test('parseArgs reads options and the agent command', () => {
  const options = parseArgs(['--port', '9000', '--allowed-roots', '/a:/b', '--', 'pi-acp', '--flag'], {});
  assert.equal(options.port, 9000);
  assert.deepEqual(options.allowedRoots, ['/a', '/b']);
  assert.deepEqual(options.command, ['pi-acp', '--flag']);
  assert.throws(() => parseArgs(['--port', '9000'], {}), /Missing agent command/);
  assert.deepEqual(parseArgs(['--', 'x'], { LAKASIR_ALLOWED_ROOTS: '/r' }).allowedRoots, ['/r']);
});

test('a bridge with a token rejects missing and wrong tokens before starting the agent', async () => {
  const token = 'correct-horse-battery-staple';
  const secured = startBridge({
    host: '127.0.0.1', port: 0, path: '/acp', allowedRoots: [], token,
    command: [process.execPath, '-e', ECHO_AGENT],
  });
  await new Promise((resolve) => secured.server.on('listening', resolve));
  const address = `ws://127.0.0.1:${secured.address().port}/acp`;

  const attempt = (headers) => new Promise((resolve) => {
    const socket = new WebSocket(address, { headers });
    socket.on('unexpected-response', (_request, response) => resolve(response.statusCode));
    socket.on('open', () => {
      socket.close();
      resolve(101);
    });
    socket.on('error', () => {});
  });

  try {
    assert.equal(await attempt({}), 401);
    assert.equal(await attempt({ Authorization: 'Bearer wrong-token-value-here' }), 401);
    assert.equal(await attempt({ Authorization: token }), 401);
    assert.equal(secured.spawnCount(), 0);
    assert.equal(await attempt({ Authorization: `Bearer ${token}` }), 101);
    assert.equal(secured.spawnCount(), 1);
  } finally {
    secured.close();
  }
});

test('token and tls options are validated', () => {
  assert.equal(parseArgs(['--token', 'x'.repeat(16), '--', 'a'], {}).token, 'x'.repeat(16));
  assert.equal(parseArgs(['--', 'a'], { LAKASIR_TOKEN: 'y'.repeat(20) }).token, 'y'.repeat(20));
  assert.throws(() => parseArgs(['--token', 'short', '--', 'a'], {}), /at least 16/);
  assert.throws(() => parseArgs(['--tls-cert', 'c.pem', '--', 'a'], {}), /used together/);
});

test('tokenMatches compares bearer tokens', () => {
  assert.equal(tokenMatches(null, undefined), true);
  assert.equal(tokenMatches('secret-token-1234', 'Bearer secret-token-1234'), true);
  assert.equal(tokenMatches('secret-token-1234', 'bearer secret-token-1234'), true);
  assert.equal(tokenMatches('secret-token-1234', 'Bearer secret-token-12345'), false);
  assert.equal(tokenMatches('secret-token-1234', 'secret-token-1234'), false);
});

test('--tls-cert/--tls-key serve wss with the same token check', async (t) => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'lakasir-tls-'));
  const cert = path.join(dir, 'cert.pem');
  const key = path.join(dir, 'key.pem');
  try {
    execFileSync('openssl', [
      'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1', '-subj', '/CN=localhost',
      '-keyout', key, '-out', cert,
    ], { stdio: 'ignore' });
  } catch {
    t.skip('openssl not available');
    return;
  }
  const token = 'tls-token-value-123456';
  const secured = startBridge({
    host: '127.0.0.1', port: 0, path: '/acp', allowedRoots: [], token, tlsCert: cert, tlsKey: key,
    command: [process.execPath, '-e', ECHO_AGENT],
  });
  await new Promise((resolve) => secured.server.on('listening', resolve));
  try {
    const reply = await new Promise((resolve, reject) => {
      const socket = new WebSocket(`wss://127.0.0.1:${secured.address().port}/acp`, {
        headers: { Authorization: `Bearer ${token}` },
        rejectUnauthorized: false,
      });
      socket.on('open', () => socket.send(JSON.stringify({ jsonrpc: '2.0', id: 1, method: '_lakasir/hello' })));
      socket.on('message', (data) => {
        socket.close();
        resolve(JSON.parse(data.toString()));
      });
      socket.on('error', reject);
    });
    assert.equal(reply.result.fs, true);
  } finally {
    secured.close();
    await fs.rm(dir, { recursive: true, force: true });
  }
});
