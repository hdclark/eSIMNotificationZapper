package ca.hdclark.esimnotificationzapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningMatcherTest {
    @Test fun acceptsExactSamsungWarning() {
        assertTrue(WarningMatcher.warningMatches(
            "Check SIM card tray", "Check SIM card tray"))
    }

    @Test fun acceptsEmbeddedTitleWithDifferentWhitespaceAndCase() {
        assertTrue(WarningMatcher.warningMatches(
            "Warning!  CHECK   sim card tray. Please inspect the tray.",
            "check sim card tray"))
    }

    @Test fun rejectsUnrelatedOrBroadTarget() {
        assertFalse(WarningMatcher.warningMatches("Check Wi-Fi", "Check SIM card tray"))
        assertFalse(WarningMatcher.warningMatches("SIM", "SIM"))
        assertFalse(WarningMatcher.warningMatches(null, "Check SIM card tray"))
    }

    @Test fun buttonRequiresExactLabel() {
        assertTrue(WarningMatcher.buttonMatches(" OK ", "ok"))
        assertFalse(WarningMatcher.buttonMatches("OK to erase all data", "OK"))
    }

    @Test fun limitsDefaultPackages() {
        assertTrue(PackageGuard.isProbablySystem("com.android.systemui"))
        assertTrue(PackageGuard.isProbablySystem("com.samsung.android.app.telephonyui"))
        assertFalse(PackageGuard.isProbablySystem("com.example.game"))
    }
}
