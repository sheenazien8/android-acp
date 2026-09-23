package com.lakasir.acp.ui.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionButtonStyleTest {

    @Test
    fun `maps option kinds to button styles`() {
        assertEquals(PermissionButtonStyle.PRIMARY, permissionButtonStyle("allow_once"))
        assertEquals(PermissionButtonStyle.TONAL, permissionButtonStyle("allow_always"))
        assertEquals(PermissionButtonStyle.DESTRUCTIVE, permissionButtonStyle("reject_once"))
        assertEquals(PermissionButtonStyle.DESTRUCTIVE, permissionButtonStyle("reject_always"))
        assertEquals(PermissionButtonStyle.NEUTRAL, permissionButtonStyle("something_new"))
    }
}
