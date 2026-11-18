package com.weizao.movieapp

import android.app.Application
import com.weizao.movieapp.db.AppDatabase
import com.weizao.movieapp.db.Defaults

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        db = AppDatabase.get(this)
        seedSources()
    }

    /** 首次启动写入默认源，并自动选中第一个 */
    private fun seedSources() {
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    if (db.sourceDao().all().isEmpty()) {
                        for (i in Defaults.SOURCES.indices) {
                            val (name, api) = Defaults.SOURCES[i]
                            db.sourceDao().insert(
                                com.weizao.movieapp.db.Source(
                                    name = name, api = api, isSelected = i == 0
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }
    companion object {
        lateinit var instance: App
            private set
        lateinit var db: AppDatabase
            private set
    }
}
