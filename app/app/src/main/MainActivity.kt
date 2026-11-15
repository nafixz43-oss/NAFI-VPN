package bd.nafi.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private val Navy = Color(0xFF071126)
private val Panel = Color(0xFF111E3B)
private val Blue = Color(0xFF3C8DFF)
private val Purple = Color(0xFF9B5CFF)
private val Muted = Color(0xFF9AAAC8)

class MainActivity : ComponentActivity() {
    private lateinit var backend: GoBackend
    private val vpnTunnel = object : Tunnel {
        override fun getName(): String = "nafi"
        override fun onStateChange(newState: Tunnel.State) { }
    }

    private var selectedConfig: File? = null
    private var connected = mutableStateOf(false)
    private var status = mutableStateOf("Import a WireGuard profile to begin")
    private var profileNames = mutableStateListOf<String>()
    private var selectedName = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = GoBackend(this)
        val dir = File(filesDir, "profiles").apply { mkdirs() }
        profileNames.clear()
        profileNames.addAll(dir.listFiles()?.filter { it.extension == "conf" }?.map { it.nameWithoutExtension } ?: emptyList())
        selectedName.value = profileNames.firstOrNull() ?: ""
        selectedConfig = profileNames.firstOrNull()?.let { File(dir, "$it.conf") }

        setContent {
            NafiTheme {
                NafiHome(
                    connected = connected.value,
                    status = status.value,
                    names = profileNames.toList(),
                    selected = selectedName.value,
                    onSelect = { name ->
                        selectedName.value = name
                        selectedConfig = File(dir, "$name.conf")
                    },
                    onImport = { openConfig.launch(arrayOf("*/*")) },
                    onConnect = { requestVpnPermissionAndConnect() },
                    onDisconnect = { disconnectVpn() }
                )
            }
        }
    }

    private val openConfig = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val rawName = (uri.lastPathSegment ?: "profile").substringAfterLast('/').substringBefore('?')
                val safeName = rawName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_")
                    .ifBlank { "NAFI_Server_${System.currentTimeMillis()}" }
                val destination = File(File(filesDir, "profiles").apply { mkdirs() }, "$safeName.conf")
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destination).use { output -> input.copyTo(output) }
                    } ?: error("Could not read selected file")
                    FileInputStream(destination).use { Config.parse(it) } // validate before listing
                }
                if (!profileNames.contains(safeName)) profileNames.add(safeName)
                selectedName.value = safeName
                selectedConfig = destination
                status.value = "Imported: $safeName"
            } catch (e: Exception) {
                status.value = "Import failed: ${e.message ?: "Invalid configuration"}"
            }
        }
    }

    private fun requestVpnPermissionAndConnect() {
        val file = selectedConfig
        if (file == null || !file.exists()) {
            status.value = "Import a valid .conf profile first"
            return
        }
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            vpnPermission.launch(permissionIntent)
        } else {
            connectVpn(file)
        }
    }

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) selectedConfig?.let { file -> connectVpn(file) }
        else status.value = "VPN permission was not granted"
    }

    private fun connectVpn(file: File) {
        status.value = "Connecting…"
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val config = FileInputStream(file).use { Config.parse(it) }
                    backend.setState(vpnTunnel, Tunnel.State.UP, config)
                }
                connected.value = true
                status.value = "Connected • ${selectedName.value}"
            } catch (e: Exception) {
                connected.value = false
                status.value = "Connection failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private fun disconnectVpn() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    backend.setState(vpnTunnel, Tunnel.State.DOWN, null)
                }
                connected.value = false
                status.value = "Disconnected"
            } catch (e: Exception) {
                status.value = "Disconnect error: ${e.message}"
            }
        }
    }
}

@Composable
private fun NafiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue, secondary = Purple, background = Navy,
            surface = Panel, onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

@Composable
private fun NafiHome(
    connected: Boolean,
    status: String,
    names: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF071126), Color(0xFF10133B), Color(0xFF090D20)))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(Brush.linearGradient(listOf(Blue, Purple)), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("N", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NAFI VPN", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("PRIVATE • SECURE • SIMPLE", fontSize = 9.sp, color = Muted, letterSpacing = 1.5.sp)
                }
                Text("FREE", color = Color(0xFF77E6C0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(36.dp))
            Text("YOUR CONNECTION", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.size(206.dp).border(
                    width = 2.dp,
                    brush = Brush.linearGradient(listOf(Blue, Purple)),
                    shape = CircleShape
                ).padding(12.dp).border(1.dp, Color(0x553C8DFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (connected) "●" else "◈", fontSize = 43.sp, color = if (connected) Color(0xFF63E6B4) else Blue)
                    Text(if (connected) "PROTECTED" else "NOT CONNECTED", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(if (connected) "WireGuard active" else "Tap below to connect", fontSize = 10.sp, color = Muted)
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = if (connected) onDisconnect else onConnect,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connected) Color(0xFF263553) else Blue
                )
            ) {
                Text(if (connected) "DISCONNECT" else "CONNECT VPN", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }

            Spacer(Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("SERVER LOCATION", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(10.dp))
                    Box {
                        OutlinedButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text(if (selected.isBlank()) "No server imported" else selected, modifier = Modifier.weight(1f))
                            Text("⌄", color = Purple, fontSize = 20.sp)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Automatic (first imported)") }, onClick = {
                                names.firstOrNull()?.let(onSelect); menuOpen = false
                            })
                            names.forEach { name ->
                                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); menuOpen = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        Text("＋  IMPORT .CONF PROFILE", color = Color(0xFFB9A1FF), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1930)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(15.dp)) {
                    Text("CONNECTION STATUS", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(7.dp))
                    Text(status, color = if (connected) Color(0xFF77E6C0) else Color.White, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("NAFI VPN  •  WireGuard-powered", color = Muted, fontSize = 10.sp)
            Text("No account required • Profiles stay on this device", color = Muted.copy(alpha = 0.75f), fontSize = 9.sp)
        }
    }
}
