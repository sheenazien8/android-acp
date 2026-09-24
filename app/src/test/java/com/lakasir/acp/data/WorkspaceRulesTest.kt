package com.lakasir.acp.data

import com.lakasir.acp.acp.AcpException
import com.lakasir.acp.acp.RpcError
import com.lakasir.acp.data.local.MessageEntity
import com.lakasir.acp.data.local.MessageRole
import com.lakasir.acp.data.local.MessageType
import com.lakasir.acp.data.model.DiffState
import com.lakasir.acp.data.model.ToolCallState
import com.lakasir.acp.data.model.UserPromptPayload
import com.lakasir.acp.data.repository.AcpRepository
import com.lakasir.acp.data.repository.WorkspaceRepository
import com.lakasir.acp.data.repository.WorkspaceRepository.AttachResult
import com.lakasir.acp.ui.chat.ChatItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceRulesTest {

    @Test
    fun `attachments are de-duplicated and capped`() {
        assertEquals(AttachResult.Added, WorkspaceRepository.addAttachmentResult(listOf("a"), "b"))
        assertEquals(AttachResult.AlreadyAttached, WorkspaceRepository.addAttachmentResult(listOf("a"), "a"))
        val full = (1..WorkspaceRepository.MAX_ATTACHMENTS).map { "f$it" }
        assertEquals(AttachResult.LimitReached, WorkspaceRepository.addAttachmentResult(full, "new"))
        assertEquals(AttachResult.AlreadyAttached, WorkspaceRepository.addAttachmentResult(full, "f1"))
    }

    @Test
    fun `workspace errors map to readable messages`() {
        fun rpc(code: Int) = WorkspaceRepository.errorMessage(AcpException.Rpc(RpcError(code, "raw")))
        assertEquals("The file changed on the bridge machine", rpc(-32010))
        assertEquals("That path is outside the workspace", rpc(-32011))
        assertEquals("Not found. It may have been moved or deleted.", rpc(-32012))
        assertEquals("A file or folder with that name already exists", rpc(-32013))
        assertEquals("This bridge doesn't support file access", rpc(-32601))
        assertEquals("raw", rpc(-32000))
        assertEquals("Not connected to the bridge", WorkspaceRepository.errorMessage(AcpException.Disconnected()))
    }

    @Test
    fun `completed edits and commands change the workspace`() {
        val done = ToolCallState("c1", kind = "edit", status = "completed")
        assertTrue(AcpRepository.changesWorkspace(done))
        assertTrue(AcpRepository.changesWorkspace(done.copy(kind = "execute")))
        assertTrue(AcpRepository.changesWorkspace(done.copy(kind = "other", diffs = listOf(DiffState("a", null, "b")))))
        assertFalse(AcpRepository.changesWorkspace(done.copy(kind = "read")))
        assertFalse(AcpRepository.changesWorkspace(done.copy(status = "in_progress")))
    }

    @Test
    fun `user prompt payload round trips and tolerates missing json`() {
        val payload = UserPromptPayload(listOf("src/a.kt", "README.md"))
        assertEquals(payload, UserPromptPayload.decode(payload.encode()))
        assertEquals(emptyList<String>(), UserPromptPayload.decode(null).attachments)
        assertEquals(emptyList<String>(), UserPromptPayload.decode("not json").attachments)
    }

    @Test
    fun `user chat item carries attachments from raw json`() {
        val message = MessageEntity(
            id = 1, sessionId = 1, role = MessageRole.USER, type = MessageType.TEXT, content = "look",
            timestamp = 0, rawJson = UserPromptPayload(listOf("a.kt")).encode(),
        )
        assertEquals(ChatItem.UserText(1, "look", listOf("a.kt")), ChatItem.from(message))
        assertEquals(ChatItem.UserText(1, "look"), ChatItem.from(message.copy(rawJson = null)))
    }
}
