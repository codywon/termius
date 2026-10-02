package com.termius.clone

import android.app.Application
import com.termius.clone.data.local.AppDatabase

class TermiusApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 预热并初始化数据库
        AppDatabase.getDatabase(this)
    }
}
