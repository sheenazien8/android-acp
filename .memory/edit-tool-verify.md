---
name: Verify edit tool success
description: The edit tool may report success while only part of the change lands.
type: rule
---

## Why
During the Plan 18 implementation, multiple `edit()` calls on `AcpRepository.kt` reported "Successfully replaced N block(s)" but later reads showed several blocks were still missing. This caused a compile error and required re-applying the same edits.

## How to apply
- After any multi-block `edit()` on a large or complex file, immediately verify the change with `grep` or `read`.
- For stateful changes (new properties, event routing, branch additions), check each location individually.
- If a block is missing, re-apply with a smaller, targeted `oldText` instead of assuming the first call covered it.

## Related
- `docs/plan/18-agent-control.md`
- `app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt`
