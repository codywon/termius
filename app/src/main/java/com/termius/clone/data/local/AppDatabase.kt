package com.termius.clone.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.termius.clone.data.model.HostEntity
import com.termius.clone.data.model.IdentityEntity
import com.termius.clone.data.model.SnippetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [HostEntity::class, IdentityEntity::class, SnippetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun identityDao(): IdentityDao
    abstract fun snippetDao(): SnippetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "termius_clone.db"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // 预置默认常用命令片段
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getDatabase(context).snippetDao()
                            dao.insertSnippet(SnippetEntity(
                                title = "系统资源监控 (htop)",
                                command = "htop\n",
                                tag = "System"
                            ))
                            dao.insertSnippet(SnippetEntity(
                                title = "查看磁盘占用 (df -h)",
                                command = "df -h\n",
                                tag = "System"
                            ))
                            dao.insertSnippet(SnippetEntity(
                                title = "查看网络端口监听 (ss -tulpn)",
                                command = "ss -tulpn\n",
                                tag = "Network"
                            ))
                            dao.insertSnippet(SnippetEntity(
                                title = "Docker 正在运行容器",
                                command = "docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}'\n",
                                tag = "Docker"
                            ))
                        }
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
