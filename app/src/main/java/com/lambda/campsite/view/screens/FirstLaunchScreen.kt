/*
 * AI use: ChatGPT (OpenAI) helped write the KDoc comments for this file
 * in November 2026. Harmanjoth Kaur reviewed and edited the documentation.
 */
package com.lambda.campsite.view.screens

import androidx.compose.runtime.Composable
import com.lambda.campsite.view.components.AppPolicySheet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.Button
import com.lambda.campsite.view.theme.ForestGreen
import com.lambda.campsite.view.theme.LightSageGreen
import com.lambda.campsite.view.theme.DeepGreen
import com.lambda.campsite.view.theme.PureWhite
import  androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

/**
 * The first-launch screen: introduces Lambda Campsite and collects
 * the user's name and agreement to the app policy before entering
 * the application.
 *
 * @param onGetStarted callback invoked when the user completes the
 * onboarding requirements and clicks the Get Started button.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FirstLaunchScreen( onGetStarted: () -> Unit) {

    var name by remember { mutableStateOf("") }
    var hasAgreed by remember { mutableStateOf(false) }
    var showPolicySheet by remember { mutableStateOf(false) }
    // reference- https://developer.android.com/reference/kotlin/androidx/compose/material3/ModalBottomSheet.composable
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp)
            .background(ForestGreen)

    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row {
                Image(
                    painter = painterResource(R.drawable.camp_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(180.dp)
                )
            }
            Row{
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = PureWhite
                )
            }

            Row(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.titleLarge,
                    color = PureWhite,
                    textAlign = TextAlign.Center

                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .background(LightSageGreen)

        ) {

            Row(
                modifier = Modifier.padding(top = 20.dp)
                    .padding(start=10.dp)

            ) {
                Text(
                    text = stringResource(R.string.get_started_heading),
                    style = MaterialTheme.typography.titleLarge

                )
            }

            Row(
                modifier = Modifier.padding(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.get_started_description),
                    style = MaterialTheme.typography.bodyMedium,

                )
            }

            Row {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = {
                        Text(stringResource(R.string.name_label))
                    },
                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                )
            }

            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ){
                Checkbox(
                    checked = hasAgreed,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            hasAgreed = false
                        }
                    }
                )
                Text(
                    text = stringResource(R.string.policy_agreement)
                )

                Text(
                    text = stringResource(R.string.policy_link),
                    color =  DeepGreen,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        showPolicySheet = true
                    }
                )
            }
            Row(
                modifier = Modifier.padding(bottom=10.dp)
            ) {
                Button(
                    onClick = {
                        onGetStarted()
                    },
                    enabled = name.isNotBlank() && hasAgreed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.get_started_button))
                }
            }
        }
        if (showPolicySheet) {
            ModalBottomSheet(
                modifier = Modifier.fillMaxHeight(),
                sheetState = sheetState,
                onDismissRequest = {
                    showPolicySheet = false
                }

            ) {
                AppPolicySheet(
                    ClickCloseAppPolicy = {
                        showPolicySheet = false
                    },
                    ClickAgreeAppPolicy = {
                        hasAgreed = true
                        showPolicySheet = false
                    }
                )
            }
        }
    }
}