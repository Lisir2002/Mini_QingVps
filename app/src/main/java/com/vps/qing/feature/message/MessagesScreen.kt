package com.vps.qing.feature.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vps.qing.core.AppContainer
import com.vps.qing.core.ui.Strings

/** 消息 Tab：按需求暂时占位，保留一个站点站内信的 WebView 入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(onOpenWeb: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(Strings.MESSAGES_PLACEHOLDER) })
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(Strings.COMING_SOON, style = MaterialTheme.typography.titleMedium)
                    Text(
                        Strings.MESSAGE_TIP,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = {
                        onOpenWeb(AppContainer.webUrl("plugin/30/announcement.htm"))
                    }) { Text(Strings.OPEN_WEB_MESSAGES) }
                }
            }
        }
    }
}
