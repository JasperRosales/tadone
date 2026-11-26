package com.tadone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tadone.AppState
import com.tadone.data.AppInfo
import com.tadone.data.Apps
import com.tadone.data.Db
import com.tadone.data.Keys

@Composable
fun AppsScreen(s: AppState) {
    LaunchedEffect(Unit) { s.refreshConnections() }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("All") }
    val groups = listOf("All", "Connected") + Apps.all.map { it.group }.distinct()
    val conns = s.connections
    val shown = Apps.all.filter { a ->
        (query.isBlank() || a.name.contains(query, true) || a.blurb.contains(query, true)) &&
            when (group) { "All" -> true; "Connected" -> conns?.containsKey(a.slug) == true; else -> a.group == group }
    }

    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Apps", style = MaterialTheme.typography.displaySmall, color = Palette.Bark)
                Text(
                    if (conns == null) "Loading your connections…" else "${conns.size} connected · connect the tools you use for school",
                    color = Palette.Olive,
                )
            }
            ClayButton("Refresh", Icons.Rounded.Sync, Palette.Cream, Palette.Bark) { s.refreshConnections() }
        }
        Spacer(Modifier.height(14.dp))
        ClayField(query, { query = it }, "Search apps…", Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            leading = { Icon(Icons.Rounded.Search, null, tint = Palette.Olive) })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEach { g ->
                val on = g == group
                Text(g, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else Palette.Bark,
                    modifier = Modifier.clay(if (on) Palette.Amber else Palette.Cream, 50.dp, if (on) 6.dp else 3.dp)
                        .pressable { group = g }.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        if (Db.get(Keys.COMPOSIO_KEY).isBlank()) {
            Text("Sign in to connect apps.", color = Palette.Bark)
            Spacer(Modifier.height(8.dp))
            ClayButton("Sign in", Icons.Rounded.Login) { s.signOut() }
            return@Column
        }
        LazyVerticalGrid(GridCells.Adaptive(250.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(shown, key = { it.slug }) { a -> AppCard(s, a, conns?.containsKey(a.slug)) }
        }
    }
}

@Composable
private fun AppCard(s: AppState, a: AppInfo, connected: Boolean?) {
    val brand = Color(a.color)
    val busyHere = a.slug in s.connecting
    Tilt3D(maxDeg = 4f) {
        Column(Modifier.fillMaxWidth().height(168.dp).clay(Palette.Paper, 24.dp, 10.dp).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(brand, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text(a.name.split(" ").last().take(1), color = Color.White, fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = Palette.Bark, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(a.group, color = Palette.Olive, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(a.blurb, color = Palette.Bark.copy(.75f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    busyHere -> {
                        ThinkingDots(Palette.Amber); Spacer(Modifier.width(8.dp))
                        Text("Finish in your browser…", color = Palette.Bark, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text("Cancel", color = Palette.Amber, fontWeight = FontWeight.Bold, modifier = Modifier.pressable { s.cancelConnect(a.slug) })
                    }
                    connected == true -> {
                        Chip("Connected", Palette.Olive); Spacer(Modifier.weight(1f))
                        Text("Disconnect", color = Palette.Bark.copy(.7f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.pressable { s.disconnectApp(a.slug) }.padding(6.dp))
                    }
                    else -> ClayButton("Connect", Icons.Rounded.Link, brand, enabled = connected != null) { s.connectApp(a.slug) }
                }
            }
        }
    }
}
