package com.phoneagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.phoneagent.agent.AgentController
import com.phoneagent.perception.ScreenCaptureManagerImpl
import com.phoneagent.ui.AppRoot
import com.phoneagent.ui.theme.PhoneAgentTheme

class MainActivity : ComponentActivity() {

    private val agentController: AgentController by lazy {
        PhoneAgentApplication.agentController(this)
    }

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        (agentController.getScreenCaptureManager() as? ScreenCaptureManagerImpl)?.onActivityResult(
            ScreenCaptureManagerImpl.REQUEST_CODE, result.resultCode, result.data
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhoneAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(agentController = agentController)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do not destroy the shared controller here — it lives for the app lifecycle.
    }

    fun startScreenCapture() {
        (agentController.getScreenCaptureManager() as? ScreenCaptureManagerImpl)?.startCapture(screenCaptureLauncher)
    }
}
