import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { after, before, describe, test } from 'node:test';
import { ErrorCode } from '../src/errors.js';
import { createGitHandlers, parseLog, parseNumstat, parseStatus } from '../src/git.js';

const git = createGitHandlers();
let base;
let repo;

const sh = (cwd, ...args) => execFileSync('git', args, { cwd, stdio: 'pipe' }).toString();
const rejectsWith = (promise, code) => assert.rejects(promise, (error) => error.code === code);

before(async () => {
  base = await fs.realpath(await fs.mkdtemp(path.join(os.tmpdir(), 'lakasir-git-')));
  repo = path.join(base, 'repo');
  await fs.mkdir(path.join(repo, 'app', 'src'), { recursive: true });
  sh(repo, 'init', '-q', '-b', 'main');
  sh(repo, 'config', 'user.name', 'Tester');
  sh(repo, 'config', 'user.email', 'tester@example.com');
  sh(repo, 'config', 'commit.gpgsign', 'false');
  await fs.writeFile(path.join(repo, 'README.md'), 'hello\n');
  await fs.writeFile(path.join(repo, 'app', 'src', 'main.kt'), 'fun main() {}\n');
  await fs.writeFile(path.join(repo, 'app', 'old name.txt'), 'rename me\n');
  sh(repo, 'add', '-A');
  sh(repo, 'commit', '-q', '-m', 'Initial commit', '-m', 'Body line');
});

after(async () => {
  await fs.rm(base, { recursive: true, force: true });
});

describe('parsers', () => {
  test('porcelain v2 with branch headers, renames, conflicts and spaces', () => {
    const output = [
      '# branch.oid 1234567890abcdef',
      '# branch.head feature/x',
      '# branch.upstream origin/feature/x',
      '# branch.ab +2 -1',
      '1 .M N... 100644 100644 100644 aaa bbb app/src/my file.kt',
      '2 R. N... 100644 100644 100644 aaa bbb R100 app/new.txt',
      'app/old.txt',
      'u UU N... 100644 100644 100644 100644 a b c app/conflict.kt',
      '? app/untracked dir/',
      '1 M. N... 100644 100644 100644 aaa bbb other/outside.kt',
      '',
    ].join('\0');
    const status = parseStatus(output, 'app/');
    assert.equal(status.branch, 'feature/x');
    assert.equal(status.oid, '1234567');
    assert.deepEqual([status.upstream, status.ahead, status.behind], ['origin/feature/x', 2, 1]);
    assert.deepEqual(status.files, [
      { path: 'src/my file.kt', origPath: null, index: null, worktree: 'M', conflicted: false, untracked: false },
      { path: 'new.txt', origPath: 'old.txt', index: 'R', worktree: null, conflicted: false, untracked: false },
      { path: 'conflict.kt', origPath: null, index: 'U', worktree: 'U', conflicted: true, untracked: false },
      { path: 'untracked dir/', origPath: null, index: null, worktree: null, conflicted: false, untracked: true },
    ]);
  });

  test('detached head and initial commit', () => {
    const status = parseStatus('# branch.oid (initial)\0# branch.head (detached)\0');
    assert.deepEqual([status.oid, status.branch, status.detached], [null, null, true]);
  });

  test('log and numstat', () => {
    const log = parseLog(Buffer.from('abc\x1fa\x1fMe\x1f10\x1fFirst\x1e\nxyz\x1fx\x1fYou\x1f20\x1fSecond\x1e\n'));
    assert.deepEqual(log.map((it) => [it.shortHash, it.time, it.subject]), [['a', 10000, 'First'], ['x', 20000, 'Second']]);
    assert.deepEqual(parseNumstat(Buffer.from('3\t1\ta.kt\n-\t-\timg.png\n')), [
      { path: 'a.kt', additions: 3, deletions: 1 },
      { path: 'img.png', additions: null, deletions: null },
    ]);
  });
});

