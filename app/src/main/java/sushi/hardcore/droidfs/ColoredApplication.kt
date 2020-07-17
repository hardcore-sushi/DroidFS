package sushi.hardcore.droidfs

import android.app.Application
import com.jaredrummler.cyanea.Cyanea

class ColoredApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        Cyanea.init(this, resources)
    }
}