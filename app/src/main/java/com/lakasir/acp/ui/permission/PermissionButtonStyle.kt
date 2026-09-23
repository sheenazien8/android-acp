package com.lakasir.acp.ui.permission

import com.lakasir.acp.acp.PermissionOptionKind

enum class PermissionButtonStyle { PRIMARY, TONAL, DESTRUCTIVE, NEUTRAL }

fun permissionButtonStyle(kind: String): PermissionButtonStyle = when (kind) {
    PermissionOptionKind.ALLOW_ONCE -> PermissionButtonStyle.PRIMARY
    PermissionOptionKind.ALLOW_ALWAYS -> PermissionButtonStyle.TONAL
    PermissionOptionKind.REJECT_ONCE, PermissionOptionKind.REJECT_ALWAYS -> PermissionButtonStyle.DESTRUCTIVE
    else -> PermissionButtonStyle.NEUTRAL
}
