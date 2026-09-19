package com.ryry79261.lensassist

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Declines every request.
 *
 * LensAssist does no speech work at all. This exists because voice_interaction.xml has
 * to name a RecognitionService in this package, and because the assistant picker only
 * lists apps that declare one.
 */
class StubRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // The caller is gone by the time a dead binder surfaces; nothing to do about it.
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
