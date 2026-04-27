package com.phoneagent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.phoneagent.agent.AgentController
import com.phoneagent.overlay.OverlayPermissionManager
import com.phoneagent.ui.AppRoot

class MainActivity : ComponentActivity() {

    private lateinit var agentController: AgentController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        agentController = AgentController(applicationContext)

        setContent {
            MaterialTheme {
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
        agentController.destroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OverlayPermissionManager.REQUEST_CODE) {
            OverlayPermissionManager.handleOverlayPermissionResult(this)
        }
    }
}
