/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.BundleCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import at.bitfire.ical4android.TaskProvider.ProviderName
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.*
import com.etesync.syncadapter.Constants.KEY_ACCOUNT
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.ServiceDB
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.utils.installerPackageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.logging.Level

class DebugInfoActivity : BaseActivity() {
    private val model: ReportModel by viewModels()

    internal lateinit var tvReport: TextView
    internal var report: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_debug_info)
        tvReport = findViewById(R.id.text_report)

        model.generate(intent.extras)
        model.report.observe(this) { data ->
            report = data
            tvReport.text = data
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_debug_info, menu)
        return true
    }


    fun onShare(item: MenuItem) {
        val report = report ?: return
        val context = applicationContext
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "debug-info")
                dir.mkdirs()
                val file = File(dir, "debug-info.txt")
                file.writeText(report)
                FileProvider.getUriForFile(context, context.getString(R.string.authority_log_provider), file)
            }
            val share = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            share.clipData = ClipData.newRawUri(null, uri)
            startActivity(Intent.createChooser(share, getString(R.string.send)))
        }
    }


    class ReportModel(application: Application) : AndroidViewModel(application) {
        val report = MutableLiveData<String>()
        private var generating = false

        fun generate(extras: Bundle?) {
            if (generating)
                return
            generating = true

            viewModelScope.launch {
                report.value = withContext(Dispatchers.IO) {
                    generateReport(extras)
                }
            }
        }

        @SuppressLint("MissingPermission")
        private fun generateReport(extras: Bundle?): String {
            val context: Context = getApplication()
            var throwable: Throwable? = null
            var caller: String? = null
            var logs: String? = null
            var authority: String? = null
            var account: Account? = null
            var phase: String? = null

            if (extras != null) {
                throwable = BundleCompat.getSerializable(extras, KEY_THROWABLE, Throwable::class.java)
                caller = extras.getString(KEY_CALLER)
                logs = extras.getString(KEY_LOGS)
                account = BundleCompat.getParcelable(extras, KEY_ACCOUNT, Account::class.java)
                authority = extras.getString(KEY_AUTHORITY)
                phase = if (extras.containsKey(KEY_PHASE)) context.getString(extras.getInt(KEY_PHASE)) else null
            }

            val report = StringBuilder("--- BEGIN DEBUG INFO ---\n")

            // begin with most specific information

            if (phase != null)
                report.append("SYNCHRONIZATION INFO\nSynchronization phase: ").append(phase).append("\n")
            if (account != null)
                report.append("Account name: ").append(account.name).append("\n")
            if (authority != null)
                report.append("Authority: ").append(authority).append("\n")
            if (caller != null)
                report.append("Debug activity source: ").append(caller).append("\n")

            if (throwable != null)
                report.append("\nEXCEPTION:\n")
                        .append(throwable.stackTraceToString())

            if (logs != null)
                report.append("\nLOGS:\n").append(logs).append("\n")

            try {
                val installedFrom = installerPackageName(context) ?: "APK (directly)"
                report.append("\nSOFTWARE INFORMATION\n" + "EteSync version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(") ").append("\n")
                        .append("Installed from: ").append(installedFrom).append("\n")
            } catch (ex: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't get software information", ex)
            }

            report.append("CONFIGURATION\n")
            // power saving
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
            if (powerManager != null)
                report.append("Power saving disabled: ")
                        .append(if (powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) "yes" else "no")
                        .append("\n")
            // permissions
            val permissions = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) + ProviderName.OpenTasks.permissions + ProviderName.TasksOrg.permissions +
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
            for (permission in permissions)
                report.append(permission).append(" permission: ")
                        .append(if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) "granted" else "denied")
                        .append("\n")
            // system-wide sync settings
            report.append("System-wide synchronization: ")
                    .append(if (ContentResolver.getMasterSyncAutomatically()) "automatically" else "manually")
                    .append("\n")
            // main accounts
            val accountManager = AccountManager.get(context)
            for (acct in accountManager.getAccountsByType(context.getString(R.string.account_type)))
                try {
                    val settings = AccountSettings(context, acct)
                    report.append("Account: ").append(acct.name).append("\n" + "  Address book sync. interval: ").append(syncStatus(settings, App.addressBooksAuthority)).append("\n" + "  Calendar     sync. interval: ").append(syncStatus(settings, CalendarContract.AUTHORITY)).append("\n" + "  OpenTasks    sync. interval: ").append(syncStatus(settings, ProviderName.OpenTasks.authority)).append("\n" + "  Tasks.org    sync. interval: ").append(syncStatus(settings, ProviderName.TasksOrg.authority)).append("\n" + "  WiFi only: ").append(settings.syncWifiOnly)
                    if (settings.syncWifiOnlySSID != null)
                        report.append(", SSID: ").append(settings.syncWifiOnlySSID)
                    if (settings.isEteSync1Account)
                        report.append("\n  EteSync 1.0 account (not supported anymore)")
                    report.append("\n  [CardDAV] Contact group method: ").append(settings.groupMethod)
                            .append("\n           Manage calendar colors: ").append(settings.manageCalendarColors)
                            .append("\n")
                } catch (e: InvalidAccountException) {
                    report.append(acct).append(" is invalid (unsupported settings version) or does not exist\n")
                }

            // address book accounts
            for (acct in accountManager.getAccountsByType(App.addressBookAccountType))
                try {
                    val addressBook = LocalAddressBook(context, acct, null)
                    report.append("Address book account: ").append(acct.name).append("\n" + "  Main account: ").append(addressBook.mainAccount).append("\n" + "  URL: ").append(addressBook.url).append("\n" + "  Sync automatically: ").append(ContentResolver.getSyncAutomatically(acct, ContactsContract.AUTHORITY)).append("\n")
                } catch (e: ContactsStorageException) {
                    report.append(acct).append(" is invalid: ").append(e.message).append("\n")
                }

            report.append("\n")

            report.append("SQLITE DUMP\n")
            val dbHelper = ServiceDB.OpenHelper(context)
            dbHelper.dump(report)
            dbHelper.close()
            report.append("\n")

            try {
                report.append(
                        "SYSTEM INFORMATION\n" + "Android version: ").append(Build.VERSION.RELEASE).append(" (").append(Build.DISPLAY).append(")\n" + "Device: ").append(Build.MANUFACTURER.replaceFirstChar { it.titlecase() }).append(" ").append(Build.MODEL).append(" (").append(Build.DEVICE).append(")\n\n"
                )
            } catch (ex: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't get system details", ex)
            }

            report.append("--- END DEBUG INFO ---\n")
            return report.toString()
        }

        private fun syncStatus(settings: AccountSettings, authority: String): String {
            val interval = settings.getSyncInterval(authority)
            return if (interval != null)
                if (interval == AccountSettings.SYNC_INTERVAL_MANUALLY) "manually" else (interval / 60).toString() + " min"
            else
                "—"
        }
    }

    companion object {
        val KEY_CALLER = "caller"
        val KEY_THROWABLE = "throwable"
        val KEY_LOGS = "logs"
        val KEY_AUTHORITY = "authority"
        val KEY_PHASE = "phase"

        fun newIntent(context: Context?, caller: String): Intent {
            val intent = Intent(context, DebugInfoActivity::class.java)
            intent.putExtra(KEY_CALLER, caller)
            return intent
        }
    }

}
