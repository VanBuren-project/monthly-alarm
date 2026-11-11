package com.anqi.app

import android.content.ComponentName
import android.content.IntentFilter
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.alarm.OemAutostart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

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

    private val realManufacturer: String = Build.MANUFACTURER
    private val realBrand: String = Build.BRAND

    @After
    fun restoreBuildFields() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", realManufacturer)
        ReflectionHelpers.setStaticField(Build::class.java, "BRAND", realBrand)
    }

    private fun pretendToBe(manufacturer: String, brand: String) {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", manufacturer)
        ReflectionHelpers.setStaticField(Build::class.java, "BRAND", brand)
    }

    /**
     * Robolectric records `startActivity` without resolving it, so every deep
     * link would look like it opened. Real devices throw
     * ActivityNotFoundException instead, which is the whole basis of the
     * candidate loop and of the fallback to app info — so turn resolution
     * checking on for the tests that care.
     */
    private fun resolveIntents(app: android.app.Application) {
        shadowOf(app).checkActivities(true)
    }

    /**
     * Registers one activity of another app, the way a ROM would.
     *
     * Robolectric also insists that an explicit intent's *action* match a
     * declared filter before it will resolve it, which real Android does not do
     * for an explicit component. Declaring the filter is therefore part of
     * describing the fake ROM, not part of the thing under test.
     */
    private fun installEditor(packageName: String, className: String, action: String) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val componentName = ComponentName(packageName, className)
        shadowOf(app.packageManager).addActivityIfNotPresent(componentName)
        shadowOf(app.packageManager).addIntentFilterForActivity(
            componentName,
            IntentFilter(action)
        )
    }

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

    // -------------------------------------------------- the one-jump path (MIUI)

    @Test
    fun thePermissionEditorCarriesOurPackageName() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        installEditor(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
            "miui.intent.action.APP_PERM_EDITOR"
        )

        assertTrue(OemAutostart.openPermissionEditor(app))

        val started = shadowOf(app).nextStartedActivity
        assertNotNull("the editor must actually be started", started)
        assertEquals(
            "com.miui.securitycenter",
            started.component?.packageName
        )
        assertEquals(
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
            started.component?.className
        )
        // The extra is the whole trick: without it the editor opens on its own
        // permission list and the user has to find the app themselves.
        assertEquals(app.packageName, started.getStringExtra("extra_pkgname"))
        assertEquals("miui.intent.action.APP_PERM_EDITOR", started.action)
    }

    @Test
    fun aRomWithoutTheEditorFallsBackToTheLegacyClass() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        resolveIntents(app)
        // Only the old class exists: exactly the MIUI V7 situation.
        installEditor(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
            "miui.intent.action.APP_PERM_EDITOR"
        )

        assertTrue(OemAutostart.openPermissionEditor(app))
        assertEquals(
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
            shadowOf(app).nextStartedActivity.component?.className
        )
    }

    @Test
    fun aRomWithNeitherEditorReportsFailureInsteadOfPretending() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        resolveIntents(app)
        // No MIUI packages registered at all.
        assertFalse(OemAutostart.openPermissionEditor(app))
    }

    @Test
    fun xiaomiGoesStraightToThePermissionEditor() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        resolveIntents(app)
        pretendToBe("Xiaomi", "Redmi")
        installEditor(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
            "miui.intent.action.APP_PERM_EDITOR"
        )

        assertTrue(OemAutostart.openForManualSwitches(app))
        assertEquals(
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
            shadowOf(app).nextStartedActivity.component?.className
        )
    }

    @Test
    fun xiaomiFallsBackToAutostartWhenTheEditorIsMissing() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        resolveIntents(app)
        pretendToBe("Xiaomi", "Redmi")
        // A ROM where only the autostart list exists: the one-jump shortcut has
        // to give up rather than send the user nowhere.
        shadowOf(app.packageManager).addActivityIfNotPresent(
            OemAutostart.candidatesFor("Xiaomi", "Redmi").first()
        )

        assertTrue(OemAutostart.openForManualSwitches(app))
        assertEquals(
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
            shadowOf(app).nextStartedActivity.component?.className
        )
    }

    @Test
    fun anotherBrandStillGetsItsAutostartPage() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        pretendToBe("HUAWEI", "HONOR")
        shadowOf(app.packageManager).addActivityIfNotPresent(
            OemAutostart.candidatesFor("HUAWEI", "HONOR").first()
        )

        assertTrue(OemAutostart.openForManualSwitches(app))
        assertEquals(
            "com.huawei.systemmanager",
            shadowOf(app).nextStartedActivity.component?.packageName
        )
    }
}
