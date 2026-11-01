package com.lambda.campsite.view.components
/*
 * AI use: ChatGPT (OpenAI) helped write the KDoc comments for this file
 * in November 2026. Harmanjoth Kaur reviewed and edited the documentation.
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

/**
 * Displays the app policy and guidelines in a bottom sheet.
 *
 * The user can either close the policy without agreeing or accept
 * the policy to continue the onboarding process.
 *
 * @param ClickCloseAppPolicy callback invoked when the user closes
 * the policy sheet.
 * @param ClickAgreeAppPolicy callback invoked when the user agrees
 * to the app policy.
 */
@Composable


fun AppPolicySheet(
    ClickCloseAppPolicy: () -> Unit = {},
    ClickAgreeAppPolicy: () -> Unit = {}
){
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
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
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.policy_lottery_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column {
            Text(
                text = stringResource(R.string.policy_camper_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.policy_camper_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column {
            Text(
                text = stringResource(R.string.policy_information_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.policy_information_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { ClickCloseAppPolicy() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("close_policy_button")
            ) {
                Text(text = stringResource(R.string.close))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { ClickAgreeAppPolicy() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("agree_policy_button")
            ) {
                Text(text = stringResource(R.string.agree))
            }
        }
    }
}