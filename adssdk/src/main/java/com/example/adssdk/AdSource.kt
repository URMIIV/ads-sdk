package com.example.adssdk

enum class AdSource {
    OWN,
    GOOGLE,
    OWN_THEN_GOOGLE,
    GOOGLE_THEN_OWN,
    /** Picks OWN or GOOGLE at random every time; the other one is the fallback. */
    RANDOM;

    /** Order in which the sources are tried. */
    internal fun chain(): List<AdSource> = when (this) {
        OWN -> listOf(OWN)
        GOOGLE -> listOf(GOOGLE)
        OWN_THEN_GOOGLE -> listOf(OWN, GOOGLE)
        GOOGLE_THEN_OWN -> listOf(GOOGLE, OWN)
        RANDOM -> listOf(OWN, GOOGLE).shuffled()
    }

    companion object {
        /** Reads a value written in ads.json, e.g. "google", "own", "google_then_own". */
        internal fun parse(text: String?): AdSource? {
            if (text == null) return null
            val key = text.trim().lowercase().replace(Regex("[^a-z]"), "")
            return when (key) {
                "own", "sdk", "custom" -> OWN
                "google", "admob" -> GOOGLE
                "ownthengoogle", "ownfirst" -> OWN_THEN_GOOGLE
                "googlethenown", "googlefirst" -> GOOGLE_THEN_OWN
                "random", "mix", "both" -> RANDOM
                else -> null
            }
        }
    }
}