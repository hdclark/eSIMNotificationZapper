package ca.hdclark.esimnotificationzapper

import java.util.Locale

/** Strict enough to avoid responding to generic "SIM" text in unrelated dialogs. */
object WarningMatcher {
    private val whitespace = Regex("\\s+")

    fun normalize(value: CharSequence?): String =
        value?.toString()?.trim()?.replace(whitespace, " ")
            ?.lowercase(Locale.ROOT).orEmpty()

    fun validTarget(target: String): Boolean =
        normalize(target).length >= 8 && normalize(target).contains(" ")

    fun warningMatches(value: CharSequence?, target: String): Boolean =
        validTarget(target) && normalize(value).contains(normalize(target))

    fun buttonMatches(value: CharSequence?, label: String): Boolean =
        normalize(label).isNotEmpty() && normalize(value) == normalize(label)
}
