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

    @Test
    fun `path is normalized and validated`() {
        assertEquals("/acp", valid.copy(path = "").validate().toEntity().path)
        assertEquals("/bridge", valid.copy(path = "bridge/").validate().toEntity().path)
        assertNotNull(valid.copy(path = "/a b").validate().pathError)
        assertNotNull(valid.copy(path = "/acp?x=1").validate().pathError)
    }

    @Test
    fun `host with a path or invalid characters is rejected`() {
        assertNotNull(valid.copy(host = "example.com/acp").validate().hostError)
        assertNotNull(valid.copy(host = "a..b").validate().hostError)
        assertTrue(valid.copy(host = "bridge.example.com").validate().isValid)
    }

    @Test
    fun `token is trimmed and optional`() {
        assertEquals(null, valid.toEntity().authToken)
        assertEquals("abc", valid.copy(token = "  abc ").toEntity().authToken)
        assertEquals(null, valid.copy(token = "   ").toEntity().authToken)
    }

    @Test
    fun `saved token is kept unless replaced or removed and never loaded into the field`() {
        val profile = valid.copy(id = 3, token = "first").toEntity()
        val edit = ProfileForm.from(profile)
        assertEquals("", edit.token)
        assertTrue(edit.hasSavedToken)
        assertEquals("first", edit.toEntity().authToken)
        assertEquals("second", edit.copy(token = "second").toEntity().authToken)
        assertEquals(null, edit.copy(savedToken = null).toEntity().authToken)
        assertFalse(edit.toString().contains("first"))
    }

    @Test
    fun `scheme and insecure tls round trip`() {
        val secure = valid.copy(scheme = "wss", allowInsecureTls = true).toEntity()
        assertEquals("wss", secure.scheme)
        assertTrue(secure.allowInsecureTls)
        val back = ProfileForm.from(secure)
        assertEquals("wss", back.scheme)
        assertTrue(back.allowInsecureTls)
        assertFalse(valid.copy(scheme = "ws", allowInsecureTls = true).toEntity().allowInsecureTls)
    }
}
