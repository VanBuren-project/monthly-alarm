package com.anqi.app

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.alarm.OemAutostart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guards the deep-link table itself.
 *
 * Getting a component name wrong is invisible at compile time and silently
 * degrades to the app-info page at runtime, which on a MIUI phone is the
 * difference between "one tap and one switch" and "go hunting through the
 * security app". So the MIUI entries are pinned to the exact names AutoStarter
 * ships, and unknown brands must fall back rather than guess.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OemAutostartTest {

    @Test
    fun xiaomiOpensTheAutostartManagerFirst() {
        val first = OemAutostart.candidatesFor("Xiaomi", "Redmi").first()
        assertEquals("com.miui.securitycenter", first.packageName)
        assertEquals(
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
            first.className
        )
    }

    @Test
    fun pocoIsTreatedAsXiaomi() {
        // POCO runs the same security centre; MANUFACTURER and BRAND disagree on
        // which of the three names they report, so both are checked.
        assertTrue(OemAutostart.candidatesFor("Xiaomi", "POCO").isNotEmpty())
        assertTrue(OemAutostart.candidatesFor("POCO", "POCO").isNotEmpty())
    }

    @Test
    fun brandAndManufacturerAreBothConsidered() {
        // A Redmi phone reports MANUFACTURER=Xiaomi, BRAND=Redmi. Matching only
        // one of the two would miss it.
        val byBrand = OemAutostart.candidatesFor("unknown", "Redmi")
        assertEquals("com.miui.securitycenter", byBrand.first().packageName)
    }

    @Test
    fun theOtherMainlandOemsHaveTheirOwnEntries() {
        for ((manufacturer, pkg) in listOf(
            "HUAWEI" to "com.huawei.systemmanager",
            "OPPO" to "com.coloros.safecenter",
            "vivo" to "com.vivo.permissionmanager",
            "samsung" to "com.samsung.android.lool",
            "Meizu" to "com.meizu.safe"
        )) {
            val first = OemAutostart.candidatesFor(manufacturer, manufacturer)
            assertTrue("$manufacturer must have a deep link", first.isNotEmpty())
            assertEquals(manufacturer, pkg, first.first().packageName)
        }
    }

    @Test
    fun anUnknownBrandOffersNoDeepLinkInsteadOfGuessing() {
        // Sending a Pixel owner into some random vendor settings activity would
        // be worse than sending them to app info.
        assertTrue(OemAutostart.candidatesFor("Google", "Pixel").isEmpty())
        assertNull(OemAutostart.oemLabel("Google", "Pixel"))
    }

    @Test
    fun theLabelNamesTheBrandForTheCopy() {
        assertEquals("小米 / 红米", OemAutostart.oemLabel("Xiaomi", "Redmi"))
        assertEquals("华为 / 荣耀", OemAutostart.oemLabel("HONOR", "HONOR"))
    }

    @Test
    fun candidatesAreComponentNamesOnlyAndNeverRepeat() {
        for (manufacturer in listOf("Xiaomi", "HUAWEI", "OPPO", "vivo", "samsung", "Meizu", "ASUS")) {
            val list: List<ComponentName> = OemAutostart.candidatesFor(manufacturer, manufacturer)
            assertEquals("$manufacturer has a duplicate entry", list.size, list.distinct().size)
        }
    }
}
