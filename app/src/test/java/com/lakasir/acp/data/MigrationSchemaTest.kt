package com.lakasir.acp.data

import com.lakasir.acp.data.local.Migrations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MigrationSchemaTest {

    private fun profileColumns(version: Int): Map<String, JsonObject> {
        val file = File("schemas/com.lakasir.acp.data.local.AppDatabase/$version.json")
        val entities = Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
        val table = entities.map { it.jsonObject }.first { it["tableName"]!!.jsonPrimitive.content == "connection_profiles" }
        return table["fields"]!!.jsonArray.map { it.jsonObject }.associateBy { it["columnName"]!!.jsonPrimitive.content }
    }

    private val columnSql = Regex("""ADD COLUMN (\w+) (\w+)( NOT NULL)?(?: DEFAULT (.+))?$""")

    @Test
    fun `migration 2 to 3 adds exactly the columns of schema 3 with matching defaults`() {
        val before = profileColumns(2)
        val after = profileColumns(3)
        val added = Migrations.ADD_ENDPOINT_SQL.map { columnSql.find(it)!!.destructured }

        assertEquals(after.keys - before.keys, added.map { it.component1() }.toSet())
        added.forEach { (name, affinity, notNull, default) ->
            val field = after.getValue(name)
            assertEquals(name, field["affinity"]!!.jsonPrimitive.content, affinity)
            assertEquals(name, field["notNull"]!!.jsonPrimitive.boolean, notNull.isNotEmpty())
            assertEquals(name, field["defaultValue"]?.jsonPrimitive?.contentOrNull, default.ifEmpty { null })
        }
    }
}
