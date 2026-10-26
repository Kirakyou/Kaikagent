package com.visualagent.hyperos.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.visualagent.hyperos.agent.AgentRunner
import com.visualagent.hyperos.agent.GeminiComputerUseClient
import com.visualagent.hyperos.core.MediaProjectionRequestActivity
import com.visualagent.hyperos.core.ScreenCaptureManager
import com.visualagent.hyperos.core.TouchInjector
import com.visualagent.hyperos.core.VisualAgentAccessibilityService
import com.visualagent.hyperos.ui.sound.McSoundManager
import com.visualagent.hyperos.ui.theme.McButtonVariant
import com.visualagent.hyperos.ui.theme.McPanelContainer
import com.visualagent.hyperos.ui.theme.McPixelButton
import com.visualagent.hyperos.ui.theme.mcBevelPanel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val apiKey by AgentRunner.apiKey.collectAsState()
    val selectedModel by AgentRunner.selectedModel.collectAsState()
    val fps by ScreenCaptureManager.fps.collectAsState()
    val isProjectionActive by ScreenCaptureManager.isProjectionActive.collectAsState()
    val injectionChannel by TouchInjector.activeChannel.collectAsState()
    val shizukuUid by TouchInjector.shizukuUid.collectAsState()
    val isA11yActive = VisualAgentAccessibilityService.instance != null

    // 仅当用户点击未启动的 Shizuku 或主动点击帮助按钮时才弹出对话框，平时完全隐藏不占版面
    var helpDialogTitle by remember { mutableStateOf<String?>(null) }
    var helpDialogBody by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 极简三键权限开关（无冗余状态说教文字）
        McPanelContainer(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "■ 核心权限开关",
                    color = Color(0xFFFFD54F),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                val shizukuOk = injectionChannel == TouchInjector.InjectionChannel.SHIZUKU_SHELL_UID_2000 && shizukuUid == 2000

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    McPixelButton(
                        text = if (shizukuOk) "✓ Shizuku" else "Shizuku",
                        variant = if (shizukuOk) McButtonVariant.GREEN else McButtonVariant.GRAY,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                    ) {
                        if (TouchInjector.isShizukuAvailable()) {
                            TouchInjector.requestShizukuPermission()
                        } else {
                            McSoundManager.playError()
                            helpDialogTitle = "Shizuku 尚未运行"
                            helpDialogBody = "当前未检测到 Shizuku 服务运行（未开启期间会自动使用无障碍触控代替）。\n\n如需激活 Shizuku 高速触控：\n1. 在「开发者选项」中开启「USB 调试」、「USB 调试（安全设置）」与「无线调试」。\n2. 将「设置」开启小窗或分屏，点开「使用配对码配对设备」，在 Shizuku 中输入 6 位配对码并点击启动。"
                        }
                    }

                    McPixelButton(
                        text = if (isProjectionActive) "✓ 屏幕捕获" else "开启录屏",
                        variant = if (isProjectionActive) McButtonVariant.GREEN else McButtonVariant.GRAY,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                    ) {
                        context.startActivity(
                            Intent(context, MediaProjectionRequestActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }

                    McPixelButton(
                        text = if (isA11yActive) "✓ 无障碍" else "开启无障碍",
                        variant = if (isA11yActive) McButtonVariant.GREEN else McButtonVariant.GRAY,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                    ) {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }

        // 2. 推荐模型选择与 API Key 配置
        McPanelContainer(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "■ 选择推荐模型 (支持一键切换或手动输入)",
                    color = Color(0xFFFFD54F),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GeminiComputerUseClient.RECOMMENDED_MODELS.forEach { option ->
                        val isSelected = selectedModel == option.id
                        McPixelButton(
                            text = if (isSelected) "★ ${option.title}" else option.title,
                            variant = if (isSelected) McButtonVariant.GREEN else McButtonVariant.GRAY,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            AgentRunner.setSelectedModel(context, option.id)
                        }
                    }
                }

                Text(
                    text = "当前模型代号 (可手动修改):",
                    color = Color(0xFFCCCCCC),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                BasicTextField(
                    value = selectedModel,
                    onValueChange = { AgentRunner.setSelectedModel(context, it) },
                    cursorBrush = SolidColor(Color.White),
                    textStyle = TextStyle(
                        color = Color(0xFFAED581),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .mcBevelPanel(baseColor = Color(0xFF141415))
                        .padding(10.dp)
                )

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "■ Gemini API Key",
                    color = Color(0xFFFFD54F),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                BasicTextField(
                    value = apiKey,
                    onValueChange = { AgentRunner.setApiKey(context, it) },
                    cursorBrush = SolidColor(Color.White),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .mcBevelPanel(baseColor = Color(0xFF141415))
                        .padding(12.dp),
                    decorationBox = { innerTextField ->
                        if (apiKey.isEmpty()) {
                            Text(
                                text = "粘贴 Google AI Studio API Key (AIzaSy...)",
                                color = Color(0xFF777777),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        // 3. 截图取帧率调节
        McPanelContainer(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "■ 视觉取帧速度: 当前 $fps FPS",
                    color = Color(0xFFFFD54F),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 2, 3).forEach { rate ->
                        McPixelButton(
                            text = "$rate FPS",
                            variant = if (fps == rate) McButtonVariant.GREEN else McButtonVariant.GRAY,
                            modifier = Modifier.weight(1f)
                        ) {
                            ScreenCaptureManager.setTargetFps(rate)
                        }
                    }
                }
            }
        }

        // 4. 收纳式帮助按钮（平时隐藏长篇提示，点击才展开弹窗）
        McPixelButton(
            text = "❓ 遇到权限或配对问题？点击查看帮助",
            variant = McButtonVariant.GRAY,
            modifier = Modifier.fillMaxWidth()
        ) {
            helpDialogTitle = "常见问题与激活帮助"
            helpDialogBody = "1. 【为什么报 404 模型不可用？】\nGoogle 已限制新账号调用旧版 gemini-2.5-flash，请直接点击上方「Gemini 3.8 Flash」或「Gemini 3.5 Flash-Lite」。\n\n2. 【如何激活 Shizuku？】\n在系统设置 -> 开发者选项中开启「USB 调试」、「USB 调试（安全设置）」与「无线调试」，用分屏模式将 6 位无线配对码填入 Shizuku 并点击启动。\n\n3. 【录屏中断怎么办？】\n锁屏或通话结束后系统会自动断开录屏，重新点击上方「开启录屏」并选择「录制整个屏幕」即可恢复。"
        }
    }

    if (helpDialogTitle != null) {
        Dialog(onDismissRequest = { helpDialogTitle = null }) {
            McPanelContainer(
                modifier = Modifier.fillMaxWidth(),
                useCobblestone = true
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "⚠ ${helpDialogTitle ?: ""}",
                        color = Color(0xFFFFD54F),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = helpDialogBody,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        McPixelButton(
                            text = "打开开发者选项",
                            variant = McButtonVariant.GRAY,
                            modifier = Modifier.weight(1f)
                        ) {
                            helpDialogTitle = null
                            openDeveloperSettings(context)
                        }
                        McPixelButton(
                            text = "我知道了",
                            variant = McButtonVariant.GREEN,
                            modifier = Modifier.weight(1f)
                        ) {
                            helpDialogTitle = null
                        }
                    }
                }
            }
        }
    }
}

private fun openDeveloperSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
