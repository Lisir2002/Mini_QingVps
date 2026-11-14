package com.vps.qing.feature.message

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import com.vps.qing.core.AppContainer
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.EmptyHint
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.Strings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** 消息 Tab：公告独立页面入口（v1.4.7）。 */
@Composable
fun MessagesScreen(
    onOpenWeb: (String) -> Unit,
    onOpenAnnouncements: () -> Unit = {}
) {
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.MESSAGES_PLACEHOLDER
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyHint(
                    icon = AppIcons.Message,
                    text = Strings.NO_NEWS,
                    action = {
                        CartoonButton(
                            text = Strings.OPEN_ANNOUNCEMENTS,
                            onClick = onOpenAnnouncements,
                            icon = AppIcons.Forward
                        )
                    }
                )
            }
        }
    }
}
