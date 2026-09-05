package com.brukb.zerotier.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.brukb.zerotier.data.model.LinkProfile
import com.brukb.zerotier.data.model.Moon
import com.brukb.zerotier.data.model.ZerotierBNetwork

@Database(
    entities = [ZerotierBNetwork::class, LinkProfile::class, Moon::class],
    version = 5,
    exportSchema = false,
)
@TypeConverters(LinkConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
    abstract fun linkProfileDao(): LinkProfileDao
    abstract fun moonDao(): MoonDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS networks_new (
                        networkId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        allowManaged INTEGER NOT NULL,
                        allowDefault INTEGER NOT NULL,
                        allowGlobal INTEGER NOT NULL,
                        allowDns INTEGER NOT NULL,
                        routePriority INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO networks_new (
                        networkId, name, isEnabled, allowManaged, allowDefault,
                        allowGlobal, allowDns, routePriority
                    )
                    SELECT networkId, name, isEnabled, allowManaged, allowDefault,
                           allowGlobal, allowDns, 0
                    FROM networks
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE networks")
                db.execSQL("ALTER TABLE networks_new RENAME TO networks")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE networks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE networks ADD COLUMN isPinnedMain INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS link_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        kind TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        ssid TEXT,
                        subscriptionId INTEGER,
                        simSlotIndex INTEGER,
                        label TEXT NOT NULL,
                        iccId TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO link_profiles
                    (id, kind, mode, ssid, subscriptionId, simSlotIndex, label, iccId)
                    VALUES ('other', 'OTHER', 'PROXY', NULL, NULL, NULL, 'Other', NULL)
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE link_profiles ADD COLUMN skipUplinkDnsProbe INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE link_profiles ADD COLUMN uplinkDnsHealEnabled INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE link_profiles ADD COLUMN uplinkDnsPreference TEXT NOT NULL DEFAULT 'WIFI_FIRST'",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS moons (
                        worldId TEXT NOT NULL PRIMARY KEY,
                        seed TEXT,
                        label TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        hasMoonFile INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zerotierb.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
