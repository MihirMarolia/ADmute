package com.example.admutetv

import java.util.Locale

/**
 * Hostname suffixes that serve video-ad manifests, trackers, or creatives.
 * A lookup for one of these while media plays is treated as evidence that an
 * ad break is starting, regardless of which application is in the foreground.
 */
internal object AdHosts {

    private val SUFFIXES = setOf(
        "2mdn.net",
        "adcolony.com",
        "adform.net",
        "adnxs.com",
        "adsafeprotected.com",
        "adsrvr.org",
        "adservice.google.com",
        "adsystem.com",
        "amazon-adsystem.com",
        "aniview.com",
        "app-measurement.com",
        "beachfront.com",
        "criteo.com",
        "criteo.net",
        "doubleclick.net",
        "doubleverify.com",
        "freewheel.tv",
        "fwmrm.net",
        "googleadservices.com",
        "googlesyndication.com",
        "imasdk.googleapis.com",
        "innovid.com",
        "moatads.com",
        "pubmatic.com",
        "rubiconproject.com",
        "scorecardresearch.com",
        "serving-sys.com",
        "smartadserver.com",
        "spotxchange.com",
        "springserve.com",
        "taboola.com",
        "teads.tv",
        "tremorhub.com",
        "yieldmo.com"
    )

    fun isAdHost(host: String): Boolean {
        val candidate = host.lowercase(Locale.ROOT).trimEnd('.')
        return SUFFIXES.any { suffix ->
            candidate == suffix || candidate.endsWith(".$suffix")
        }
    }
}
