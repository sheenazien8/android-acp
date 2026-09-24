import { ErrorCode, RpcError, toRpcError } from './errors.js';
import { createGitHandlers, gitAvailable } from './git.js';
import { createWorkspaceHandlers } from './workspace.js';

export const EXTENSION_PREFIX = '_lakasir/';
export const EXTENSION_VERSION = 1;

export function isExtensionMethod(method) {
  return typeof method === 'string' && method.startsWith(EXTENSION_PREFIX);
}

export function createExtensions(options = {}) {
  const workspace = createWorkspaceHandlers(options);
  const git = createGitHandlers(options);
  const methods = {
    '_lakasir/hello': async () => ({ version: EXTENSION_VERSION, fs: true, git: await gitAvailable() }),
    '_lakasir/fs/list': workspace.list,
    '_lakasir/fs/read': workspace.read,
    '_lakasir/fs/write': workspace.write,
    '_lakasir/fs/create': workspace.create,
    '_lakasir/fs/rename': workspace.rename,
    '_lakasir/fs/delete': workspace.delete,
    '_lakasir/git/status': git.status,
    '_lakasir/git/diff': git.diff,
    '_lakasir/git/log': git.log,
    '_lakasir/git/show': git.show,
    '_lakasir/git/stage': git.stage,
    '_lakasir/git/unstage': git.unstage,
    '_lakasir/git/commit': git.commit,
  };

  return {
    async handle(message) {
      const handler = methods[message.method];
      try {
        if (!handler) throw new RpcError(ErrorCode.METHOD_NOT_FOUND, `Method not found: ${message.method}`);
        const params = message.params && typeof message.params === 'object' ? message.params : {};
        const result = await handler(params);
        return { jsonrpc: '2.0', id: message.id, result };
      } catch (raw) {
        const error = toRpcError(raw);
        const payload = { code: error.code, message: error.message };
        if (error.data !== undefined) payload.data = error.data;
        return { jsonrpc: '2.0', id: message.id, error: payload };
      }
    },
  };
}
