import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { after, before, test } from 'node:test';
import { WebSocket } from 'ws';
import { parseArgs, startBridge } from '../src/server.js';

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
  url = `ws://127.0.0.1:${bridge.server.address().port}/acp`;
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
