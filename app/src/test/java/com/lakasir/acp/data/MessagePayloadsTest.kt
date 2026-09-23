package com.lakasir.acp.data

import com.lakasir.acp.acp.AcpJson
import com.lakasir.acp.acp.PlanEntry
import com.lakasir.acp.acp.ToolCallInfo
import com.lakasir.acp.acp.ToolContent
import com.lakasir.acp.data.model.DiffState
import com.lakasir.acp.data.model.PermissionRecord
import com.lakasir.acp.data.model.PlanCodec
import com.lakasir.acp.data.model.PlanItem
import com.lakasir.acp.data.model.ToolCallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessagePayloadsTest {

    private val started = ToolCallInfo(
        toolCallId = "c1",
        title = "Edit file",
        kind = "edit",
        status = "pending",
        rawInput = AcpJson.parseToJsonElement("""{"path":"/a"}"""),
    )

    @Test
    fun `from builds state from initial tool call`() {
        val state = ToolCallState.from(started)
        assertEquals("Edit file", state.displayTitle)
        assertEquals("pending", state.status)
        assertEquals("""{"path":"/a"}""", state.input)
        assertEquals("", state.output)
    }

    @Test
    fun `merge keeps fields the update omits`() {
        val state = ToolCallState.from(started).merge(ToolCallInfo("c1", status = "in_progress"))
        assertEquals("Edit file", state.title)
        assertEquals("in_progress", state.status)
        assertEquals("""{"path":"/a"}""", state.input)
    }

    @Test
    fun `merge replaces content collection`() {
        val first = ToolCallState.from(started).merge(
            ToolCallInfo("c1", content = listOf(ToolContent.Text("one"), ToolContent.Diff("/a", "x", "y")))
        )
        assertEquals("one", first.output)
        assertEquals(listOf(DiffState("/a", "x", "y")), first.diffs)

        val second = first.merge(ToolCallInfo("c1", status = "completed", content = listOf(ToolContent.Text("two"))))
        assertEquals("two", second.output)
        assertEquals(emptyList<DiffState>(), second.diffs)
    }

    @Test
    fun `raw output used when there is no text content`() {
        val state = ToolCallState.from(started).merge(
            ToolCallInfo("c1", rawOutput = AcpJson.parseToJsonElement("""{"ok":true}"""))
        )
        assertEquals("""{"ok":true}""", state.output)
    }

    @Test
    fun `tool call state round trips`() {
        val state = ToolCallState.from(started)
        assertEquals(state, ToolCallState.decode(state.encode()))
        assertNull(ToolCallState.decode("garbage"))
    }

    @Test
    fun `plan codec round trips`() {
        val json = PlanCodec.encode(listOf(PlanEntry("step", "pending", "high")))
        assertEquals(listOf(PlanItem("step", "pending", "high")), PlanCodec.decode(json))
    }

    @Test
    fun `permission record round trips`() {
        val record = PermissionRecord("rm", "execute", "{}", listOf("Allow", "Deny"), "Allow")
        assertEquals(record, PermissionRecord.decode(record.encode()))
    }

    @Test
    fun `auto approved permission record round trips and old records decode as manual`() {
        val record = PermissionRecord("rm", "execute", "{}", listOf("Allow", "Deny"), "Allow", auto = true)
        assertEquals(record, PermissionRecord.decode(record.encode()))
        assertEquals(false, PermissionRecord.decode("""{"title":"rm","choice":"Allow"}""")?.auto)
    }
}
