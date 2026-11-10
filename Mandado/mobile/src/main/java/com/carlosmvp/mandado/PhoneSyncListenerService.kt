package com.carlosmvp.mandado

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.StandardCharsets

class PhoneSyncListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PHONE_SYNC_REQUEST_PATH) return
        val requestId = String(messageEvent.data, StandardCharsets.UTF_8).trim()
        if (requestId.isEmpty()) return

        Store.init(applicationContext)
        Store.publishCurrentStateForSync(requestId)
    }
}
