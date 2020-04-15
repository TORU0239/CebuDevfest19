package sg.toru.cebudevfest19.app

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

class Cebu19App:Application(), CameraXConfig.Provider{
    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }
}