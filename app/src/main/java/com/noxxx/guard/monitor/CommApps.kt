package com.noxxx.guard.monitor

/**
 * Messaging and video-call apps that image detection skips by default. They show your own camera on
 * calls (changing clothes, moving around) and private photos, which trip the detector with false
 * alarms. Feed-based social apps are deliberately left out: they are common vectors for explicit
 * images, so they stay scanned. The user can scan these too (one toggle) or exempt any app by hand.
 */
object CommApps {
    val PACKAGES = setOf(
        // Messaging
        "org.thoughtcrime.securesms",        // Signal
        "com.whatsapp", "com.whatsapp.w4b",  // WhatsApp / Business
        "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus",
        "com.facebook.orca", "com.facebook.mlite", // Messenger / Lite
        "com.google.android.apps.messaging", // Google Messages
        "com.samsung.android.messaging",     // Samsung Messages
        "com.viber.voip",
        "jp.naver.line.android",             // LINE
        "com.tencent.mm",                    // WeChat
        "com.kakao.talk",                    // KakaoTalk
        "org.thoughtcrime.securesms.molly",  // Molly (Signal fork)
        "im.vector.app", "chat.schildi.android", // Element / Matrix
        // Video calls / conferencing
        "com.google.android.apps.tachyon",   // Google Meet (Duo)
        "com.google.android.apps.meetings",
        "us.zoom.videomeetings",
        "com.microsoft.teams",
        "com.skype.raider",
        "com.webex.meetings",
        "com.discord",
    )

    fun isCommApp(pkg: String) = pkg in PACKAGES
}
