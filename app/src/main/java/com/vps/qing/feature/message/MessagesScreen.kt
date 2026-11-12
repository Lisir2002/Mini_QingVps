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

/** 消息 Tab：按需求暂时占位，保留一个站点站内信的 WebView 入口。 */
@Composable
fun MessagesScreen(onOpenWeb: (String) -> Unit) {
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.MESSAGES_PLACEHOLDER,
                subtitle = Strings.MESSAGE_TIP
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyHint(
                    icon = AppIcons.Message,
                    text = Strings.COMING_SOON,
                    action = {
                        CartoonButton(
                            text = Strings.OPEN_WEB_MESSAGES,
                            onClick = { onOpenWeb(AppContainer.webUrl("plugin/30/announcement.htm")) },
                            icon = AppIcons.Forward
                        )
                    }
                )
            }
        }
    }
}
