import { spawn } from 'node:child_process';
import fs from 'node:fs/promises';
import { ErrorCode, RpcError } from './errors.js';
import { DEFAULT_MAX_BYTES, resolveInside, resolveRoot } from './workspace.js';

export const GIT_TIMEOUT_MS = 20_000;
export const COMMIT_TIMEOUT_MS = 60_000;
export const MAX_STATUS_FILES = 1000;
export const MAX_LOG_COMMITS = 200;
const MAX_OUTPUT_BYTES = 16 * 1024 * 1024;
const BINARY_SNIFF_BYTES = 8192;
const FIELD = '\x1f';
const RECORD = '\x1e';
const HASH = /^[0-9a-fA-F]{4,64}$/;

const GIT_ENV = { GIT_TERMINAL_PROMPT: '0', GIT_OPTIONAL_LOCKS: '0', LC_ALL: 'C' };

export function runGit(cwd, args, { timeoutMs = GIT_TIMEOUT_MS, input } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn('git', ['-C', cwd, ...args], {
      env: { ...process.env, ...GIT_ENV },
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    const stdout = [];
    const stderr = [];
    let size = 0;
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      child.kill('SIGKILL');
    }, timeoutMs);
    child.stdout.on('data', (chunk) => {
      size += chunk.length;
      if (size <= MAX_OUTPUT_BYTES) stdout.push(chunk);
    });
    child.stderr.on('data', (chunk) => stderr.push(chunk));
    child.on('error', (error) => {
      clearTimeout(timer);
      reject(error.code === 'ENOENT'
        ? new RpcError(ErrorCode.GIT_ERROR, 'git is not installed on the bridge machine')
        : error);
    });
    child.on('close', (code) => {
      clearTimeout(timer);
      if (timedOut) {
        reject(new RpcError(ErrorCode.GIT_ERROR, `git ${args[0]} timed out`));
        return;
      }
      resolve({ code, stdout: Buffer.concat(stdout), stderr: Buffer.concat(stderr).toString('utf8') });
    });
    child.stdin.on('error', () => {});
    child.stdin.end(input ?? '');
  });
}

export function gitError(result, fallback = 'git failed') {
  const stderr = result.stderr.trim();
  if (/not a git repository/i.test(stderr)) return new RpcError(ErrorCode.NOT_A_REPO, 'Not a git repository');
  if (/index\.lock/.test(stderr)) {
    return new RpcError(ErrorCode.GIT_ERROR, 'Git is busy (index.lock exists). Try again in a moment.', { stderr });
  }
  const line = stderr.split('\n').map((it) => it.replace(/^(fatal|error):\s*/, '').trim()).find(Boolean);
  return new RpcError(ErrorCode.GIT_ERROR, line ?? fallback, { stderr });
}

async function git(cwd, args, options) {
  const result = await runGit(cwd, args, options);
  if (result.code !== 0) throw gitError(result, `git ${args[0]} failed`);
  return result.stdout;
}

let availability = null;

export function gitAvailable() {
  availability ??= runGit(process.cwd(), ['--version']).then((it) => it.code === 0, () => false);
  return availability;
}

const letter = (value) => (value === '.' ? null : value);

