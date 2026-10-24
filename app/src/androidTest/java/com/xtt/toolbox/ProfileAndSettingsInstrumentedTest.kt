package com.xtt.toolbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xtt.toolbox.data.profile.LocalProfileStore
import com.xtt.toolbox.data.search.SearchHistoryStore
import com.xtt.toolbox.data.settings.AppSettingsStore
import com.xtt.toolbox.data.settings.ThemeMode
import com.xtt.toolbox.domain.reading.ReaderSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAndSettingsInstrumentedTest {
    private lateinit var context: Context
    private lateinit var profile: LocalProfileStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("local_profile", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("reading_search", Context.MODE_PRIVATE).edit().clear().commit()
        profile = LocalProfileStore(context)
    }

    @After
    fun tearDown() {
        profile.clearAvatar()
        context.getSharedPreferences("local_profile", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("reading_search", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun nicknameAndThemePersistAcrossStoreInstances() {
        assertTrue(profile.saveNickname("  本地作者  ").isSuccess)
        assertEquals("本地作者", profile.load().nickname)

        val settings = AppSettingsStore(context)
        settings.setThemeMode(ThemeMode.DARK)
        settings.setReaderSettings(
            ReaderSettings(fontSizeSp = 20f, lineHeightSp = 34f),
        )
        val reopened = AppSettingsStore(context)
        assertEquals(ThemeMode.DARK, reopened.themeMode.value)
        assertEquals(20f, reopened.readerSettings.value.fontSizeSp, 0.01f)
        assertEquals(34f, reopened.readerSettings.value.lineHeightSp, 0.01f)
    }

    @Test
    fun punctuationWordCountSettingDefaultsOffAndPersists() {
        val settings = AppSettingsStore(context)
        assertFalse(settings.excludePunctuation.value)
        settings.setExcludePunctuation(true)
        assertTrue(settings.excludePunctuation.value)
        assertTrue(AppSettingsStore(context).excludePunctuation.value)
    }

    @Test
    fun searchHistoryPersistsAndDisablingClearsIt() {
        val settings = AppSettingsStore(context)
        assertTrue(settings.searchHistoryEnabled.value)

        val history = SearchHistoryStore(context, true)
        history.record("星海")
        history.record("Sea")
        history.record("sea")
        assertEquals(listOf("sea", "星海"), history.history.value)
        assertEquals(listOf("sea", "星海"), SearchHistoryStore(context, true).history.value)

        settings.setSearchHistoryEnabled(false)
        history.setEnabled(false)
        assertTrue(history.history.value.isEmpty())
        assertFalse(history.record("新书"))
        assertFalse(AppSettingsStore(context).searchHistoryEnabled.value)
        assertTrue(SearchHistoryStore(context, false).history.value.isEmpty())
    }

    @Test
    fun savedAvatarFileSurvivesStoreReload() = runBlocking {
        val bitmap = android.graphics.Bitmap.createBitmap(
            120,
            120,
            android.graphics.Bitmap.Config.ARGB_8888,
        ).apply { eraseColor(android.graphics.Color.GREEN) }
        val saved = profile.saveAvatarBitmap(bitmap)
        bitmap.recycle()
        assertTrue(saved.isSuccess)
        assertNotNull(profile.load().avatarFile)
        assertTrue(profile.load().avatarFile!!.length() > 0)
        assertTrue(profile.clearAvatar())
    }

    @Test
    fun boundsDecodeDoesNotReportValidImageAsUnreadable() = runBlocking {
        val source = android.graphics.Bitmap.createBitmap(
            120,
            240,
            android.graphics.Bitmap.Config.ARGB_8888,
        ).apply { eraseColor(android.graphics.Color.BLUE) }
        val file = java.io.File(context.cacheDir, "avatar-decode-test.png")
        file.outputStream().use {
            source.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        source.recycle()
        try {
            val result = profile.loadEditableAvatar(android.net.Uri.fromFile(file).toString())
            assertTrue(result.isSuccess)
            assertEquals(120, result.getOrThrow().width)
            assertEquals(240, result.getOrThrow().height)
        } finally {
            file.delete()
        }
    }
}
