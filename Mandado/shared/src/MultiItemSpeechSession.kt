package com.carlosmvp.mandado

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Escucha artículos por turnos; al reconocer cada turno, reinicia el micrófono hasta que el usuario lo finaliza. */
class MultiItemSpeechSession(
    context: Context,
    private val onPhrase: (String) -> Unit,
    private val onAudioLevel: (Float) -> Unit,
    private val onResults: (List<String>) -> Unit,
    private val onError: () -> Unit
) {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
    private val phrases = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false
    private var finishRequested = false
    private var listening = false
    private var awaitingResults = false
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 750L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 450L)
    }
    private val restartListening = Runnable { listen() }
    private val finishAfterStop = Runnable { complete() }

    fun start() {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) { onAudioLevel(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false; awaitingResults = true }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onResults(results: Bundle?) {
                listening = false
                awaitingResults = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        phrases.add(it)
                        onPhrase(it)
                    }
                if (finishRequested) complete() else scheduleNextListen()
            }

            override fun onError(error: Int) {
                listening = false
                awaitingResults = false
                if (finishRequested) complete()
                else if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                ) scheduleNextListen() else fail()
            }
        })
        listen()
    }

    /** Finaliza y devuelve lo reconocido hasta ahora. */
    fun finish() {
        if (finished || finishRequested) return
        finishRequested = true
        handler.removeCallbacks(restartListening)
        if (!listening && !awaitingResults) {
            complete()
        } else {
            handler.postDelayed(finishAfterStop, 1500L)
            if (listening) runCatching { recognizer.stopListening() }.onFailure { complete() }
        }
    }

    /** Cancela y descarta los artículos reconocidos. */
    fun cancel() {
        if (finished) return
        finished = true
        handler.removeCallbacks(restartListening)
        handler.removeCallbacks(finishAfterStop)
        recognizer.cancel()
        recognizer.destroy()
    }

    private fun listen() {
        if (finished || finishRequested) return
        runCatching {
            recognizer.startListening(intent)
            listening = true
        }.onFailure {
            listening = false
            scheduleNextListen()
        }
    }

    private fun scheduleNextListen() {
        if (!finished) handler.postDelayed(restartListening, 300L)
    }

    private fun fail() {
        if (finished) return
        finished = true
        handler.removeCallbacks(restartListening)
        handler.removeCallbacks(finishAfterStop)
        recognizer.destroy()
        onError()
    }

    private fun complete() {
        if (finished) return
        finished = true
        listening = false
        handler.removeCallbacks(restartListening)
        handler.removeCallbacks(finishAfterStop)
        recognizer.destroy()
        onResults(phrases.toList())
    }
}
