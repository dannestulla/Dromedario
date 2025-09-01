package br.gohan.dromedario.di

import android.app.Application
import br.gohan.dromedario.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext

class MobileApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        GlobalContext.startKoin {
            androidLogger()
            androidContext(this@MobileApplication)
            modules(mobileModule, sharedModule)
        }
    }
}