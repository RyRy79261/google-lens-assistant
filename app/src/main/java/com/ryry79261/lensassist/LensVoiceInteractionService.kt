package com.ryry79261.lensassist

import android.service.voice.VoiceInteractionService

/**
 * The assistant registration itself.
 *
 * All the work happens in [LensSession]; this class exists because the platform needs a
 * VoiceInteractionService to hang the registration off, and because holding
 * BIND_VOICE_INTERACTION here is what makes LensAssist selectable as the device
 * assistance app.
 */
class LensVoiceInteractionService : VoiceInteractionService()
