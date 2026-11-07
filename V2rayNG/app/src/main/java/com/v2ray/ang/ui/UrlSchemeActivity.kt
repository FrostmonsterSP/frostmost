package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.ui.main.deepLinkExtra

/**
 * Entry for `<scheme>://install-sub?url=...`, `install-config` and shared text. It only validates the
 * address and hands it to [MainActivity], which asks, imports and reports; nothing is imported here and
 * no address is logged.
 */
class UrlSchemeActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity must not send the same link twice.
        if (savedInstanceState == null) {
            startActivity(
                Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_IMPORT_LINK, linkFrom(intent))
            )
        }
        finish()
    }

    @Composable
    override fun ScreenContent() {
    }

    /** The validated address, or "" when the intent holds no acceptable link. */
    private fun linkFrom(source: Intent): String = when (source.action) {
        // Shared text goes the same way as install-config: an http(s) address or a server link.
        Intent.ACTION_SEND ->
            if (source.type == "text/plain") deepLinkExtra("install-config", source.getStringExtra(Intent.EXTRA_TEXT)) else ""
        Intent.ACTION_VIEW -> {
            val data = source.data
            deepLinkExtra(data?.host, data?.getQueryParameter("url"), data?.fragment)
        }
        else -> ""
    }
}
