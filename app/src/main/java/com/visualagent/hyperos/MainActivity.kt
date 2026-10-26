package com.visualagent.hyperos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.visualagent.hyperos.ui.overlay.FloatingOverlayController
import com.visualagent.hyperos.ui.screens.ChatScreen
import com.visualagent.hyperos.ui.screens.SettingsScreen
import com.visualagent.hyperos.ui.theme.McButtonVariant
import com.visualagent.hyperos.ui.theme.McPixelButton
import com.visualagent.hyperos.ui.theme.mcTiledTextureBackground

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TAB = "extra_open_tab"
        const val TAB_CHAT = 0
        const val TAB_SETTINGS = 1
    }

    private var selectedTab by mutableIntStateOf(TAB_CHAT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 严格留出系统状态栏、前置挖孔与顶部“屏幕共享中”胶囊的安全距离，防止顶部切换按钮被遮挡
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestNecessaryRuntimePermissions()
        FloatingOverlayController.attachToApplicationOverlayIfPermitted(this)
        handleTabIntent(intent)

        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .mcTiledTextureBackground(resId = R.drawable.mc_dirt_bg, darkenAlpha = 0.18f)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    McPixelButton(
                        text = "💬 聊天与指令",
                        variant = if (selectedTab == TAB_CHAT) McButtonVariant.GREEN else McButtonVariant.GRAY,
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedTab = TAB_CHAT
                    }
                    McPixelButton(
                        text = "⚙ 设置与模型",
                        variant = if (selectedTab == TAB_SETTINGS) McButtonVariant.GREEN else McButtonVariant.GRAY,
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedTab = TAB_SETTINGS
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == TAB_CHAT) {
                        ChatScreen(onOpenSettings = { selectedTab = TAB_SETTINGS })
                    } else {
                        SettingsScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTabIntent(intent)
    }

    private fun handleTabIntent(intent: Intent?) {
        val tab = intent?.getIntExtra(EXTRA_OPEN_TAB, -1) ?: -1
        if (tab == TAB_CHAT || tab == TAB_SETTINGS) {
            selectedTab = tab
        }
    }

    private fun requestNecessaryRuntimePermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 100)
        }
    }
}
