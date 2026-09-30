package jp.co.lightpath.reception

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import jp.co.lightpath.reception.data.ReceptionApi
import jp.co.lightpath.reception.ui.ReceptionScreen
import jp.co.lightpath.reception.ui.theme.LightpathReceptionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val baseUrl = BuildConfig.API_BASE_URL.ifBlank {
            getString(R.string.api_base_url)
        }
        val api = ReceptionApi(baseUrl)

        setContent {
            LightpathReceptionTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReceptionScreen(api = api)
                }
            }
        }
    }
}
