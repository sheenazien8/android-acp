package com.lakasir.acp.ui.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFormTest {

    private val valid = ProfileForm(host = "192.168.1.10", port = "8080", cwd = "/home/dev/project")

    @Test
    fun `valid form passes`() {
        assertTrue(valid.validate().isValid)
    }

    @Test
    fun `host is required and must be bare`() {
        assertNotNull(valid.copy(host = " ").validate().hostError)
        assertNotNull(valid.copy(host = "ws://192.168.1.10").validate().hostError)
    }

    @Test
    fun `port must be in range`() {
        assertNotNull(valid.copy(port = "0").validate().portError)
        assertNotNull(valid.copy(port = "70000").validate().portError)
        assertNotNull(valid.copy(port = "").validate().portError)
    }

    @Test
    fun `cwd must be absolute`() {
        assertNotNull(valid.copy(cwd = "project").validate().cwdError)
        assertTrue(valid.copy(cwd = "C:\\work").validate().isValid)
        assertFalse(valid.copy(cwd = "").validate().isValid)
    }

    @Test
    fun `name defaults to host`() {
        assertEquals("192.168.1.10", valid.toEntity().name)
        assertEquals(8080, valid.toEntity().port)
    }
}
