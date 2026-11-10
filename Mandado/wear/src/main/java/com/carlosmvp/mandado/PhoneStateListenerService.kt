package com.carlosmvp.mandado

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.StandardCharsets

class PhoneStateListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PHONE_STATE_CHANGED_PATH) return

        val expectedRevision = String(messageEvent.data, StandardCharsets.UTF_8).trim().toLongOrNull() ?: 0L
        Store.init(applicationContext)
        Log.i("CarritoListSync", "Received live phone update notification revision=$expectedRevision")
        Store.refreshFromPhone(expectedRevision)
    }
}
