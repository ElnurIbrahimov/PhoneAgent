package com.phoneagent.accessibility

object AppSafetyPolicy {

    private val denylistPrefixes = listOf(
        // Banking
        "com.ing.", "com.chase.", "com.bankofamerica.", "com.wellsfargo.",
        "com.citi.", "com.capitalone.", "com.usbank.", "com.pnc.",
        "com.td.", "com.rbc.", "com.scotiabank.", "com.bmo.",
        "com.monzo.", "com.revolut.", "com.starlingbank.", "com.n26.",
        "com.nubank.", "com.mercadopago.",
        // Payment
        "com.paypal.", "com.squareup.cash", "com.venmo.",
        "com.google.android.apps.nbu.paisa.",
        "com.stripe.",
        // Crypto
        "com.coinbase.", "com.binance.", "com.kraken.",
        "com.trustwallet.", "com.metamask.",
        // Password managers
        "com.lastpass.", "com.dashlane.", "com.onepassword.",
        "com.bitwarden.", "com.google.android.apps.password",
        // Authenticators
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator", "com.authy.",
        "com.duosecurity.",
        // System critical
        "com.android.settings.", "com.google.android.permissioncontroller"
    )

    fun isPackageDenied(packageName: String?): Boolean {
        if (packageName == null) return false
        return denylistPrefixes.any { packageName.startsWith(it) }
    }

    fun getDenyReason(packageName: String?): String? {
        if (!isPackageDenied(packageName)) return null
        return when {
            packageName?.contains("settings") == true -> "System settings are protected."
            packageName?.contains("password") == true || packageName?.contains("onepassword") == true
                || packageName?.contains("lastpass") == true || packageName?.contains("dashlane") == true
                || packageName?.contains("bitwarden") == true -> "Password managers are protected."
            packageName?.contains("authenticator") == true || packageName?.contains("auth") == true
                || packageName?.contains("duosecurity") == true -> "Authentication apps are protected."
            else -> "Banking and payment apps are protected from AI interaction."
        }
    }
}
