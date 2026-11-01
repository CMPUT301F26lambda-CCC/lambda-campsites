/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped change
 * this file in October 2026 to show LambdaCampsiteApp instead of the template's
 * greeting. Sashreek Addanki reviewed and committed it.
 */
package com.lambda.campsite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lambda.campsite.view.LambdaCampsiteApp
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import com.lambda.campsite.view.screens.FirstLaunchScreen
import androidx.compose.runtime.*

/**
 * The app's only activity. It draws [LambdaCampsiteApp], which holds the
 * navigation and the nav bar, inside the brand theme.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LambdaCampsiteTheme {
                var hasCompletedFirstLaunch by rememberSaveable {
                    mutableStateOf(false)
                }

                if (hasCompletedFirstLaunch) {
                    LambdaCampsiteApp()
                } else {
                    FirstLaunchScreen(
                        onGetStarted = {
                            hasCompletedFirstLaunch = true
                        }
                    )
                }
            }
        }
    }
}
