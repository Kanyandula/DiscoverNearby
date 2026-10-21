package com.kanyandula.stubnavigation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private const val TAG = "StubNav"
private val ScreenPadding = 48.dp
private val LineGap = 16.dp

/**
 * The emulator's stand-in for a navigation app (docs/03 §11): shows and logs the geo: URI it is handed, verbatim,
 * and the request that carried it (AAOS's NAVIGATE or a geo: VIEW). No routing or guidance.
 */
class StubNavigationActivity : ComponentActivity() {

    private var received by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receive(intent)
        // singleTask: a later hand-off arrives here. setIntent so a recreated activity (day/night) shows it.
        addOnNewIntentListener {
            setIntent(it)
            receive(it)
        }
        setContent { StubNavigationScreen(received?.data, received?.action) }
    }

    // ponytail: logs again when the activity is recreated (e.g. a day/night switch); harmless for a test tool.
    private fun receive(intent: Intent) {
        received = intent
        intent.data?.let { Log.i(TAG, "received $it via ${intent.action}") }
    }
}

@Composable
private fun StubNavigationScreen(destination: Uri?, request: String?) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(LineGap),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.received_destination), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = destination?.toString() ?: stringResource(R.string.no_destination),
                    style = MaterialTheme.typography.displaySmall,
                )
                if (destination != null) {
                    Text(
                        text = stringResource(R.string.received_via, request.orEmpty()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
