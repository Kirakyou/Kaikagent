package com.visualagent.hyperos

import android.app.Application
import com.visualagent.hyperos.agent.AgentRunner
import com.visualagent.hyperos.core.TouchInjector
import com.visualagent.hyperos.ui.sound.McSoundManager

class VisualAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        McSoundManager.init(this)
        AgentRunner.initPrefs(this)
        TouchInjector.initShizukuListeners()
    }
}
