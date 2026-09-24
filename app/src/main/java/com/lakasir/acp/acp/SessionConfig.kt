package com.lakasir.acp.acp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

data class ConfigChoice(
    val value: String,
    val name: String,
    val description: String? = null,
)

data class ConfigOption(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val type: String,
    val currentValue: String? = null,
    val currentBoolean: Boolean? = null,
    val choices: List<ConfigChoice> = emptyList(),
)

data class AgentCommand(
    val name: String,
    val description: String,
    val hint: String? = null,
)

data class UsageInfo(
    val used: Long,
    val size: Long,
    val costAmount: Double? = null,
    val costCurrency: String? = null,
    val turnTotal: Long? = null,
)

data class SessionControlState(
    val configOptions: List<ConfigOption> = emptyList(),
    val commands: List<AgentCommand> = emptyList(),
    val usage: UsageInfo? = null,
) {
    val model: ConfigOption?
        get() = configOptions.firstOrNull { it.category == SessionConfigCategory.MODEL }
            ?: configOptions.firstOrNull { it.id == SessionConfigIds.MODEL }
}

object SessionConfigParser {

    fun parseConfigOptions(element: JsonElement?): List<ConfigOption> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { parseConfigOption(it) }
    }

    private fun parseConfigOption(element: JsonElement): ConfigOption? {
        val obj = element as? JsonObject ?: return null
        val id = obj.string("id") ?: return null
        val name = obj.string("name") ?: return null
        val type = obj.string("type") ?: SessionConfigType.SELECT
        return ConfigOption(
            id = id,
            name = name,
            description = obj.string("description"),
            category = obj.string("category"),
            type = type,
            currentValue = if (type == SessionConfigType.SELECT) obj.string("currentValue") else null,
            currentBoolean = if (type == SessionConfigType.BOOLEAN) obj.boolean("currentValue") else null,
            choices = if (type == SessionConfigType.SELECT) parseChoices(obj["options"]) else emptyList(),
        )
    }

    private fun parseChoices(element: JsonElement?): List<ConfigChoice> {
        val array = element as? JsonArray ?: return emptyList()
        val choices = mutableListOf<ConfigChoice>()
        for (item in array) {
            val obj = item as? JsonObject ?: continue
            val group = obj["options"] as? JsonArray
            if (group != null) {
                for (child in group) {
                    parseChoice(child)?.let(choices::add)
                }
            } else {
                parseChoice(obj)?.let(choices::add)
            }
        }
        return choices
    }

    private fun parseChoice(element: JsonElement): ConfigChoice? {
        val obj = element as? JsonObject ?: return null
        val value = obj.string("value") ?: return null
        val name = obj.string("name") ?: return null
        return ConfigChoice(value, name, obj.string("description"))
    }

    fun parseUsageUpdate(element: JsonElement?): UsageInfo? {
        val obj = element as? JsonObject ?: return null
        val used = obj.long("used") ?: return null
        val size = obj.long("size") ?: return null
        val cost = obj["cost"] as? JsonObject
        return UsageInfo(
            used = used,
            size = size,
            costAmount = cost?.double("amount"),
            costCurrency = cost?.string("currency"),
        )
    }

    fun parseAvailableCommands(element: JsonElement?): List<AgentCommand> {
        val obj = element as? JsonObject ?: return emptyList()
        val array = obj["availableCommands"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { command ->
            val commandObj = command as? JsonObject ?: return@mapNotNull null
            val name = commandObj.string("name") ?: return@mapNotNull null
            val description = commandObj.string("description") ?: ""
            val input = commandObj["input"] as? JsonObject
            AgentCommand(name, description, input?.string("hint"))
        }
    }

    fun parseCurrentModeId(element: JsonElement?): String? {
        return (element as? JsonObject)?.string("currentModeId")
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
}