describe('git handlers', () => {
  test('non-repository folder reports isRepo false', async () => {
    const plain = path.join(base, 'plain');
    await fs.mkdir(plain);
    assert.deepEqual(await git.status({ cwd: plain }), { isRepo: false, files: [] });
    await rejectsWith(git.log({ cwd: plain }), ErrorCode.NOT_A_REPO);
  });

  test('status paths are relative to a cwd inside the repo', async () => {
    await fs.writeFile(path.join(repo, 'app', 'src', 'main.kt'), 'fun main() { println() }\n');
    await fs.writeFile(path.join(repo, 'README.md'), 'changed outside cwd\n');
    await fs.writeFile(path.join(repo, 'app', 'new.kt'), 'new\n');
    const status = await git.status({ cwd: path.join(repo, 'app') });
    assert.equal(status.branch, 'main');
    assert.deepEqual(status.files.map((it) => [it.path, it.worktree, it.untracked]), [
      ['src/main.kt', 'M', false],
      ['new.kt', null, true],
    ]);
  });

  test('unstaged and staged diffs return both sides', async () => {
    const cwd = path.join(repo, 'app');
    const unstaged = await git.diff({ cwd, path: 'src/main.kt', staged: false });
    assert.deepEqual([unstaged.oldText, unstaged.newText], ['fun main() {}\n', 'fun main() { println() }\n']);
    const untracked = await git.diff({ cwd, path: 'new.kt', staged: false });
    assert.deepEqual([untracked.oldText, untracked.newText], [null, 'new\n']);

    await git.stage({ cwd, paths: ['src/main.kt', 'new.kt'] });
    const staged = await git.diff({ cwd, path: 'src/main.kt', staged: true });
    assert.deepEqual([staged.oldText, staged.newText], ['fun main() {}\n', 'fun main() { println() }\n']);
    const added = await git.diff({ cwd, path: 'new.kt', staged: true });
    assert.equal(added.oldText, null);

    await git.unstage({ cwd, paths: ['new.kt'] });
    const after = await git.status({ cwd });
    assert.deepEqual(after.files.map((it) => [it.path, it.index, it.untracked]), [
      ['src/main.kt', 'M', false],
      ['new.kt', null, true],
    ]);
  });

  test('stage rejects paths outside the workspace', async () => {
    await rejectsWith(git.stage({ cwd: path.join(repo, 'app'), paths: ['../README.md'] }), ErrorCode.OUTSIDE_WORKSPACE);
    await rejectsWith(git.stage({ cwd: repo, paths: [] }), ErrorCode.INVALID_PARAMS);
  });

  test('renames stage both paths and diff against the old path', async () => {
    const cwd = path.join(repo, 'app');
    await fs.rename(path.join(cwd, 'old name.txt'), path.join(cwd, 'new name.txt'));
    await git.stage({ cwd, paths: ['old name.txt', 'new name.txt'] });
    const status = await git.status({ cwd });
    const renamed = status.files.find((it) => it.index === 'R');
    assert.deepEqual([renamed.path, renamed.origPath], ['new name.txt', 'old name.txt']);
    const diff = await git.diff({ cwd, path: 'new name.txt', origPath: 'old name.txt', staged: true });
    assert.deepEqual([diff.oldText, diff.newText], ['rename me\n', 'rename me\n']);
  });

  test('commit, log and show', async () => {
    const cwd = path.join(repo, 'app');
    await rejectsWith(git.commit({ cwd, message: '  ' }), ErrorCode.INVALID_PARAMS);
    const result = await git.commit({ cwd, message: 'Update main\n\nWith details' });
    assert.equal(result.shortHash, result.hash.slice(0, 7));
    await rejectsWith(git.commit({ cwd, message: 'again' }), ErrorCode.NOTHING_STAGED);

    const { commits } = await git.log({ cwd, limit: 1 });
    assert.deepEqual([commits[0].subject, commits[0].author], ['Update main', 'Tester']);
    const older = await git.log({ cwd, limit: 5, skip: 1 });
    assert.equal(older.commits[0].subject, 'Initial commit');

    const detail = await git.show({ cwd, hash: result.hash });
    assert.deepEqual([detail.subject, detail.body, detail.email], ['Update main', 'With details', 'tester@example.com']);
    assert.ok(detail.files.some((it) => it.path === 'app/src/main.kt' && it.additions === 1 && it.deletions === 1));
    await rejectsWith(git.show({ cwd, hash: '--help' }), ErrorCode.INVALID_PARAMS);
  });

  test('empty repository has no log and unstage works without HEAD', async () => {
    const empty = path.join(base, 'empty');
    await fs.mkdir(empty);
    sh(empty, 'init', '-q');
    await fs.writeFile(path.join(empty, 'a.txt'), 'a\n');
    assert.deepEqual(await git.log({ cwd: empty }), { commits: [] });
    await git.stage({ cwd: empty, paths: ['a.txt'] });
    assert.equal((await git.status({ cwd: empty })).files[0].index, 'A');
    await git.unstage({ cwd: empty, paths: ['a.txt'] });
    assert.equal((await git.status({ cwd: empty })).files[0].untracked, true);
  });
});
