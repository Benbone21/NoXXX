package com.noxxx.guard.monitor

/**
 * Apps that legitimately use Android's FLAG_SECURE (anti-screenshot) for security reasons, not to
 * hide content. These are exempt from the private browsing / secure screen block.
 *
 * Banking, payment, password manager, 2FA, and secure storage apps. Messaging apps that use
 * FLAG_SECURE for media are already in CommApps and exempted from image scanning; they don't
 * need to be here.
 */
object SecureScreenExemptions {
    val PACKAGES = setOf(
        // Banking
        "com.usbank.mobilebanking",           // US Bank
        "com.chase.sigbank",                  // Chase Mobile
        "com.wellsfargo.wfmobile",            // Wells Fargo
        "com.citibank.mobile",                // Citibank
        "com.bankofamerica.mobilebanking",    // Bank of America
        "com.pnc.mobilebanking",              // PNC Bank
        "com.discover.mobile",                // Discover
        // Payments / wallets
        "com.venmo",                          // Venmo
        "com.paypal.android.p2pmobile",       // PayPal
        "com.google.android.apps.walletnfcrel", // Google Wallet
        // Password managers
        "com.x8bit.bitwarden",                // Bitwarden
        "com.1password.1password",            // 1Password
        "com.lastpass.lpandroid",             // LastPass
        "com.dashlane.dashlane",              // Dashlane
        // Authenticators / 2FA
        "proton.android.authenticator",       // Proton Authenticator
        "com.google.android.apps.authenticator2", // Google Authenticator
        "com.azure.authenticator",            // Microsoft Authenticator
        "com.duosecurity.duomobile",          // Duo Mobile
        "com.beemdevelopment.aegis",          // Aegis
        "com.authy.authy",                    // Authy
        "com.twofasapp",                      // 2FAS
        "com.x8bit.bitwarden.authenticator",  // Bitwarden Authenticator
        // VPNs (protect their keys)
        "com.wireguard.android",              // WireGuard
        "com.zaneschepke.wireguardautotunnel", // WG Tunnel
        // Secure storage
        "com.samsung.knox.securefolder",      // Samsung Secure Folder
        // Proton (encrypted email, VPN)
        "ch.protonmail.android",              // Proton Mail
        "ch.protonvpn.android",               // Proton VPN
    )

    /**
     * Preinstalled system apps (fingerprint setup, Play Store payments, Samsung Pass) use secure screens
     * for real security, so they're exempt too, except browsers, whose private tabs stay blocked.
     */
    fun isExempt(pkg: String, isSystemApp: Boolean, isBrowser: Boolean, userAllowed: Set<String> = emptySet()) =
        pkg in PACKAGES || pkg in userAllowed || (isSystemApp && !isBrowser)
}