export function parseStatus(output, prefix = '') {
  const records = output.split('\0');
  const status = {
    isRepo: true,
    branch: null,
    detached: false,
    oid: null,
    upstream: null,
    ahead: 0,
    behind: 0,
    files: [],
    truncated: false,
  };
  const relative = (repoPath) => {
    if (!repoPath.startsWith(prefix)) return null;
    return repoPath.slice(prefix.length);
  };
  const push = (file) => {
    if (file.path === null || file.path === '') return;
    if (status.files.length >= MAX_STATUS_FILES) {
      status.truncated = true;
      return;
    }
    status.files.push(file);
  };

  for (let i = 0; i < records.length; i++) {
    const record = records[i];
    if (record === '') continue;
    if (record.startsWith('# ')) {
      const [key, ...rest] = record.slice(2).split(' ');
      const value = rest.join(' ');
      if (key === 'branch.oid') status.oid = value === '(initial)' ? null : value.slice(0, 7);
      if (key === 'branch.head') {
        status.detached = value === '(detached)';
        status.branch = status.detached ? null : value;
      }
      if (key === 'branch.upstream') status.upstream = value;
      if (key === 'branch.ab') {
        const match = /^\+(\d+) -(\d+)$/.exec(value);
        if (match) {
          status.ahead = Number(match[1]);
          status.behind = Number(match[2]);
        }
      }
      continue;
    }
    const type = record[0];
    const fields = record.split(' ');
    if (type === '1') {
      const xy = fields[1];
      push({ path: relative(fields.slice(8).join(' ')), origPath: null, index: letter(xy[0]), worktree: letter(xy[1]), conflicted: false, untracked: false });
    } else if (type === '2') {
      const xy = fields[1];
      const origPath = records[++i] ?? '';
      push({ path: relative(fields.slice(9).join(' ')), origPath: relative(origPath), index: letter(xy[0]), worktree: letter(xy[1]), conflicted: false, untracked: false });
    } else if (type === 'u') {
      const xy = fields[1];
      push({ path: relative(fields.slice(10).join(' ')), origPath: null, index: xy[0], worktree: xy[1], conflicted: true, untracked: false });
    } else if (type === '?') {
      push({ path: relative(record.slice(2)), origPath: null, index: null, worktree: null, conflicted: false, untracked: true });
    }
  }
  return status;
}

export function parseLog(output) {
  return output
    .toString('utf8')
    .split(RECORD)
    .map((it) => it.replace(/^\n/, ''))
    .filter((it) => it.trim() !== '')
    .map((it) => {
      const [hash, shortHash, author, time, subject] = it.split(FIELD);
      return { hash, shortHash, author, time: Number(time) * 1000, subject: subject ?? '' };
    });
}

export function parseNumstat(output) {
  return output
    .toString('utf8')
    .split('\n')
    .filter((it) => it.trim() !== '')
    .map((line) => {
      const [added, deleted, ...rest] = line.split('\t');
      return {
        path: rest.join('\t'),
        additions: added === '-' ? null : Number(added),
        deletions: deleted === '-' ? null : Number(deleted),
      };
    });
}

function decodeSide(buffer) {
  if (buffer === null) return { text: null, binary: false, truncated: false };
  const binary = buffer.subarray(0, BINARY_SNIFF_BYTES).includes(0);
  const truncated = buffer.length > DEFAULT_MAX_BYTES;
  return { text: binary ? null : buffer.subarray(0, DEFAULT_MAX_BYTES).toString('utf8'), binary, truncated };
}

async function blob(cwd, spec) {
  const result = await runGit(cwd, ['show', spec]);
  return result.code === 0 ? result.stdout : null;
}

function requirePaths(value) {
  if (!Array.isArray(value) || value.length === 0 || value.some((it) => typeof it !== 'string' || it.trim() === '')) {
    throw new RpcError(ErrorCode.INVALID_PARAMS, 'paths must be a non-empty list');
  }
  return value;
}

const pathspec = (relative) => `./${relative.replace(/\/+$/, '')}`;

