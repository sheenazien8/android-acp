export const ErrorCode = {
  METHOD_NOT_FOUND: -32601,
  INVALID_PARAMS: -32602,
  IO_ERROR: -32000,
  CONFLICT: -32010,
  OUTSIDE_WORKSPACE: -32011,
  NOT_FOUND: -32012,
  ALREADY_EXISTS: -32013,
  NOTHING_STAGED: -32020,
  GIT_ERROR: -32021,
  NOT_A_REPO: -32022,
};

export class RpcError extends Error {
  constructor(code, message, data) {
    super(message);
    this.code = code;
    this.data = data;
  }
}

export function toRpcError(error) {
  if (error instanceof RpcError) return error;
  switch (error?.code) {
    case 'ENOENT':
      return new RpcError(ErrorCode.NOT_FOUND, 'Not found');
    case 'EEXIST':
      return new RpcError(ErrorCode.ALREADY_EXISTS, 'Already exists');
    case 'ENOTEMPTY':
      return new RpcError(ErrorCode.IO_ERROR, 'Folder is not empty');
    case 'ENOTDIR':
      return new RpcError(ErrorCode.IO_ERROR, 'Not a folder');
    case 'EISDIR':
      return new RpcError(ErrorCode.IO_ERROR, 'Is a folder');
    case 'EACCES':
    case 'EPERM':
      return new RpcError(ErrorCode.IO_ERROR, 'Permission denied');
    default:
      return new RpcError(ErrorCode.IO_ERROR, error?.message ?? 'I/O error');
  }
}
