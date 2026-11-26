package com.tadone.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tadone.AppState
import java.io.File

/** First screen for a signed-out user: one sign-in gets the Composio keys so apps work right away. */
@Composable
fun SignInScreen(s: AppState) {
    val hasCli = remember { File(System.getProperty("user.home"), ".composio/user_data.json").isFile }
    val float by rememberInfiniteTransition().animateFloat(-6f, 6f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse))

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Tilt3D(maxDeg = 3f) {
            Column(
                Modifier.width(440.dp).clay(Palette.Paper, 34.dp, 18.dp).padding(horizontal = 36.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(72.dp).graphicsLayer { rotationY = float * 2; rotationX = float; cameraDistance = 8f * density }
                    .clay(Palette.Amber, 22.dp, 12.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = Palette.Cream, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text("Welcome to Tadone", style = MaterialTheme.typography.headlineMedium, color = Palette.Bark, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Sign in once and Classroom, Docs, Sheets and your other apps are ready to go.",
                    color = Palette.Olive, textAlign = TextAlign.Center)
                Spacer(Modifier.height(26.dp))

                if (s.signingIn) {
                    ThinkingDots(Palette.Amber)
                    Spacer(Modifier.height(10.dp))
                    Text("Finish signing in in your browser…", color = Palette.Bark, fontWeight = FontWeight.SemiBold)
                    Text("Tadone will continue automatically.", color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(14.dp))
                    ClayButton("Cancel", Icons.Rounded.Close, Palette.Cream, Palette.Bark) { s.cancelSignIn() }
                } else {
                    ClayButton("Sign in with Google, GitHub or email", Icons.Rounded.Login, modifier = Modifier.fillMaxWidth()) { s.signIn(false) }
                    if (hasCli) {
                        Spacer(Modifier.height(10.dp))
                        ClayButton("Use this computer's Composio login", Icons.Rounded.Terminal, Palette.Olive, modifier = Modifier.fillMaxWidth()) { s.signIn(true) }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Continue offline", color = Palette.Bark.copy(.7f), fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.pressable { s.useOffline() }.padding(6.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Accounts are powered by Composio. Your tasks stay on this computer.",
                    color = Palette.Bark.copy(.5f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}
