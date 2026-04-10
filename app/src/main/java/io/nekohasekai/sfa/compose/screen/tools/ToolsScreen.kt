package io.nekohasekai.sfa.compose.screen.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.CrashReportManager
import io.nekohasekai.sfa.bg.OOMReportManager
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(navController: NavController) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.title_tools)) },
        )
    }

    val crashUnreadCount by CrashReportManager.unreadCount.collectAsState()
    val oomUnreadCount by OOMReportManager.unreadCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.title_debug),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.crash_report),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    if (crashUnreadCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("$crashUnreadCount")
                        }
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .clickable { navController.navigate("tools/crash_report") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.oom_report),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    if (oomUnreadCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("$oomUnreadCount")
                        }
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .clickable { navController.navigate("tools/oom_report") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}
