package io.legado.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class HunReaderDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun installedIdentityAndProvidersAreIsolated() {
        val suffix = if (BuildConfig.DEBUG) ".debug" else ""
        assertEquals("io.github.hunterxue.hunreader$suffix", context.packageName)
        assertEquals(
            if (BuildConfig.DEBUG) "HunReader Debug" else "HunReader",
            context.applicationInfo.loadLabel(context.packageManager).toString()
        )
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS or
                PackageManager.MATCH_DISABLED_COMPONENTS
        )
        val providers = info.providers.orEmpty()
        assertFalse(providers.any { it.name.contains("firebase", ignoreCase = true) })
        assertFalse(providers.single { it.name.endsWith(".ReaderProvider") }.enabled)
        val fileProvider = providers.single { it.name == "androidx.core.content.FileProvider" }
        assertEquals("${context.packageName}.fileProvider", fileProvider.authority)
        assertFalse(fileProvider.exported)
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.REQUEST_INSTALL_PACKAGES"))
    }

    @Test
    fun ownImportSchemeResolvesWithoutClaimingUpstreamSchemes() {
        val scheme = if (BuildConfig.DEBUG) "hunreader-debug" else "hunreader"
        fun resolve(candidate: String) = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("$candidate://import/httpTTS?src=example"))
                .setPackage(context.packageName),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        assertNotNull(resolve(scheme))
        assertEquals(null, resolve("legado"))
        assertEquals(null, resolve("yuedu"))
    }

    @Test
    fun packagedDataHasNoOnlinePresets() {
        val defaults = context.assets.list("defaultData").orEmpty().toSet()
        assertTrue(defaults.contains("txtTocRule.json"))
        listOf("bookSources", "rssSources", "httpTTS", "dictRules", "directLinkUpload").forEach {
            assertFalse(defaults.contains("$it.json"))
        }
    }
}
