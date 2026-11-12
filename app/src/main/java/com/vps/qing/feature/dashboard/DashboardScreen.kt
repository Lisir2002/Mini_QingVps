package com.vps.qing.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.HostSummary
import com.vps.qing.data.repository.NewsItem

private data class QuickEntry(val title: String, val path: String)

private val QUICK_ENTRIES = listOf(
    QuickEntry(Strings.MY_ORDERS, "clientarea/order.htm"),
    QuickEntry(Strings.MY_HOSTS, "clientarea/host.htm"),
    QuickEntry(Strings.FINANCE, "clientarea/finance.htm"),
    QuickEntry(Strings.TICKETS, "plugin/27/ticket.htm"),
    QuickEntry(Strings.CERTIFICATION, "plugin/22/certification.htm"),
    QuickEntry(Strings.SECURITY, "clientarea/security.htm")
)

/**
 * 首页仪表盘：欢迎卡 + 概览数字 + 已订购服务 + 网站公告 + 快捷入口。
 * 公告 / 菜单 / 商品接口匿名可用；主机与账号需登录态，未登录时降级为提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onOpenWeb: (String) -> Unit) {
    val vm: DashboardViewModel = viewModel(
        factory = simpleViewModelFactory {
            DashboardViewModel(AppContainer.catalogRepository, AppContainer.profileRepository)
        }
    )
    val state by vm.ui.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(Strings.DASHBOARD_TITLE) },
            actions = { TextButton(onClick = { vm.load() }) { Text(Strings.REFRESH) } }
        )

        if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { WelcomeCard(state) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = Strings.SERVICE_COUNT,
                        value = if (state.loggedIn) state.hosts.size.toString() else Strings.UNKNOWN,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = Strings.NEWS_COUNT,
                        value = state.news.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = Strings.BALANCE,
                        value = state.account?.balance ?: Strings.UNKNOWN,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { SectionTitle(Strings.MY_SERVICES) }
            if (state.hosts.isEmpty()) {
                item { HintCard(if (state.loggedIn) Strings.NO_SERVICE else Strings.NOT_LOGGED_TIP) }
            } else {
                items(state.hosts.take(5)) { host -> HostCard(host) }
            }

            item { SectionTitle(Strings.ANNOUNCEMENTS) }
            if (state.news.isEmpty()) {
                item { HintCard(Strings.NO_NEWS) }
            } else {
                items(state.news) { news -> NewsCard(news) }
            }

            item { SectionTitle(Strings.QUICK_ENTRIES) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_ENTRIES.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { entry ->
                                QuickEntryCard(
                                    entry = entry,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenWeb(AppContainer.webUrl(entry.path)) }
                                )
                            }
                            if (row.size == 1) Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Text(
                    Strings.WEB_FALLBACK_TIP,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WelcomeCard(state: DashboardUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Strings.WELCOME, style = MaterialTheme.typography.labelMedium)
            Text(
                state.account?.username ?: state.account?.email ?: Strings.APP_NAME,
                style = MaterialTheme.typography.titleLarge
            )
            state.notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun HintCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun HostCard(host: HostSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                host.productName ?: host.name ?: Strings.UNKNOWN,
                style = MaterialTheme.typography.bodyLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    host.status ?: Strings.UNKNOWN,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                host.dueTime?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsCard(news: NewsItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(news.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                news.type,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickEntryCard(entry: QuickEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(entry.title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
