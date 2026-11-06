/*
Purpose:
The purpose of this screen is to display the first-launch screen
for the Lambda Campsite application.(US 01.09.01)
Users must enter their name and check the option confirming that
they agree with the app guidelines. They also have the opportunity
to view and review the guidelines by clicking on the policy link
before agreeing to them.
Once the user has entered their name and agreed to the guidelines,
they can click on the Get Started button. After clicking it, their
profile information is saved to Firestore. This allows the application
to recognize their profile when they use the app again, so they do
not need to create a new profile every time they open the app.
If saving the profile fails, an error message is displayed so the
user can try again.
After the profile is successfully saved, the user is taken to the
first tab associated with their active role.
Design Choice:
The user's name is requested before displaying the app policy so that
it can be included in the policy through AppPolicySheet. This makes
the agreement feel more personal and direct, rather than presenting
users with a generic policy.
Outstanding Issue-None
*/
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
 * Displays the first-launch screen for the Lambda Campsite application.
 *
 * Allows users to enter their name, view the app guidelines through the
 * policy link, and agree to the guidelines before proceeding.
 *
 * The Get Started button is enabled only when the user has entered a
 * non-blank name and agreed to the app policy. When selected, the button
 * invokes [onGetStarted] with the user's trimmed name so the parent
 * component can create and save the user's profile.
 *
 * @param onGetStarted callback invoked with the user's trimmed name when
 *                      they select Get Started. The parent component uses
 *                      this name to handle profile creation and saving.
 * @param saveFailed indicates whether saving the user's profile failed.
 *                   When true, an error message is displayed so the user
 *                   can try again.
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