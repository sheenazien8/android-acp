package com.lakasir.acp.acp

import com.lakasir.acp.acp.AcpJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionConfigTest {

    @Test
    fun `parse config option with choices`() {
        val json = """
            [{"id":"model","name":"Model","description":"AI model","category":"model","type":"select","currentValue":"sonnet","options":[
                {"value":"opus","name":"Opus","description":"Best quality"},
                {"value":"sonnet","name":"Sonnet"}
            ]}]
        """.trimIndent()
        val options = SessionConfigParser.parseConfigOptions(AcpJson.parseToJsonElement(json))
        assertEquals(1, options.size)
        val option = options.first()
        assertEquals("model", option.id)
        assertEquals("sonnet", option.currentValue)
        assertEquals(2, option.choices.size)
        assertEquals("Opus", option.choices[0].name)
        assertEquals("Best quality", option.choices[0].description)
    }

    @Test
    fun `parse boolean config option`() {
        val json = """[{"id":"auto-approve","name":"Auto approve","type":"boolean","currentValue":true}]"""
        val options = SessionConfigParser.parseConfigOptions(AcpJson.parseToJsonElement(json))
        val option = options.first()
        assertTrue(option.currentBoolean == true)
        assertEquals(SessionConfigType.BOOLEAN, option.type)
    }

    @Test
    fun `parse config options ignores invalid entries`() {
        val json = """[{"id":"bad"},{"id":"good","name":"Good","type":"select","currentValue":"x"}]"""
        val options = SessionConfigParser.parseConfigOptions(AcpJson.parseToJsonElement(json))
        assertEquals(1, options.size)
        assertEquals("good", options.first().id)
    }

    @Test
    fun `control state picks model option by category`() {
        val options = listOf(
            ConfigOption(id = "theme", name = "Theme", type = SessionConfigType.SELECT),
            ConfigOption(id = "model", name = "Model", type = SessionConfigType.SELECT, category = SessionConfigCategory.MODEL),
        )
        val state = SessionControlState(configOptions = options)
        assertEquals("model", state.model?.id)
    }

    @Test
    fun `control state falls back to id model`() {
        val options = listOf(
            ConfigOption(id = "model", name = "Model", type = SessionConfigType.SELECT),
        )
        val state = SessionControlState(configOptions = options)
        assertEquals("model", state.model?.id)
    }

    @Test
    fun `control state has no model when absent`() {
        assertNull(SessionControlState().model)
    }

    @Test
    fun `parse usage update`() {
        val json = """{"used":500,"size":100000,"cost":{"amount":0.42,"currency":"USD"}}"""
        val usage = SessionConfigParser.parseUsageUpdate(AcpJson.parseToJsonElement(json))
        assertEquals(500L, usage?.used)
        assertEquals(100000L, usage?.size)
        assertEquals(0.42, usage?.costAmount)
        assertEquals("USD", usage?.costCurrency)
    }

    @Test
    fun `parse usage update without cost`() {
        val json = """{"used":0,"size":200000}"""
        val usage = SessionConfigParser.parseUsageUpdate(AcpJson.parseToJsonElement(json))
        assertNull(usage?.costAmount)
        assertEquals(200000L, usage?.size)
    }

    @Test
    fun `parse usage update requires used and size`() {
        assertNull(SessionConfigParser.parseUsageUpdate(AcpJson.parseToJsonElement("""{"used":1}""")))
        assertNull(SessionConfigParser.parseUsageUpdate(AcpJson.parseToJsonElement("""{"size":1}""")))
    }

    @Test
    fun `parse available commands`() {
        val json = """{"availableCommands":[{"name":"todo","description":"Track tasks","input":{"hint":"What to track?"}}]}"""
        val commands = SessionConfigParser.parseAvailableCommands(AcpJson.parseToJsonElement(json))
        assertEquals(1, commands.size)
        assertEquals("todo", commands.first().name)
        assertEquals("What to track?", commands.first().hint)
    }

    @Test
    fun `parse available commands ignores missing names`() {
        val json = """{"availableCommands":[{"description":"No name"}]}"""
        val commands = SessionConfigParser.parseAvailableCommands(AcpJson.parseToJsonElement(json))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `parse current mode id`() {
        val json = """{"currentModeId":"agentic"}"""
        assertEquals("agentic", SessionConfigParser.parseCurrentModeId(AcpJson.parseToJsonElement(json)))
    }

    @Test
    fun `missing values return null or empty`() {
        assertTrue(SessionConfigParser.parseConfigOptions(JsonObject(emptyMap())).isEmpty())
        assertTrue(SessionConfigParser.parseAvailableCommands(JsonObject(emptyMap())).isEmpty())
        assertNull(SessionConfigParser.parseUsageUpdate(JsonPrimitive(1)))
    }
}
