// LocationEnableRequest.kt
package io.pawsomepals.app.ui.components.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun LocationEnableRequest(
    onOpenDeviceSettings: () -> Unit = {},
    onTryAgain: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.GpsOff,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Location Services Disabled",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "PawsomePals needs location services to find nearby playdate matches for your dog.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onOpenDeviceSettings,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Open Device Settings")
        }

        if (onTryAgain != null) {
            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onTryAgain,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Try Again")
            }
        }
    }
}

// Preview
@Preview(showBackground = true)
@Composable
private fun LocationEnableRequestPreview() {
    MaterialTheme {
        LocationEnableRequest(
            onOpenDeviceSettings = {},
            onTryAgain = {}
        )
    }
}

// Preview without Try Again button
@Preview(showBackground = true)
@Composable
private fun LocationEnableRequestSimplePreview() {
    MaterialTheme {
        LocationEnableRequest(
            onOpenDeviceSettings = {}
        )
    }
}