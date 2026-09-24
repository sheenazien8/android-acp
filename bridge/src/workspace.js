import fs from 'node:fs/promises';
import path from 'node:path';
import { ErrorCode, RpcError } from './errors.js';

export const DEFAULT_MAX_BYTES = 512 * 1024;
export const MAX_READ_BYTES = 4 * 1024 * 1024;
export const MAX_LIST_ENTRIES = 2000;
const BINARY_SNIFF_BYTES = 8192;
const HIDDEN_NAMES = new Set(['.git']);

const outside = () => new RpcError(ErrorCode.OUTSIDE_WORKSPACE, 'Path is outside the workspace');
const invalid = (message) => new RpcError(ErrorCode.INVALID_PARAMS, message);

export function isInside(root, candidate) {
  if (candidate === root) return true;
  const prefix = root.endsWith(path.sep) ? root : root + path.sep;
  return candidate.startsWith(prefix);
}

export function parseAllowedRoots(value) {
  return (value ?? '').split(path.delimiter).map((it) => it.trim()).filter(Boolean);
}

export async function resolveRoot(cwd, allowedRoots = []) {
  if (typeof cwd !== 'string' || !path.isAbsolute(cwd)) throw invalid('cwd must be an absolute path');
  let root;
  try {
    root = await fs.realpath(cwd);
  } catch {
    throw new RpcError(ErrorCode.NOT_FOUND, 'Workspace folder not found');
  }
  if (allowedRoots.length > 0) {
    const allowed = await Promise.all(allowedRoots.map((it) => fs.realpath(it).catch(() => null)));
    if (!allowed.some((it) => it !== null && isInside(it, root))) throw outside();
  }
  return root;
}

export async function resolveInside(root, relative, { follow = true } = {}) {
  if (typeof relative !== 'string') throw invalid('path must be a string');
  if (path.isAbsolute(relative)) throw outside();
  const candidate = path.resolve(root, relative);
  if (!isInside(root, candidate)) throw outside();
  if (candidate === root) return candidate;

  const checked = follow ? candidate : path.dirname(candidate);
  if (follow) {
    const link = await fs.lstat(candidate).catch(() => null);
    if (link?.isSymbolicLink()) {
      const target = await fs.realpath(candidate).catch(() => null);
      if (target === null || !isInside(root, target)) throw outside();
      return candidate;
    }
  }
  const real = await realpathOfNearestExisting(checked);
  if (!isInside(root, real)) throw outside();
  return candidate;
}

async function realpathOfNearestExisting(target) {
  const missing = [];
  let current = target;
  for (;;) {
    try {
      const real = await fs.realpath(current);
      return path.join(real, ...missing.reverse());
    } catch (error) {
      if (error.code !== 'ENOENT' && error.code !== 'ENOTDIR') throw error;
      const parent = path.dirname(current);
      if (parent === current) throw error;
      missing.push(path.basename(current));
      current = parent;
    }
  }
}

function requirePath(value, name = 'path') {
  if (typeof value !== 'string' || value.trim() === '') throw invalid(`${name} is required`);
  return value;
}

const relativeTo = (root, absolute) => path.relative(root, absolute).split(path.sep).join('/');
const mtimeOf = (stat) => Math.floor(stat.mtimeMs);

async function describe(root, absolute, name) {
  const link = await fs.lstat(absolute);
  let stat = link;
  let type = 'other';
  if (link.isSymbolicLink()) {
    const target = await fs.realpath(absolute).catch(() => null);
    stat = target !== null && isInside(root, target) ? await fs.stat(absolute).catch(() => null) : null;
  }
  if (stat?.isDirectory()) type = 'dir';
  else if (stat?.isFile()) type = 'file';
  return {
    name,
    path: relativeTo(root, absolute),
    type,
    size: stat?.isFile() ? stat.size : 0,
    mtime: mtimeOf(stat ?? link),
    symlink: link.isSymbolicLink(),
  };
}

const typeRank = (entry) => (entry.type === 'dir' ? 0 : 1);

export function sortEntries(entries) {
  return entries.sort((a, b) => typeRank(a) - typeRank(b) || a.name.localeCompare(b.name, 'en', { sensitivity: 'base' }));
}

