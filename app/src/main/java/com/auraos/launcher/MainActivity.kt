package com.auraos.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Midnight = Color(0xFF061326)
private val DeepBlue = Color(0xFF0A2342)
private val Cyan = Color(0xFF72D5FF)
private val Glass = Color(0xFF173B5C).copy(alpha = .58f)
private val Muted = Color(0xFFA9C5DB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(6, 19, 38)
        window.navigationBarColor = android.graphics.Color.rgb(6, 19, 38)
        setContent { AuraApp() }
    }
}

@Composable
private fun AuraApp() {
    var page by remember { mutableStateOf("home") }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, secondary = Color(0xFF9ACBFF),
        background = Midnight, surface = DeepBlue, onSurface = Color.White
    )) {
        Box(Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF102F53), Midnight, Color(0xFF07101F)))
        )) {
            Box(Modifier.offset(x = 220.dp, y = 110.dp).size(210.dp).blur(85.dp).background(Cyan.copy(.15f), CircleShape))
            Box(Modifier.offset(x = (-80).dp, y = 430.dp).size(230.dp).blur(95.dp).background(Color(0xFF3478C8).copy(.16f), CircleShape))
            when (page) {
                "apps" -> AppDrawer(onBack = { page = "home" })
                else -> HomeScreen(onApps = { page = "apps" })
            }
        }
    }
}

@Composable
private fun HomeScreen(onApps: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var taskText by remember { mutableStateOf("") }
    val tasks = remember { mutableStateListOf("Review today's priorities", "Complete a focus session") }
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(25 * 60) }
    var completed by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1000) }
    }
    LaunchedEffect(running) {
        while (running && seconds > 0) { delay(1000); seconds-- }
        if (running && seconds == 0) { running = false; completed++ }
    }
    val time = SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("A U R A", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("YOUR SPACE. YOUR FLOW.", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
            }
            Surface(color = Glass, shape = CircleShape, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.WbSunny, null, tint = Cyan) }
            }
        }

        Column(Modifier.padding(top = 5.dp)) {
            Text(time, color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
            Text(date, color = Muted, fontSize = 14.sp)
            Text("A little progress goes a long way.", color = Color(0xFFDAEAF7), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }

        GlassCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("TODAY AT A GLANCE", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Text("${tasks.count { it.startsWith("✓ ") }} / ${tasks.size} completed", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF2C719D).copy(.3f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Cyan, modifier = Modifier.size(24.dp))
                }
            }
            LinearProgressIndicator(
                progress = { if (tasks.isEmpty()) 0f else tasks.count { it.startsWith("✓ ") }.toFloat() / tasks.size },
                modifier = Modifier.fillMaxWidth().padding(top = 13.dp).height(5.dp).clip(CircleShape),
                color = Cyan, trackColor = Color.White.copy(.12f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, null, tint = Cyan, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("FOCUS", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                }
                val mm = (seconds / 60).toString().padStart(2, '0')
                val ss = (seconds % 60).toString().padStart(2, '0')
                Text("$mm:$ss", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 8.dp))
                Text("$completed sessions", color = Muted, fontSize = 11.sp)
                Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { running = !running }, modifier = Modifier.size(34.dp)) {
                        Icon(if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { running = false; seconds = 25 * 60 }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.Refresh, null, tint = Muted)
                    }
                }
            }
            GlassCard(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircleOutline, null, tint = Cyan, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("MOMENTUM", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                }
                Text("${tasks.count { it.startsWith("✓ ") }}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 7.dp))
                Text("tasks completed", color = Muted, fontSize = 11.sp)
                Text("Keep it moving.", color = Color(0xFFDAEAF7), fontSize = 11.sp, modifier = Modifier.padding(top = 17.dp))
            }
        }

        GlassCard(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("YOUR TASKS", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Small steps, meaningful days", color = Muted, fontSize = 11.sp)
                }
                Icon(Icons.Outlined.Tune, null, tint = Cyan)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = taskText, onValueChange = { taskText = it },
                    placeholder = { Text("Add a task...", color = Muted) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Cyan, unfocusedBorderColor = Color.White.copy(.15f),
                        cursorColor = Cyan
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = {
                    if (taskText.isNotBlank()) { tasks.add(taskText.trim()); taskText = "" }
                }) { Icon(Icons.Outlined.Add, "Add task") }
            }
            LazyColumn(Modifier.weight(1f).padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(tasks.toList()) { task ->
                    val done = task.startsWith("✓ ")
                    Row(Modifier.fillMaxWidth().clickable {
                        val i = tasks.indexOf(task)
                        if (i >= 0) tasks[i] = if (done) task.removePrefix("✓ ") else "✓ $task"
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (done) Cyan else Muted, modifier = Modifier.size(19.dp))
                        Text(task.removePrefix("✓ "), color = if (done) Muted else Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("AURA OS  •  v0.1", color = Muted.copy(.8f), fontSize = 10.sp, letterSpacing = 1.sp)
            FilledTonalButton(onClick = onApps, shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Outlined.Apps, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("All apps")
            }
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        color = Glass,
        shape = RoundedCornerShape(25.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.13f)),
        tonalElevation = 3.dp
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

private data class LaunchableApp(val label: String, val packageName: String, val icon: android.graphics.drawable.Drawable)

@Composable
private fun AppDrawer(onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { LaunchableApp(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
    val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = Color.White) }
            Column(Modifier.padding(start = 8.dp)) {
                Text("YOUR APPS", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Text("${apps.size} apps available", color = Muted, fontSize = 11.sp)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
            placeholder = { Text("Search apps", color = Muted) }, leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Muted) },
            singleLine = true, shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Cyan, unfocusedBorderColor = Color.White.copy(.15f))
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(filtered) { app ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable {
                    val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (launch != null) context.startActivity(launch)
                }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { viewContext ->
                            android.widget.ImageView(viewContext).apply {
                                setImageDrawable(app.icon)
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    )
                    Text(app.label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 13.dp).weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}
