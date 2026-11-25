/*
 * Purpose: the round profile avatar and the photo picker for the entrant's
 * profile photo (US 01.08.02, Flow 18). The avatar shows the profile photo,
 * or the person's initials when there's no photo. The Profile and Edit
 * Profile screens both use it, so they always look the same.
 *
 * Outstanding issues: none
 *
 * AI Use: Claude Opus 5.5 (Anthropic), November 2026, helped Harmanjoth Kaur with how to shrink
 * large photos before upload. It also assisted with Kotlin syntax and refining KDoc comments.
 */
package com.lambda.campsite.view.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lambda.campsite.R
import com.lambda.campsite.view.theme.DeepGreen
import com.lambda.campsite.view.theme.Mint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The round profile avatar: the profile photo, or the initials of [name]
 * when there's no photo (US 01.08.02).
 *
 * @param name the person's name, used for the initials.
 * @param photoUrl where to load the photo from, or `null` to show the initials.
 * @param size the avatar's width and height.
 */
@Composable
fun ProfileAvatar(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Mint),
        contentAlignment = Alignment.Center,
    ) {
        // The initials sit underneath, so they show while the photo loads or if it fails.
        Text(
            text = initials(name),
            style = MaterialTheme.typography.headlineLarge,
            color = DeepGreen,
        )
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = stringResource(R.string.profile_photo_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Up to two initials from [name], like "MR" for "Mike Ross", or "?" for a blank name. */
fun initials(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifBlank { "?" }

/**
 * Opens the phone's photo picker and gives back the picked photo (US 01.08.02).
 * Large photos are shrunk to at most 1600 pixels on each side before they're
 * returned, the same as campsite photos. Open the picker with [PHOTO_TYPES].
 *
 * @param onPicked called with the photo the entrant picked.
 * @param onFailed called when the picked file can't be read as a photo.
 */
@Composable
fun rememberPhotoPicker(
    onPicked: (Bitmap) -> Unit,
    onFailed: () -> Unit,
): ManagedActivityResultLauncher<String, Uri?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        // The entrant closed the picker without choosing a photo.
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val photo = withContext(Dispatchers.IO) {
                runCatching {
                    // Read only the size first, so a huge photo isn't loaded whole.
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    val sample = generateSequence(1) { it * 2 }
                        .first { bounds.outWidth / it <= 1600 && bounds.outHeight / it <= 1600 }
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                }.getOrNull()
            }
            if (photo != null) onPicked(photo) else onFailed()
        }
    }
}

/** The file types the photo picker offers: any image. */
const val PHOTO_TYPES = "image/" + "*"