# Publish the bridge to npm

## Context
- files: @bridge/package.json
- files: @bridge/src/server.js
- files: @bridge/README.md
- `bridge/package.json` is `"private": true`; users have to clone the repo to run it
- The first-run screen (plan 35) will tell users to run it with `npx`

## Goals
1. Remove `private`, set `name` (check availability, e.g. `lakasir-acp-bridge` or a scoped `@lakasir/acp-bridge`), `license`, `repository`, `files: ["src", "README.md"]`
2. Shebang in `src/server.js` so `npx` works; `engines.node >=20`
3. README quick start: `npx <name> --tls auto --token $(openssl rand -hex 16) -- <agent command>`, how to find the fingerprint, allowed roots, firewall note
4. Publish `0.1.0`; GitHub Action to publish on `bridge-v*` tags (optional)

## Notes
- `--tls auto` from plan 21 should land before publishing so the README shows the secure setup first
- Warn in README: the bridge runs an agent that can edit files; bind to LAN only and always use a token

## Testing
- `npm pack --dry-run` shows only the intended files
- `npx ./bridge-0.1.0.tgz --help` from a temp directory
- `cd bridge && npm test`

## Tools / Skills
- Bash: `npm`, `npx`

## Implementation
<!-- Write you've done in here -->
