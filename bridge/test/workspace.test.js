import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { after, before, describe, test } from 'node:test';
import { ErrorCode } from '../src/errors.js';
import { createExtensions } from '../src/extensions.js';
import { createWorkspaceHandlers, resolveInside, resolveRoot } from '../src/workspace.js';

let base;
let root;
let outsideDir;

before(async () => {
  base = await fs.realpath(await fs.mkdtemp(path.join(os.tmpdir(), 'lakasir-bridge-')));
  root = path.join(base, 'project');
  outsideDir = path.join(base, 'outside');
  await fs.mkdir(path.join(root, 'src', 'deep'), { recursive: true });
  await fs.mkdir(path.join(root, '.git'));
  await fs.mkdir(outsideDir);
  await fs.writeFile(path.join(root, 'README.md'), '# hello\n');
  await fs.writeFile(path.join(root, 'src', 'main.kt'), 'fun main() {}\n');
  await fs.writeFile(path.join(root, 'image.bin'), Buffer.from([1, 2, 0, 3]));
  await fs.writeFile(path.join(outsideDir, 'secret.txt'), 'secret');
  await fs.symlink(outsideDir, path.join(root, 'escape'));
  await fs.symlink(path.join(outsideDir, 'missing.txt'), path.join(root, 'dangling'));
  await fs.symlink(path.join(root, 'src'), path.join(root, 'src-link'));
});

after(async () => {
  await fs.rm(base, { recursive: true, force: true });
});

const rejectsWith = (promise, code) => assert.rejects(promise, (error) => error.code === code);

describe('path guard', () => {
  test('allows nested paths and the root', async () => {
    assert.equal(await resolveInside(root, 'src/deep'), path.join(root, 'src', 'deep'));
    assert.equal(await resolveInside(root, ''), root);
    assert.equal(await resolveInside(root, '.'), root);
    assert.equal(await resolveInside(root, 'src/new-file.txt'), path.join(root, 'src', 'new-file.txt'));
  });

  test('rejects parent traversal and absolute paths', async () => {
    await rejectsWith(resolveInside(root, '../outside/secret.txt'), ErrorCode.OUTSIDE_WORKSPACE);
    await rejectsWith(resolveInside(root, 'src/../../outside'), ErrorCode.OUTSIDE_WORKSPACE);
    await rejectsWith(resolveInside(root, '/etc/passwd'), ErrorCode.OUTSIDE_WORKSPACE);
  });

  test('rejects symlinks that leave the workspace', async () => {
    await rejectsWith(resolveInside(root, 'escape'), ErrorCode.OUTSIDE_WORKSPACE);
    await rejectsWith(resolveInside(root, 'escape/secret.txt'), ErrorCode.OUTSIDE_WORKSPACE);
    await rejectsWith(resolveInside(root, 'escape/new.txt'), ErrorCode.OUTSIDE_WORKSPACE);
    await rejectsWith(resolveInside(root, 'dangling'), ErrorCode.OUTSIDE_WORKSPACE);
  });

  test('allows symlinks inside the workspace', async () => {
    assert.equal(await resolveInside(root, 'src-link/main.kt'), path.join(root, 'src-link', 'main.kt'));
  });

  test('a link itself can be addressed without following it', async () => {
    assert.equal(await resolveInside(root, 'escape', { follow: false }), path.join(root, 'escape'));
  });

  test('cwd must be absolute and inside allowed roots', async () => {
    await rejectsWith(resolveRoot('relative/path'), ErrorCode.INVALID_PARAMS);
    await rejectsWith(resolveRoot(path.join(base, 'nope')), ErrorCode.NOT_FOUND);
    await rejectsWith(resolveRoot(root, [outsideDir]), ErrorCode.OUTSIDE_WORKSPACE);
    assert.equal(await resolveRoot(path.join(root, 'src'), [root]), path.join(root, 'src'));
  });
});

