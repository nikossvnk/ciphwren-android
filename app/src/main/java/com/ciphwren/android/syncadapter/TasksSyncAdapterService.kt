/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.ciphwren.android.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Build
import android.os.Bundle
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.TaskProvider.ProviderName
import com.ciphwren.android.*
import com.ciphwren.android.log.Logger
import com.ciphwren.android.model.CollectionType
import com.ciphwren.android.resource.LocalTaskList
import org.dmfs.tasks.contract.TaskContract
import java.util.*

/**
 * Synchronization manager for CalDAV collections; handles tasks ({@code VTODO}).
 */
class TasksSyncAdapterService: SyncAdapterService() {

    override fun syncAdapter() = TasksSyncAdapter(this, ProviderName.OpenTasks)

	class TasksSyncAdapter(
            context: Context,
            private val name: ProviderName
    ): SyncAdapter(context) {
        override fun onPerformSyncDo(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {

            val taskProvider = TaskProvider.fromProviderClient(context, name, provider)

            // make sure account can be seen by OpenTasks
            if (Build.VERSION.SDK_INT >= 26)
                AccountManager.get(context).setAccountVisibility(account, taskProvider.name.packageName, AccountManager.VISIBILITY_VISIBLE)

            val accountSettings = AccountSettings(context, account)
            if (!checkAccountSupported(account, accountSettings))
                return
            /* don't run sync if
               - sync conditions (e.g. "sync only in WiFi") are not met AND
               - this is is an automatic sync (i.e. manual syncs are run regardless of sync conditions)
             */
            if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(accountSettings))
                return

            RefreshCollections(account, CollectionType.TASKS).run()

            updateLocalTaskLists(taskProvider, account, accountSettings)

            for (taskList in AndroidTaskList.find(account, taskProvider, LocalTaskList.Factory, "${TaskContract.TaskLists.SYNC_ENABLED}!=0", null)) {
                Logger.log.info("Synchronizing task list #${taskList.id} [${taskList.syncId}]")
                TasksSyncManager(context, account, accountSettings, extras, authority, syncResult, taskList).use {
                    it.performSync()
                }
            }

            Logger.log.info("Task sync complete")
        }

        private fun updateLocalTaskLists(provider: TaskProvider, account: Account, settings: AccountSettings) {
            val remote = HashMap<String, CachedCollection>()
            val etebaseLocalCache = EtebaseLocalCache.getInstance(context, account.name)
            val collections: List<CachedCollection>
            synchronized(etebaseLocalCache) {
                collections = HttpClient.Builder(context).setForeground(false).build().use { httpClient ->
                    val etebase = EtebaseLocalCache.getEtebase(context, httpClient.okHttpClient, settings)
                    val colMgr = etebase.collectionManager()

                    etebaseLocalCache.collectionList(colMgr).filter { it.collectionType == Constants.ETEBASE_TYPE_TASKS }
                }
            }

            for (collection in collections) {
                remote[collection.col.uid()] = collection
            }

            val local = AndroidTaskList.find(account, provider, LocalTaskList.Factory, null, null)

            val updateColors = settings.manageCalendarColors

            // delete obsolete local calendar
            for (taskList in local) {
                val url = taskList.syncId
                val collection = remote[url]
                if (collection == null) {
                    Logger.log.fine("Deleting obsolete local taskList $url")
                    taskList.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.fine("Updating local taskList $url")
                    taskList.update(collection, updateColors)
                    // we already have a local taskList for this remote collection, don't take into consideration anymore
                    remote.remove(url)
                }
            }

            // create new local calendars
            for (url in remote.keys) {
                val cachedCollection = remote[url]!!
                Logger.log.info("Adding local calendar list $cachedCollection")
                LocalTaskList.create(account, provider, cachedCollection)
            }
        }

    }

}
