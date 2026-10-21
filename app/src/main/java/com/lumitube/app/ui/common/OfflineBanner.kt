package com.lumitube.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumitube.app.data.Net

/**
 * Pop up kecil di bawah layar: "Kamu sedang offline" selama tidak ada internet,
 * lalu "Kembali online" sebentar saat koneksi pulih. Ikut tampil di mode PiP (versi ringkas).
 */
@Composable
fun BoxScope.OfflineBanner(pip: Boolean) {
    val online = Net.online
    var showBack by remember { mutableStateOf(false) }
    var wasOffline by remember { mutableStateOf(!online) }
    LaunchedEffect(online) {
        if (!online) {
            wasOffline = true
            showBack = false
        } else if (wasOffline) {
            wasOffline = false
            showBack = true
            kotlinx.coroutines.delay(2000)
            showBack = false
        }
    }
    AnimatedVisibility(
        visible = !online || showBack,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
            .then(if (pip) Modifier.padding(bottom = 6.dp) else Modifier.navigationBarsPadding().padding(bottom = 78.dp)),
    ) {
        val offline = !online
        Row(
            Modifier.shadow(6.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                .background(if (offline) Color(0xFF323232) else Color(0xFF2E7D32))
                .padding(horizontal = if (pip) 8.dp else 14.dp, vertical = if (pip) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (offline) Icons.Filled.WifiOff else Icons.Filled.Wifi, null, tint = Color.White,
                modifier = Modifier.size(if (pip) 12.dp else 16.dp),
            )
            Spacer(Modifier.width(if (pip) 4.dp else 8.dp))
            Text(
                if (offline) (if (pip) "Offline" else "Kamu sedang offline") else "Kembali online",
                color = Color.White, fontSize = if (pip) 10.sp else 13.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
}
