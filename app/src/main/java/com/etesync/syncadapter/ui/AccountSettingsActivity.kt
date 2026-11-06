/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.accounts.Account
import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.content.SyncStatusObserver
import android.os.Bundle
import android.provider.CalendarContract
import android.text.TextUtils
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NavUtils
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.preference.*
import at.bitfire.ical4android.TaskProvider.Companion.TASK_PROVIDERS
import com.etebase.client.EtebaseException
import com.etesync.syncadapter.*
import com.etesync.syncadapter.Constants.KEY_ACCOUNT
import com.etesync.syncadapter.R
import com.etesync.syncadapter.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountSettingsActivity : BaseActivity() {
    private lateinit var account: Account

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        padContentForSystemBars()

        account = BundleCompat.getParcelable(intent.extras!!, KEY_ACCOUNT, Account::class.java)!!
        title = getString(R.string.settings_title, account.name)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            val frag = AccountSettingsFragment()
            frag.arguments = intent.extras
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, frag)
                    .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val intent = Intent(this, AccountActivity::class.java)
            intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
            NavUtils.navigateUpTo(this, intent)
            return true
        } else
            return false
    }
}


class AccountSettingsFragment : PreferenceFragmentCompat() {
    private val model: AccountSettingsViewModel by viewModels()
    internal lateinit var account: Account

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = BundleCompat.getParcelable(requireArguments(), KEY_ACCOUNT, Account::class.java)!!

        model.initialize(account)
        model.settings.observe(this) { settings ->
            onSettingsLoaded(settings)
        }
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.settings_account)
    }

    private fun onSettingsLoaded(settings: AccountSettings?) {
        if (settings == null) {
            activity?.finish()
            return
        }
        // EteSync 1.0 accounts have no Etebase session, so the account can't be managed anymore
        val isSupported = !settings.isEteSync1Account

        // Category: dashboard
        val prefManageAccount = findPreference<Preference>("manage_account")!!
        prefManageAccount.isEnabled = isSupported
        prefManageAccount.onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
            val applicationContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val url = withContext(Dispatchers.IO) {
                        val httpClient = HttpClient.Builder(applicationContext).build()
                        val etebase = EtebaseLocalCache.getEtebase(applicationContext, httpClient.okHttpClient, settings)
                        etebase.fetchDashboardUrl()
                    }
                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (e: EtebaseException) {
                    val context = context
                    if (context != null) {
                        AlertDialog.Builder(context)
                                .setIcon(R.drawable.ic_error_dark)
                                .setTitle(R.string.exception)
                                .setMessage(e.localizedMessage)
                                .setPositiveButton(android.R.string.yes) { _, _ -> }
                                .show()
                    }
                }
            }
            true
        }

        // Category: encryption
        val prefEncryptionPassword = findPreference<Preference>("password")!!
        prefEncryptionPassword.isEnabled = isSupported
        prefEncryptionPassword.onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
            startActivity(ChangeEncryptionPasswordActivity.newIntent(requireActivity(), account))
            true
        }

        val prefSync = findPreference<ListPreference>("sync_interval")!!
        val syncInterval = settings.getSyncInterval(CalendarContract.AUTHORITY) // Calendar is the baseline interval
        if (syncInterval != null) {
            prefSync.value = syncInterval.toString()
            if (syncInterval == AccountSettings.SYNC_INTERVAL_MANUALLY)
                prefSync.setSummary(R.string.settings_sync_summary_manually)
            else
                prefSync.summary = getString(R.string.settings_sync_summary_periodically, prefSync.entry)
            prefSync.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val newInterval = (newValue as String).toLong()
                settings.setSyncInterval(App.addressBooksAuthority, newInterval)
                settings.setSyncInterval(CalendarContract.AUTHORITY, newInterval)
                TASK_PROVIDERS.forEach {
                    settings.setSyncInterval(it.authority, newInterval)
                }
                model.reload()
                false
            }
        } else {
            prefSync.isEnabled = false
            prefSync.setSummary(R.string.settings_sync_summary_not_available)
        }

        val prefWifiOnly = findPreference<SwitchPreferenceCompat>("sync_wifi_only")!!
        prefWifiOnly.isChecked = settings.syncWifiOnly
        prefWifiOnly.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, wifiOnly ->
            settings.setSyncWiFiOnly(wifiOnly as Boolean)
            model.reload()
            false
        }

        val prefWifiOnlySSID = findPreference<EditTextPreference>("sync_wifi_only_ssid")!!
        val onlySSID = settings.syncWifiOnlySSID
        prefWifiOnlySSID.text = onlySSID
        if (onlySSID != null)
            prefWifiOnlySSID.summary = getString(R.string.settings_sync_wifi_only_ssid_on, onlySSID)
        else
            prefWifiOnlySSID.setSummary(R.string.settings_sync_wifi_only_ssid_off)
        prefWifiOnlySSID.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val ssid = newValue as String
            settings.syncWifiOnlySSID = if (!TextUtils.isEmpty(ssid)) ssid else null
            model.reload()
            false
        }
    }
}


/**
 * Loads the [AccountSettings] of an account and reloads them when the sync settings change.
 * The value is null when the account isn't valid (anymore).
 */
class AccountSettingsViewModel(application: Application) : AndroidViewModel(application), SyncStatusObserver {
    val settings = MutableLiveData<AccountSettings?>()

    private var account: Account? = null
    private val listenerHandle: Any = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)

    fun initialize(account: Account) {
        if (this.account == null) {
            this.account = account
            reload()
        }
    }

    fun reload() {
        val account = account ?: return
        viewModelScope.launch {
            settings.value = withContext(Dispatchers.IO) {
                try {
                    AccountSettings(getApplication(), account)
                } catch (e: InvalidAccountException) {
                    null
                }
            }
        }
    }

    override fun onStatusChanged(which: Int) {
        Logger.log.fine("Reloading account settings")
        reload()
    }

    override fun onCleared() {
        ContentResolver.removeStatusChangeListener(listenerHandle)
    }
}