export function createWorkspaceHandlers({ allowedRoots = [] } = {}) {
  const rootOf = (params) => resolveRoot(params?.cwd, allowedRoots);

  return {
    async list(params) {
      const root = await rootOf(params);
      const dir = await resolveInside(root, params.path ?? '');
      const dirents = await fs.readdir(dir, { withFileTypes: true });
      const visible = dirents.filter((it) => !HIDDEN_NAMES.has(it.name));
      const entries = await Promise.all(
        visible.map((it) => describe(root, path.join(dir, it.name), it.name).catch(() => null)),
      );
      const sorted = sortEntries(entries.filter((it) => it !== null));
      return {
        path: relativeTo(root, dir),
        entries: sorted.slice(0, MAX_LIST_ENTRIES),
        truncated: sorted.length > MAX_LIST_ENTRIES,
      };
    },

    async read(params) {
      const root = await rootOf(params);
      const file = await resolveInside(root, requirePath(params.path));
      const stat = await fs.stat(file);
      if (!stat.isFile()) throw new RpcError(ErrorCode.IO_ERROR, 'Not a file');
      const requested = Number.isInteger(params.maxBytes) && params.maxBytes > 0 ? params.maxBytes : DEFAULT_MAX_BYTES;
      const maxBytes = Math.min(requested, MAX_READ_BYTES);
      const buffer = Buffer.alloc(Math.min(stat.size, maxBytes));
      const handle = await fs.open(file, 'r');
      let bytesRead;
      try {
        ({ bytesRead } = await handle.read(buffer, 0, buffer.length, 0));
      } finally {
        await handle.close();
      }
      const content = buffer.subarray(0, bytesRead);
      const binary = content.subarray(0, BINARY_SNIFF_BYTES).includes(0);
      return {
        path: relativeTo(root, file),
        text: binary ? null : content.toString('utf8'),
        binary,
        truncated: stat.size > maxBytes,
        size: stat.size,
        mtime: mtimeOf(stat),
      };
    },

    async write(params) {
      const root = await rootOf(params);
      const file = await resolveInside(root, requirePath(params.path));
      if (typeof params.text !== 'string') throw invalid('text is required');
      const current = await fs.stat(file).catch((error) => {
        if (error.code === 'ENOENT') return null;
        throw error;
      });
      if (current?.isDirectory()) throw new RpcError(ErrorCode.IO_ERROR, 'Is a folder');
      if (params.expectedMtime !== undefined && params.expectedMtime !== null) {
        if (current === null) throw new RpcError(ErrorCode.CONFLICT, 'The file was deleted on the bridge machine');
        if (mtimeOf(current) !== params.expectedMtime) {
          throw new RpcError(ErrorCode.CONFLICT, 'The file changed on the bridge machine', { mtime: mtimeOf(current) });
        }
      }
      await fs.writeFile(file, params.text, 'utf8');
      const written = await fs.stat(file);
      return { path: relativeTo(root, file), mtime: mtimeOf(written), size: written.size };
    },

    async create(params) {
      const root = await rootOf(params);
      const target = await resolveInside(root, requirePath(params.path), { follow: false });
      if (target === root) throw invalid('path is required');
      if (params.type === 'dir') {
        await fs.mkdir(target);
      } else if (params.type === 'file') {
        await fs.writeFile(target, '', { flag: 'wx' });
      } else {
        throw invalid('type must be "file" or "dir"');
      }
      return describe(root, target, path.basename(target));
    },

    async rename(params) {
      const root = await rootOf(params);
      const from = await resolveInside(root, requirePath(params.from, 'from'), { follow: false });
      const to = await resolveInside(root, requirePath(params.to, 'to'), { follow: false });
      if (from === root || to === root) throw invalid('The workspace folder cannot be renamed');
      await fs.lstat(from);
      if (await fs.lstat(to).then(() => true, () => false)) {
        throw new RpcError(ErrorCode.ALREADY_EXISTS, 'Already exists');
      }
      await fs.rename(from, to);
      return describe(root, to, path.basename(to));
    },

    async delete(params) {
      const root = await rootOf(params);
      const target = await resolveInside(root, requirePath(params.path), { follow: false });
      if (target === root) throw invalid('The workspace folder cannot be deleted');
      const stat = await fs.lstat(target);
      if (stat.isDirectory()) {
        if (params.recursive === true) await fs.rm(target, { recursive: true });
        else await fs.rmdir(target);
      } else {
        await fs.unlink(target);
      }
      return {};
    },
  };
}
