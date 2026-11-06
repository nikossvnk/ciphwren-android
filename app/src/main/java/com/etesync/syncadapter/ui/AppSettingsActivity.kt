/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import at.bitfire.cert4android.CustomCertManager
import com.etesync.syncadapter.App
import com.etesync.syncadapter.BuildConfig
import com.etesync.syncadapter.R
import com.etesync.syncadapter.model.ServiceDB
import com.etesync.syncadapter.model.Settings
import com.etesync.syncadapter.utils.HintManager
import com.etesync.syncadapter.utils.LanguageUtils
import com.etesync.syncadapter.utils.defaultSharedPreferences
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URISyntaxException

class AppSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        padContentForSystemBars()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, SettingsFragment())
                    .commit()
        }
    }


    class SettingsFragment : PreferenceFragmentCompat() {
        internal lateinit var dbHelper: ServiceDB.OpenHelper
        internal lateinit var settings: Settings

        internal lateinit var prefPreferTasksOrg: SwitchPreferenceCompat

        internal lateinit var prefResetHints: Preference
        internal lateinit var prefOverrideProxy: SwitchPreferenceCompat
        internal lateinit var prefDistrustSystemCerts: SwitchPreferenceCompat

        internal lateinit var prefProxyHost: EditTextPreference
        internal lateinit var prefProxyPort: EditTextPreference

        override fun onCreate(savedInstanceState: Bundle?) {
            dbHelper = ServiceDB.OpenHelper(requireContext())
            settings = Settings(dbHelper.readableDatabase)

            super.onCreate(savedInstanceState)
        }

        override fun onDestroy() {
            super.onDestroy()
            dbHelper.close()
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            addPreferencesFromResource(R.xml.settings_app)

            findPreference<Preference>("notification_settings")!!.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    onPreferenceClickListener = Preference.OnPreferenceClickListener {
                        startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                        false
                    }
                else
                    isVisible = false
            }

            prefResetHints = findPreference("reset_hints")!!

            prefOverrideProxy = findPreference("override_proxy")!!
            prefOverrideProxy.isChecked = settings.getBoolean(App.OVERRIDE_PROXY, false)
            prefOverrideProxy.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                settings.putBoolean(App.OVERRIDE_PROXY, newValue as Boolean)
                true
            }

            prefPreferTasksOrg = findPreference("prefer_tasksorg")!!
            prefPreferTasksOrg.isChecked = requireContext().defaultSharedPreferences.getBoolean(App.PREFER_TASKSORG, false)
            prefPreferTasksOrg.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                requireContext().defaultSharedPreferences.edit().putBoolean(App.PREFER_TASKSORG, newValue as Boolean).apply()
                Snackbar.make(requireView(), getString(R.string.app_settings_prefer_tasksorg_snack), Snackbar.LENGTH_LONG).show()
                true
            }

            prefProxyHost = findPreference("proxy_host")!!
            val proxyHost = settings.getString(App.OVERRIDE_PROXY_HOST, App.OVERRIDE_PROXY_HOST_DEFAULT)
            prefProxyHost.text = proxyHost
            prefProxyHost.summary = proxyHost
            prefProxyHost.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val host = newValue as String
                try {
                    URI(null, host, null, null)
                } catch (e: URISyntaxException) {
                    Snackbar.make(requireView(), e.localizedMessage ?: e.toString(), Snackbar.LENGTH_LONG).show()
                    return@OnPreferenceChangeListener false
                }

                settings.putString(App.OVERRIDE_PROXY_HOST, host)
                prefProxyHost.summary = host
                true
            }

            prefProxyPort = findPreference("proxy_port")!!
            val proxyPort = settings.getString(App.OVERRIDE_PROXY_PORT, App.OVERRIDE_PROXY_PORT_DEFAULT.toString())
            prefProxyPort.text = proxyPort
            prefProxyPort.summary = proxyPort
            prefProxyPort.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                var port: Int
                try {
                    port = Integer.parseInt(newValue as String)
                } catch (e: NumberFormatException) {
                    port = App.OVERRIDE_PROXY_PORT_DEFAULT
                }

                settings.putInt(App.OVERRIDE_PROXY_PORT, port)
                prefProxyPort.text = port.toString()
                prefProxyPort.summary = port.toString()
                true
            }

            prefDistrustSystemCerts = findPreference("distrust_system_certs")!!
            prefDistrustSystemCerts.isChecked = settings.getBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, false)

            findPreference<Preference>("reset_certificates")!!.apply {
                isVisible = BuildConfig.customCerts
                isEnabled = true
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    resetCertificates()
                    false
                }
            }


            val prefChangeNotification = findPreference<SwitchPreferenceCompat>("show_change_notification")!!
            prefChangeNotification.isChecked = requireContext().defaultSharedPreferences.getBoolean(App.CHANGE_NOTIFICATION, true)

            initSelectLanguageList()
        }

        private fun initSelectLanguageList() {
            val listPreference = findPreference<ListPreference>("select_language")!!
            val applicationContext = requireContext().applicationContext
            lifecycleScope.launch {
                val locales = withContext(Dispatchers.Default) {
                    LanguageUtils.getAppLanguages(applicationContext)
                }

                listPreference.entries = locales.displayNames
                listPreference.entryValues = locales.localeData
                listPreference.value = LanguageUtils.getLanguage()
                listPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    // AppCompat recreates the activities to apply the language
                    LanguageUtils.setLanguage(newValue.toString())
                    true
                }
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            if (preference === prefResetHints)
                resetHints()
            else if (preference === prefDistrustSystemCerts)
                setDistrustSystemCerts(preference.isChecked)
            else
                return false
            return true
        }

        private fun resetHints() {
            HintManager.resetHints(requireContext())
            Snackbar.make(requireView(), R.string.app_settings_reset_hints_success, Snackbar.LENGTH_LONG).show()
        }

        private fun setDistrustSystemCerts(distrust: Boolean) {
            settings.putBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, distrust)
        }

        private fun resetCertificates() {
            if (CustomCertManager.resetCertificates(requireActivity()))
                Snackbar.make(requireView(), getString(R.string.app_settings_reset_certificates_success), Snackbar.LENGTH_LONG).show()
        }
    }
}
