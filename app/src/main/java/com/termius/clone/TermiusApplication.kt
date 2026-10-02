package com.termius.clone

import android.app.Application
import com.termius.clone.data.local.AppDatabase
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class TermiusApplication : Application() {

    companion object {
        init {
            setupBouncyCastle()
        }

        fun setupBouncyCastle() {
            try {
                Security.removeProvider("BC")
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        setupBouncyCastle()
        // 预热并初始化数据库
        AppDatabase.getDatabase(this)
    }
}