export function createGitHandlers({ allowedRoots = [] } = {}) {
  const rootOf = (params) => resolveRoot(params?.cwd, allowedRoots);

  async function guardPaths(root, paths) {
    await Promise.all(paths.map((it) => resolveInside(root, it, { follow: false })));
    return paths.map(pathspec);
  }

  async function hasHead(root) {
    const result = await runGit(root, ['rev-parse', '--verify', '-q', 'HEAD']);
    if (result.code === 0) return true;
    const error = gitError(result);
    if (error.code === ErrorCode.NOT_A_REPO) throw error;
    return false;
  }

  return {
    async status(params) {
      const root = await rootOf(params);
      const prefixResult = await runGit(root, ['rev-parse', '--show-prefix']);
      if (prefixResult.code !== 0) {
        const error = gitError(prefixResult);
        if (error.code === ErrorCode.NOT_A_REPO) return { isRepo: false, files: [] };
        throw error;
      }
      const prefix = prefixResult.stdout.toString('utf8').trim();
      const output = await git(root, ['status', '--porcelain=v2', '--branch', '-z', '--', '.']);
      return parseStatus(output.toString('utf8'), prefix);
    },

    async diff(params) {
      const root = await rootOf(params);
      if (typeof params.path !== 'string' || params.path === '') throw new RpcError(ErrorCode.INVALID_PARAMS, 'path is required');
      const file = await resolveInside(root, params.path, { follow: false });
      const origPath = typeof params.origPath === 'string' && params.origPath !== '' ? params.origPath : null;
      if (origPath !== null) await resolveInside(root, origPath, { follow: false });
      const spec = pathspec(params.path);
      const oldSpec = origPath !== null ? pathspec(origPath) : spec;

      let oldBuffer;
      let newBuffer;
      if (params.staged === true) {
        oldBuffer = await blob(root, `HEAD:${oldSpec}`);
        newBuffer = (await blob(root, `:${spec}`)) ?? Buffer.alloc(0);
      } else {
        oldBuffer = await blob(root, `:${spec}`);
        newBuffer = await fs.readFile(file).catch((error) => {
          if (error.code === 'ENOENT') return Buffer.alloc(0);
          throw error;
        });
      }
      const oldSide = decodeSide(oldBuffer);
      const newSide = decodeSide(newBuffer);
      const binary = oldSide.binary || newSide.binary;
      return {
        path: params.path,
        oldText: binary ? null : oldSide.text,
        newText: binary ? '' : newSide.text ?? '',
        binary,
        truncated: oldSide.truncated || newSide.truncated,
      };
    },

    async log(params) {
      const root = await rootOf(params);
      const limit = Number.isInteger(params.limit) ? Math.min(Math.max(params.limit, 1), MAX_LOG_COMMITS) : 30;
      const skip = Number.isInteger(params.skip) && params.skip > 0 ? params.skip : 0;
      if (!(await hasHead(root))) return { commits: [] };
      const format = ['%H', '%h', '%an', '%at', '%s'].join('%x1f') + '%x1e';
      const output = await git(root, ['log', `--max-count=${limit}`, `--skip=${skip}`, `--format=${format}`, '--', '.']);
      return { commits: parseLog(output) };
    },

    async show(params) {
      const root = await rootOf(params);
      if (typeof params.hash !== 'string' || !HASH.test(params.hash)) throw new RpcError(ErrorCode.INVALID_PARAMS, 'hash is invalid');
      const format = ['%H', '%h', '%an', '%ae', '%at', '%s', '%b'].join('%x1f');
      const header = (await git(root, ['show', '--no-patch', `--format=${format}`, params.hash, '--'])).toString('utf8');
      const [hash, shortHash, author, email, time, subject, ...body] = header.split(FIELD);
      const numstat = await git(root, ['diff-tree', '--no-commit-id', '--numstat', '-r', '--root', '-M', hash]);
      return {
        hash,
        shortHash,
        author,
        email,
        time: Number(time) * 1000,
        subject,
        body: body.join(FIELD).trim(),
        files: parseNumstat(numstat),
      };
    },

    async stage(params) {
      const root = await rootOf(params);
      const specs = await guardPaths(root, requirePaths(params.paths));
      await git(root, ['add', '-A', '--', ...specs]);
      return {};
    },

    async unstage(params) {
      const root = await rootOf(params);
      const specs = await guardPaths(root, requirePaths(params.paths));
      if (await hasHead(root)) {
        await git(root, ['restore', '--staged', '--', ...specs]);
      } else {
        await git(root, ['rm', '-r', '-q', '--cached', '--', ...specs]);
      }
      return {};
    },

    async commit(params) {
      const root = await rootOf(params);
      if (typeof params.message !== 'string' || params.message.trim() === '') {
        throw new RpcError(ErrorCode.INVALID_PARAMS, 'message is required');
      }
      const staged = await runGit(root, ['diff', '--cached', '--quiet']);
      if (staged.code === 0) throw new RpcError(ErrorCode.NOTHING_STAGED, 'Nothing is staged');
      if (staged.code !== 1) throw gitError(staged);
      await git(root, ['commit', '-q', '-F', '-'], { timeoutMs: COMMIT_TIMEOUT_MS, input: params.message });
      const head = (await git(root, ['rev-parse', 'HEAD'])).toString('utf8').trim();
      return { hash: head, shortHash: head.slice(0, 7) };
    },
  };
}
