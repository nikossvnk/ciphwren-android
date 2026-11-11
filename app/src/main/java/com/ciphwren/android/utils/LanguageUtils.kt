package com.ciphwren.android.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ciphwren.android.App
import com.ciphwren.android.R
import com.ciphwren.android.log.Logger
import com.ciphwren.android.model.ServiceDB
import com.ciphwren.android.model.Settings
import java.util.*

/**
 * Created by tal on 11/09/17.
 */

object LanguageUtils {

    fun getAppLanguages(context: Context): LocaleList {
        val locales = Locale.getAvailableLocales()
        Arrays.sort(locales) { aLocale, aT1 -> aLocale.displayName.compareTo(aT1.displayName) }
        val localeData = ArrayList<String>(locales.size + 1)
        val displayNames = ArrayList<String>(locales.size + 1)
        localeData += App.DEFAULT_LANGUAGE
        displayNames += context.getString(R.string.app_settings_force_language_default)
        for (locale in locales) {
            localeData += locale.toLanguageTag()
            displayNames += locale.displayName
        }
        return LocaleList(localeData.toTypedArray(), displayNames.toTypedArray())
    }

    /**
     * @return language tag of the language chosen for the app, or [App.DEFAULT_LANGUAGE] if
     * the app follows the system language
     */
    fun getLanguage(): String =
            AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag() ?: App.DEFAULT_LANGUAGE

    /**
     * Sets the per-app language. Activities are recreated by AppCompat to apply it.
     *
     * @param languageTag IETF BCP 47 language tag or [App.DEFAULT_LANGUAGE] to follow the system language
     */
    fun setLanguage(languageTag: String) {
        val locales = if (languageTag == App.DEFAULT_LANGUAGE)
            LocaleListCompat.getEmptyLocaleList()
        else
            LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * Older versions stored the forced language in the settings database and applied it by
     * themselves. Moves such a setting to the per-app language of AppCompat/Android.
     */
    fun migrateForcedLanguage(context: Context) {
        val serviceDB = ServiceDB.OpenHelper(context)
        try {
            val settings = Settings(serviceDB.writableDatabase)
            val lang = settings.getString(App.FORCE_LANGUAGE, null) ?: return
            if (lang != App.DEFAULT_LANGUAGE) {
                val data = lang.split(";")
                val locale = Locale.Builder()
                        .setLanguage(data.getOrNull(0))
                        .setRegion(data.getOrNull(1))
                        .setVariant(data.getOrNull(2))
                        .build()
                Logger.log.info("Migrating forced language $lang to per-app language ${locale.toLanguageTag()}")
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
            }
            settings.remove(App.FORCE_LANGUAGE)
        } catch (e: Exception) {
            Logger.log.warning("Couldn't migrate forced language: $e")
        } finally {
            serviceDB.close()
        }
    }

    class LocaleList internal constructor(val localeData: Array<String>, val displayNames: Array<String>)
}
