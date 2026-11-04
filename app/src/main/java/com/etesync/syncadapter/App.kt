/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Application
import android.content.*
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.StrictMode
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.TaskProvider.Companion.TASK_PROVIDERS
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.*
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.ui.AccountsActivity
import com.etesync.syncadapter.utils.HintManager
import com.etesync.syncadapter.utils.LanguageUtils
import com.etesync.syncadapter.utils.installerPackageName
import com.etesync.syncadapter.utils.NotificationUtils
import com.etesync.syncadapter.utils.TaskProviderHandling
import org.acra.ACRA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @SuppressLint("HardwareIds")
    override fun onCreate() {
        super.onCreate()
        reinitLogger()
        StrictMode.enableDefaults()
        initPrefVersion()

        NotificationUtils.createChannels(this)

        appName = getString(R.string.app_name)
        accountType = getString(R.string.account_type)
        addressBookAccountType = getString(R.string.account_type_address_book)
        addressBooksAuthority = getString(R.string.address_books_authority)

        LanguageUtils.migrateForcedLanguage(this)

        // don't block UI for some background checks
        applicationScope.launch(Dispatchers.IO) {
            // watch installed/removed apps
            val tasksFilter = IntentFilter()
            tasksFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
            tasksFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            tasksFilter.addDataScheme("package")
            ContextCompat.registerReceiver(this@App, PackageChangedReceiver(), tasksFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

            TASK_PROVIDERS.forEach {
                // check whether a tasks app is currently installed
                TaskProviderHandling.updateTaskSync(this@App, it)
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        if (!BuildConfig.DEBUG) {
            // The following line triggers the initialization of ACRA
            ACRA.init(this, AcraConfiguration.getConfig(this))
            ACRA.errorReporter.putCustomData("installedFrom", installerPackageName(base) ?: "")
        }
    }

    fun reinitLogger() {
        Logger.initialize(this)
    }

    /** Init the preferences version of the app.
     * This is used to initialise the first version if not already set.  */
    private fun initPrefVersion() {
        val prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
        if (prefs.getInt(PREF_VERSION, 0) == 0) {
            prefs.edit().putInt(PREF_VERSION, BuildConfig.VERSION_CODE).apply()
        }
    }

    private fun update(fromVersion: Int) {
        Logger.log.info("Updating from version " + fromVersion + " to " + BuildConfig.VERSION_CODE)

        if (fromVersion < 7) {
            /* Fix all of the etags to be non-null */
            val am = AccountManager.get(this)
            for (account in am.getAccountsByType(App.accountType)) {
                try {
                    // Generate account settings to make sure account is migrated.
                    AccountSettings(this, account)

                    val calendars = AndroidCalendar.find(account, this.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)!!,
                            LocalCalendar.Factory, null, null)
                    for (calendar in calendars) {
                        calendar.fixEtags()
                    }
                } catch (e: CalendarStorageException) {
                    e.printStackTrace()
                } catch (e: InvalidAccountException) {
                    e.printStackTrace()
                }

            }

            for (account in am.getAccountsByType(App.addressBookAccountType)) {
                val addressBook = LocalAddressBook(this, account, this.contentResolver.acquireContentProviderClient(ContactsContract.Contacts.CONTENT_URI))
                try {
                    addressBook.fixEtags()
                } catch (e: ContactsStorageException) {
                    e.printStackTrace()
                }

            }
        }

        if (fromVersion < 10) {
            HintManager.setHintSeen(this, AccountsActivity.HINT_ACCOUNT_ADD, true)
        }

    }

    class AppUpdatedReceiver : BroadcastReceiver() {

        @SuppressLint("UnsafeProtectedBroadcastReceiver,MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            Logger.log.info("EteSync was updated, checking for app version")

            val app = context.applicationContext as App
            val prefs = app.getSharedPreferences("app", Context.MODE_PRIVATE)
            val fromVersion = prefs.getInt(PREF_VERSION, 1)
            app.update(fromVersion)
            prefs.edit().putInt(PREF_VERSION, BuildConfig.VERSION_CODE).apply()
        }

    }


    companion object {
        val DISTRUST_SYSTEM_CERTIFICATES = "distrustSystemCerts"
        val LOG_TO_EXTERNAL_STORAGE = "logToExternalStorage"
        val OVERRIDE_PROXY = "overrideProxy"
        val OVERRIDE_PROXY_HOST = "overrideProxyHost"
        val OVERRIDE_PROXY_PORT = "overrideProxyPort"
        val PREFER_TASKSORG = "preferTasksOrg"
        val FORCE_LANGUAGE = "forceLanguage"
        val CHANGE_NOTIFICATION = "show_change_notification"

        val OVERRIDE_PROXY_HOST_DEFAULT = "localhost"
        val OVERRIDE_PROXY_PORT_DEFAULT = 8118

        val DEFAULT_LANGUAGE = "default"

        var appName: String = "EteSync"

        lateinit var accountType: String
            private set
        lateinit var addressBookAccountType: String
            private set
        lateinit var addressBooksAuthority: String
            private set

        fun getLauncherBitmap(context: Context): Bitmap? {
            var bitmapLogo: Bitmap? = null
            val drawableLogo = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)

            if (drawableLogo is BitmapDrawable)
                bitmapLogo = drawableLogo.bitmap
            return bitmapLogo
        }

        // update from previous account settings

        private val PREF_VERSION = "version"

    }
}
