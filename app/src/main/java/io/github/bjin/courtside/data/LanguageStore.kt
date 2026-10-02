package io.github.bjin.courtside.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Native names stay recognizable even when the current UI language is unfamiliar. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("", ""),
    ENGLISH("en", "English"),
    SIMPLIFIED_CHINESE("zh-Hans", "简体中文"),
    TRADITIONAL_CHINESE("zh-Hant", "繁體中文"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    SPANISH("es", "Español"),
}

/** Android 13+ owns the persisted app locale; older Android versions use local preferences. */
class LanguageStore(private val context: Context) {
    private val _language = MutableStateFlow(read())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun select(language: AppLanguage) {
        if (language == _language.value) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language.tag)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(KEY, language.tag)
            }
        }
        _language.value = language
    }

    /** Also reflects changes made in Android's App languages settings. */
    fun refresh() {
        _language.value = read()
    }

    private fun read(): AppLanguage {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        }
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.SYSTEM
    }

    companion object {
        private const val PREFS = "language"
        private const val KEY = "tag"

        /** The default resources are English, so unsupported system languages fall back to English. */
        fun localizedContext(context: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
            val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            if (tag.isEmpty()) return context
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            }
            return context.createConfigurationContext(configuration)
        }
    }
}
