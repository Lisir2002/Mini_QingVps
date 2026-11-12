package com.vps.qing.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.Strings

/**
 * H5 兜底入口。IDCSmart 插件页格式为 /plugin/{id}/{page}.htm
 * （依据 GET /menu 返回的 plugin/21/source.htm 与首页 addons_js 里的插件 id）。
 *
 * TODO(实测确认)：这些路径需要在登录态下用浏览器确认，集中替换本表即可，UI 不用改。
 */
object ProfileLinks {
    val entries: List<Pair<String, String>> = listOf(
        Strings.MY_ORDERS to "clientarea/order.htm",
        Strings.MY_HOSTS to "clientarea/host.htm",
        Strings.FINANCE to "clientarea/finance.htm",
        Strings.TICKETS to "plugin/27/ticket.htm",
        Strings.CERTIFICATION to "plugin/22/certification.htm",
        Strings.MESSAGES to "plugin/30/announcement.htm",
        Strings.SECURITY to "clientarea/security.htm"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    onOpenWeb: (String) -> Unit
) {
    val vm: ProfileViewModel = viewModel(
        factory = simpleViewModelFactory {
            ProfileViewModel(AppContainer.profileRepository)
        }
    )
    val state by vm.ui.collectAsState()
    var showLogout by remember { mutableStateOf(false) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.PROFILE) },
                actions = { TextButton(onClick = { vm.load() }) { Text(Strings.REFRESH) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(Strings.ACCOUNT, style = MaterialTheme.typography.labelMedium)
                    Text(
                        state.account?.username ?: Strings.UNKNOWN,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "邮箱：${state.account?.email ?: Strings.UNKNOWN}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "手机：${state.account?.phone ?: Strings.UNKNOWN}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Text(
                            "${Strings.BALANCE}：${state.account?.balance ?: Strings.UNKNOWN}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${Strings.CREDIT}：${state.account?.creditLimit ?: Strings.UNKNOWN}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.load() }) { Text(Strings.RETRY) }
            }

            Text(Strings.WEB_FALLBACK_TIP, style = MaterialTheme.typography.bodySmall)
            ProfileLinks.entries.forEach { (title, path) ->
                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = { Text("站点网页承载") },
                    modifier = Modifier.clickable { onOpenWeb(AppContainer.webUrl(path)) }
                )
            }

            Button(
                onClick = { showLogout = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) { Text(Strings.LOGOUT) }
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(Strings.LOGOUT) },
            text = { Text(Strings.LOGOUT_CONFIRM) },
            confirmButton = {
                TextButton(onClick = { showLogout = false; vm.logout() }) { Text(Strings.CONFIRM) }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text(Strings.CANCEL) }
            }
        )
    }
}
