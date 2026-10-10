package com.lumitube.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Layar pembuka: pilih YouTube atau YouTube Music. */
@Composable
fun ModeChooser(onPick: (mode: String, remember: Boolean) -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1D1934), Color(0xFF0B0B0B))))
            .systemBarsPadding()
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("LumiTube", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Text(
                "Mau buka yang mana?", color = Color(0xB3FFFFFF), fontSize = 16.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 32.dp), textAlign = TextAlign.Center,
            )
            ModeCard(
                title = "YouTube",
                bg = Brush.horizontalGradient(listOf(Color(0xFFFF0033), Color(0xFFB0002A))),
                music = false,
            ) { onPick("video", false) }
            Spacer(Modifier.height(16.dp))
            ModeCard(
                title = "YouTube Music",
                bg = Brush.horizontalGradient(listOf(Color(0xFF7B2CF5), Color(0xFF3B1C8C))),
                music = true,
            ) { onPick("music", false) }
        }
    }
}

@Composable
private fun ModeCard(title: String, bg: Brush, music: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(20.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (music) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).border(3.dp, YtRed, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PlayArrow, null, tint = YtRed, modifier = Modifier.size(20.dp)) }
            }
        } else {
            Box(
                Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, null, tint = YtRed, modifier = Modifier.size(28.dp)) }
        }
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp))
        Spacer(Modifier.width(8.dp))
    }
}
