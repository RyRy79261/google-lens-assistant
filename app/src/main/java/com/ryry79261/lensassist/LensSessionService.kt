package com.ryry79261.lensassist

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Hands the system a fresh [LensSession] each time the assistant is invoked. */
class LensSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession = LensSession(this)
}