describe('fs handlers', () => {
  const ws = createWorkspaceHandlers();

  test('list sorts folders first and hides .git', async () => {
    const { entries, truncated } = await ws.list({ cwd: root, path: '' });
    assert.equal(truncated, false);
    const names = entries.map((it) => it.name);
    assert.ok(!names.includes('.git'));
    assert.deepEqual(names.slice(0, 2), ['src', 'src-link']);
    const escape = entries.find((it) => it.name === 'escape');
    assert.equal(escape.type, 'other');
    assert.equal(escape.symlink, true);
    const readme = entries.find((it) => it.name === 'README.md');
    assert.deepEqual([readme.type, readme.path, readme.size], ['file', 'README.md', 8]);
  });

  test('read returns text, binary and truncated flags', async () => {
    const text = await ws.read({ cwd: root, path: 'src/main.kt' });
    assert.equal(text.text, 'fun main() {}\n');
    assert.equal(text.binary, false);
    const binary = await ws.read({ cwd: root, path: 'image.bin' });
    assert.equal(binary.binary, true);
    assert.equal(binary.text, null);
    const cut = await ws.read({ cwd: root, path: 'README.md', maxBytes: 3 });
    assert.deepEqual([cut.text, cut.truncated, cut.size], ['# h', true, 8]);
    await rejectsWith(ws.read({ cwd: root, path: 'escape/secret.txt' }), ErrorCode.OUTSIDE_WORKSPACE);
    await rejectsWith(ws.read({ cwd: root, path: 'missing.txt' }), 'ENOENT');
  });

  test('write checks expectedMtime', async () => {
    const file = await ws.read({ cwd: root, path: 'README.md' });
    const saved = await ws.write({ cwd: root, path: 'README.md', text: '# changed\n', expectedMtime: file.mtime });
    assert.equal(saved.size, 10);
    await rejectsWith(
      ws.write({ cwd: root, path: 'README.md', text: 'x', expectedMtime: saved.mtime - 5000 }),
      ErrorCode.CONFLICT,
    );
    await rejectsWith(ws.write({ cwd: root, path: 'gone.md', text: 'x', expectedMtime: 1 }), ErrorCode.CONFLICT);
    await ws.write({ cwd: root, path: 'README.md', text: 'forced\n' });
    assert.equal(await fs.readFile(path.join(root, 'README.md'), 'utf8'), 'forced\n');
  });

  test('write cannot follow a dangling link out of the workspace', async () => {
    await rejectsWith(ws.write({ cwd: root, path: 'dangling', text: 'x' }), ErrorCode.OUTSIDE_WORKSPACE);
    await assert.rejects(fs.access(path.join(outsideDir, 'missing.txt')));
  });

  test('create, rename and delete', async () => {
    const created = await ws.create({ cwd: root, path: 'src/notes.txt', type: 'file' });
    assert.deepEqual([created.path, created.type], ['src/notes.txt', 'file']);
    await rejectsWith(ws.create({ cwd: root, path: 'src/notes.txt', type: 'file' }), 'EEXIST');
    await ws.create({ cwd: root, path: 'tmp', type: 'dir' });

    await rejectsWith(ws.rename({ cwd: root, from: 'src/notes.txt', to: 'README.md' }), ErrorCode.ALREADY_EXISTS);
    const renamed = await ws.rename({ cwd: root, from: 'src/notes.txt', to: 'tmp/notes.txt' });
    assert.equal(renamed.path, 'tmp/notes.txt');

    await rejectsWith(ws.delete({ cwd: root, path: 'tmp' }), 'ENOTEMPTY');
    await ws.delete({ cwd: root, path: 'tmp', recursive: true });
    await assert.rejects(fs.access(path.join(root, 'tmp')));
  });

  test('the workspace root cannot be deleted or renamed', async () => {
    await rejectsWith(ws.delete({ cwd: root, path: '.', recursive: true }), ErrorCode.INVALID_PARAMS);
    await rejectsWith(ws.rename({ cwd: root, from: '.', to: 'x' }), ErrorCode.INVALID_PARAMS);
  });

  test('deleting a link removes the link only', async () => {
    await fs.symlink(outsideDir, path.join(root, 'escape2'));
    await ws.delete({ cwd: root, path: 'escape2', recursive: true });
    assert.equal(await fs.readFile(path.join(outsideDir, 'secret.txt'), 'utf8'), 'secret');
  });
});

describe('extension dispatch', () => {
  const extensions = createExtensions();

  test('hello reports features', async () => {
    const response = await extensions.handle({ id: 1, method: '_lakasir/hello' });
    assert.deepEqual(response, { jsonrpc: '2.0', id: 1, result: { version: 1, fs: true, git: false } });
  });

  test('unknown method returns method not found', async () => {
    const response = await extensions.handle({ id: 'a', method: '_lakasir/nope' });
    assert.equal(response.error.code, ErrorCode.METHOD_NOT_FOUND);
  });

  test('errors are mapped to JSON-RPC errors', async () => {
    const missing = await extensions.handle({ id: 2, method: '_lakasir/fs/read', params: { cwd: root, path: 'nope' } });
    assert.equal(missing.error.code, ErrorCode.NOT_FOUND);
    const escaped = await extensions.handle({ id: 3, method: '_lakasir/fs/list', params: { cwd: root, path: '..' } });
    assert.equal(escaped.error.code, ErrorCode.OUTSIDE_WORKSPACE);
  });
});
