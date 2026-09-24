package com.lakasir.acp.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    const val ADD_AUTO_APPROVE_SQL = "ALTER TABLE sessions ADD COLUMN autoApprove INTEGER NOT NULL DEFAULT 0"

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(ADD_AUTO_APPROVE_SQL)
        }
    }

    val ADD_ENDPOINT_SQL = listOf(
        "ALTER TABLE connection_profiles ADD COLUMN scheme TEXT NOT NULL DEFAULT 'ws'",
        "ALTER TABLE connection_profiles ADD COLUMN path TEXT NOT NULL DEFAULT '/acp'",
        "ALTER TABLE connection_profiles ADD COLUMN authToken TEXT",
        "ALTER TABLE connection_profiles ADD COLUMN allowInsecureTls INTEGER NOT NULL DEFAULT 0",
    )

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ADD_ENDPOINT_SQL.forEach(db::execSQL)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
