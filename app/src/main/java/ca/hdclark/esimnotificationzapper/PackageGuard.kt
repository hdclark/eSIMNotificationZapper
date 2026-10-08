package ca.hdclark.esimnotificationzapper

/** Conservative default. Additional vendor packages can be enabled from the UI. */
object PackageGuard {
    fun isProbablySystem(name: String): Boolean {
        return name == "android" ||
            name == "com.android.phone" ||
            name.startsWith("com.android.") ||
            name.startsWith("com.samsung.") ||
            name.startsWith("com.sec.") ||
            name.startsWith("com.qualcomm.") ||
            name.startsWith("org.codeaurora.")
    }
}
