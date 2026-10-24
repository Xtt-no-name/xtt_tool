package com.xtt.toolbox.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.data.profile.AvatarImageState
import com.xtt.toolbox.ui.components.DefaultAvatar

@Composable
fun ProfileAvatar(
    avatarState: AvatarImageState,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "选择头像" },
        contentAlignment = Alignment.Center,
    ) {
        when (val state = avatarState) {
            is AvatarImageState.Ready -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = "用户头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            AvatarImageState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            )
            AvatarImageState.None,
            AvatarImageState.Failed,
            -> DefaultAvatar()
        }
    }
}
