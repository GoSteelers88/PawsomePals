package com.example.pawsomepals.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun TermsOfServiceScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Terms of Service",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = """
                Welcome to PawsomePals!

                1. Acceptance of Terms
                By accessing or using the PawsomePals app, you agree to be bound by these Terms of Service.

                2. Description of Service
                PawsomePals is a platform designed to connect dog owners for playdates and socialization opportunities for their pets.

                3. User Responsibilities
                a) You must be at least 18 years old to use this service.
                b) You are responsible for the accuracy of the information you provide about yourself and your dog(s).
                c) You agree to use the app in a manner consistent with all applicable laws and regulations.

                4. Privacy Policy
                Your use of PawsomePals is also governed by our Privacy Policy, which can be found [insert link].

                5. Safety Guidelines
                a) Always meet in public places for initial playdates.
                b) We recommend exchanging information about your dogs' temperaments and health status before meeting.
                c) PawsomePals is not responsible for any incidents that may occur during playdates.

                6. Content Policy
                Users are prohibited from posting content that is offensive, illegal, or violates the rights of others.

                7. Termination of Service
                We reserve the right to terminate or suspend your account at our discretion, without notice, for conduct that we believe violates these Terms of Service or is harmful to other users, us, or third parties, or for any other reason.

                8. Changes to Terms
                We may modify these Terms of Service at any time. Continued use of PawsomePals after changes constitutes acceptance of the new Terms.

                9. Disclaimer of Warranties
                PawsomePals is provided "as is" without any warranties, expressed or implied.

                10. Limitation of Liability
                PawsomePals shall not be liable for any indirect, incidental, special, consequential or punitive damages resulting from your use of the service.

                By clicking "Accept," you acknowledge that you have read and understood these Terms of Service and agree to be bound by them.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Justify,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onDecline,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Decline")
            }
            Button(onClick = onAccept) {
                Text("Accept")
            }
        }
    }
}