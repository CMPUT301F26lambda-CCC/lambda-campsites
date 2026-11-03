/**
 * Displays the first-launch screen for the Lambda Campsite application.
 *
 * Users must enter their name before they can open and review the app policy.
 * After reviewing the policy, they must check the agreement checkbox.
 * The Get Started button is enabled only when the user has entered a
 * non-blank name and checked the agreement checkbox.
 *
 * When the user selects Get Started, the entered name is passed to the
 * ProfileController, which handles creating and saving the user's profile.
 *
 * After the profile is successfully saved, the application displays
 * LambdaCampsiteApp. The user is taken to the first tab associated
 * with their active role.
 **/
/*
 * AI use: GPT-6 (OpenAI), used through the ChatGPT website, helped resolve
 * errors while connecting this screen with ProfileController and the
 * profile-saving functionality. It also helped draft and revise the KDoc
 * documentation for this file in November 2026. Harmanjoth Kaur reviewed,
 * edited and tested the changes.
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
import com.lambda.campsite.view.theme.Mint
import com.lambda.campsite.view.theme.DeepGreen
import  androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color

/**
 * Displays the first-launch screen and handles user interactions.
 *
 * The user must enter a non-blank name and agree to the app policy
 * before proceeding. The entered name is passed to [onGetStarted]
 * when the Get Started button is selected.
 *
 * @param onGetStarted callback invoked with the user's trimmed name
 * when they select Get Started.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FirstLaunchScreen(onGetStarted: (String) -> Unit,
                      saveFailed: Boolean = false) {

    var name by rememberSaveable { mutableStateOf("") }
    var hasAgreed by rememberSaveable { mutableStateOf(false) }
    var showPolicySheet by remember { mutableStateOf(false) }
    // reference- https://developer.android.com/reference/kotlin/androidx/compose/material3/ModalBottomSheet.composable
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForestGreen)
            .statusBarsPadding()
            .imePadding()

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
                    contentDescription = null,
                    modifier = Modifier.size(180.dp)
                )
            }
            Row{
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
            }

            Row(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center

                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 28.dp
                    )
                )
                .background(Mint)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())

        ) {

            Row(
                modifier = Modifier.padding(top = 20.dp)
                    .padding(start=15.dp)

            ) {
                Text(
                    text = stringResource(R.string.get_started_heading),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Row(
                modifier = Modifier.padding(15.dp),
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
                    label = {
                        Text(stringResource(R.string.name_label))
                    },
                    modifier = Modifier.fillMaxWidth().padding(10.dp).testTag("name_field")
                )
            }

            Row(
                modifier = Modifier.padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ){
                Checkbox(
                    checked = hasAgreed,
                    onCheckedChange = { checked ->
                        hasAgreed = checked
                    },modifier = Modifier.testTag("policy_checkbox")
                )
                Text(
                    text = stringResource(R.string.policy_agreement)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.policy_link),
                    color = if (name.isNotBlank()) DeepGreen else DeepGreen.copy(alpha = 0.38f),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(
                        enabled = name.isNotBlank()
                    ,role = Role.Button ){
                        showPolicySheet = true
                    }.testTag("policy_link")
                )
            }
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp,bottom=15.dp)
            ) {
                Button(
                    onClick = {
                        onGetStarted(name.trim())
                    },
                    enabled = name.isNotBlank() && hasAgreed,
                    modifier = Modifier.fillMaxWidth()
                        .height(56.dp)
                        .testTag("get_started_button")

                ) {
                    Text(stringResource(R.string.get_started_button))
                }
                if (saveFailed) {
                    Text(
                        text = stringResource(R.string.profile_save_failed),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }
        if (showPolicySheet) {
            ModalBottomSheet(
                modifier = Modifier.fillMaxHeight(),
                sheetState = sheetState,
                onDismissRequest = {
                    showPolicySheet = false
                },

                containerColor =  Mint,
                dragHandle = {
                    BottomSheetDefaults.DragHandle(
                        color = DeepGreen
                    )
                }

            ) {
                AppPolicySheet(
                    name = name,
                    CloseAppPolicy = {
                        showPolicySheet = false
                    },
                    AgreeAppPolicy = {
                        hasAgreed = true
                        showPolicySheet = false
                    }
                )
            }
        }
    }
}