package com.lakasir.acp.di

import android.content.Context
import com.lakasir.acp.data.local.AppDatabase

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
}
