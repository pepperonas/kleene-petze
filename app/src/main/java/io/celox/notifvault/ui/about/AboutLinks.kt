package io.celox.notifvault.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/** Everything the About section links to, in one place (pinned by AboutLinksTest). */
object AboutLinks {
    const val AUTHOR = "Martin Pfeffer"
    const val WEBSITE_LABEL = "celox.io"
    const val WEBSITE_URL = "https://celox.io"
    const val PRODUCT_URL = "https://kleene-petze.celox.io"
    const val REPO_URL = "https://github.com/pepperonas/kleene-petze"
    const val LICENSE_NAME = "MIT License"
    const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"
    const val CHANGELOG_URL = "$REPO_URL/blob/main/CHANGELOG.md"
    const val DONATE_EMAIL = "martin.pfeffer@celox.io"

    /** PayPal donation link — the same form Brutus and Flipper the Ripper use. */
    fun donateUrl(itemName: String = "Kleene Petze", email: String = DONATE_EMAIL): String =
        "https://www.paypal.com/donate/?business=$email&currency_code=EUR&item_name=" +
            URLEncoder.encode(itemName, "UTF-8").replace("+", "%20")
}

/** Opens [url] in the browser; false when the phone has nothing to open it with. */
fun Context.openUrl(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
