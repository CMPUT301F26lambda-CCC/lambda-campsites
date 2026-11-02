package com.lambda.campsite.view.components

/**
* Displays the app policy in a bottom sheet so users can review the
* guidelines and information about using the Lambda Campsite application.
*
* The sheet provides users with the option to close it without agreeing
* or select I Agree to accept the policy. The agreement callback allows
* the parent screen to update the policy agreement checkbox.
**/
/*
 * AI use: ChatGPT (OpenAI), used through the ChatGPT website, helped with
 * revising the KDoc comments for this file in November 2026.
 * Harmanjoth Kaur reviewed and edited the documentation.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import com.lambda.campsite.view.theme.Mint
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
/**
 * Displays the app policy and guidelines in a bottom sheet.
 *
 * The user can either close the policy without agreeing or accept
 * the policy to continue the getStarted process.
 *
 * @param CloseAppPolicy callback invoked when the user closes
 * the policy sheet.
 * @param AgreeAppPolicy callback invoked when the user agrees
 * to the app policy.
 */
@Composable


fun AppPolicySheet(
    name: String,
    CloseAppPolicy: () -> Unit = {},
    AgreeAppPolicy: () -> Unit = {}
){
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mint)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = stringResource(R.string.app_policy_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column {
            Text(
                text = stringResource(R.string.policy_lottery_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.policy_lottery_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column {
            Text(
                text = stringResource(R.string.policy_camper_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(

                text = "$name ${stringResource(R.string.policy_camper_description)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column {
            Text(
                text = stringResource(R.string.policy_information_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.policy_information_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { CloseAppPolicy() },
                modifier = Modifier
                    .testTag("close_policy_button")
            ) {
                Text(text = stringResource(R.string.close))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { AgreeAppPolicy() },
                modifier = Modifier

                    .testTag("agree_policy_button")
            ) {
                Text(text = stringResource(R.string.agree))
            }
        }
    }
}