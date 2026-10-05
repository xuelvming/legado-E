package io.legado.app

import io.legado.app.data.entities.HttpTTS
import io.legado.app.constant.PreferKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class HunReaderConfigurationTest {

    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    private fun elements(path: String, tag: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val nodes = factory.newDocumentBuilder().parse(File("src/main/$path"))
            .getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun manifestKeepsTtsAndFileSharingButDisablesPublisherSurfaces() {
        assertEquals(
            "false",
            elements("AndroidManifest.xml", "application").single()
                .getAttributeNS(androidNamespace, "allowBackup")
        )
        val permissions = elements("AndroidManifest.xml", "uses-permission")
            .map { it.getAttributeNS(androidNamespace, "name") }
        assertTrue(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES"))

        val providers = elements("AndroidManifest.xml", "provider")
        val reader = providers.single {
            it.getAttributeNS(androidNamespace, "name") == ".api.ReaderProvider"
        }
        assertEquals("false", reader.getAttributeNS(androidNamespace, "enabled"))
        val file = providers.single {
            it.getAttributeNS(androidNamespace, "name") == "androidx.core.content.FileProvider"
        }
        assertEquals("\${applicationId}.fileProvider", file.getAttributeNS(androidNamespace, "authorities"))
        assertEquals("false", file.getAttributeNS(androidNamespace, "exported"))
        assertTrue(elements("AndroidManifest.xml", "service").any {
            it.getAttributeNS(androidNamespace, "name") == ".service.HttpReadAloudService"
        })

        val schemes = elements("AndroidManifest.xml", "data")
            .map { it.getAttributeNS(androidNamespace, "scheme") }
        assertTrue(schemes.contains("\${import_scheme}"))
        assertFalse(schemes.contains("legado"))
        assertFalse(schemes.contains("yuedu"))
    }

    @Test
    fun onlinePresetsAreAbsentButLocalReadingDefaultsRemain() {
        val assets = File("src/main/assets/defaultData")
        listOf("bookSources", "rssSources", "httpTTS", "dictRules", "directLinkUpload").forEach {
            assertFalse("$it must not be bundled", File(assets, "$it.json").exists())
        }
        listOf("txtTocRule", "themeConfig", "readConfig", "keyboardAssists", "coverRule").forEach {
            assertTrue("$it must remain available", File(assets, "$it.json").isFile)
        }
    }

    @Test
    fun updaterAndRestoreOnlinePresetsAreNotOffered() {
        listOf("about", "pref_config_other").forEach { name ->
            val keys = elements("res/xml/$name.xml", "*")
                .map { it.getAttributeNS(androidNamespace, "key") }
            assertFalse(keys.contains("check_update"))
            assertFalse(keys.contains("autoUpdateVariant"))
            assertFalse(keys.contains("updateToVariant"))
        }
        listOf("speak_engine", "rss_source", "dict_rule", "direct_link_upload_config").forEach { name ->
            val ids = elements("res/menu/$name.xml", "item")
                .map { it.getAttributeNS(androidNamespace, "id") }
            assertFalse(ids.contains("@+id/menu_default"))
            assertFalse(ids.contains("@+id/menu_import_default"))
        }
    }

    @Test
    fun automaticTextSelectionIsOptInWithoutChangingTheExistingMenu() {
        val preferences = elements("res/xml/pref_config_read.xml", "*")
        fun preference(key: String) = preferences.single {
            it.getAttributeNS(androidNamespace, "key") == key
        }
        assertEquals(
            "",
            preference(PreferKey.selectionApp).getAttributeNS(androidNamespace, "defaultValue")
        )
        assertEquals(
            "false",
            preference(PreferKey.autoOpenSelectionApp).getAttributeNS(androidNamespace, "defaultValue")
        )
        assertEquals(
            "false",
            preference(PreferKey.expandTextMenu).getAttributeNS(androidNamespace, "defaultValue")
        )
        assertEquals(
            listOf("menu_replace", "menu_copy", "menu_bookmark", "menu_aloud", "menu_dict"),
            elements("res/menu/content_select_action.xml", "item").take(5).map {
                it.getAttributeNS(androidNamespace, "id").substringAfter("/")
            }
        )
    }

    @Test
    fun readAloudAppearanceIsScopedToTheCurrentParagraph() {
        val preferences = elements("res/xml/pref_config_aloud.xml", "*")
        fun preference(key: String) = preferences.single {
            it.getAttributeNS(androidNamespace, "key") == key
        }
        assertEquals(
            "true",
            preference(PreferKey.ttsHighlightColor)
                .getAttributeNS(androidNamespace, "defaultValue")
        )
        assertEquals(
            "false",
            preference(PreferKey.ttsHighlightBold)
                .getAttributeNS(androidNamespace, "defaultValue")
        )
        assertEquals(
            "0",
            preference(PreferKey.ttsHighlightUnderline)
                .getAttributeNS(androidNamespace, "defaultValue")
        )
        assertFalse(
            elements("res/layout/dialog_read_bg_text.xml", "*").any {
                it.getAttributeNS(androidNamespace, "id").endsWith("/sp_underline")
            }
        )
    }

    @Test
    fun textSelectionUsesNarrowPackageVisibility() {
        val queries = elements("AndroidManifest.xml", "queries").single()
        val intents = queries.getElementsByTagName("intent")
        val processText = (0 until intents.length).map { intents.item(it) as Element }.single {
            val actions = it.getElementsByTagName("action")
            (0 until actions.length).any { index ->
                (actions.item(index) as Element).getAttributeNS(androidNamespace, "name") ==
                    "android.intent.action.PROCESS_TEXT"
            }
        }
        val data = processText.getElementsByTagName("data").item(0) as Element
        assertEquals("text/plain", data.getAttributeNS(androidNamespace, "mimeType"))
        assertFalse(elements("AndroidManifest.xml", "uses-permission").any {
            it.getAttributeNS(androidNamespace, "name") == "android.permission.QUERY_ALL_PACKAGES"
        })
    }

    @Test
    fun importsUserHttpTtsWithoutNetworkOrPublisherIdentity() {
        val tts = HttpTTS.fromJsonArray(
            """[{"id":-42,"name":"Personal Azure TTS","url":"https://example.invalid/tts",
                "contentType":"audio/mpeg","header":"{\"Authorization\":\"Bearer example\"}",
                "jsLib":"var customVoice = 'personal';"}]"""
        ).getOrThrow().single()
        assertEquals(-42L, tts.id)
        assertEquals("Personal Azure TTS", tts.name)
        assertEquals("https://example.invalid/tts", tts.url)
        assertEquals("audio/mpeg", tts.contentType)
        assertEquals("""{"Authorization":"Bearer example"}""", tts.header)
        assertEquals("var customVoice = 'personal';", tts.jsLib)
    }
}
