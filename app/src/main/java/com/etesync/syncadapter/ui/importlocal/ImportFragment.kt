package com.etesync.syncadapter.ui.importlocal

import android.accounts.Account
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import at.bitfire.ical4android.*
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.CachedCollection
import com.etesync.syncadapter.Constants.*
import com.etesync.syncadapter.R
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionType
import com.etesync.syncadapter.resource.*
import com.etesync.syncadapter.syncadapter.ContactsSyncManager
import com.etesync.syncadapter.ui.Refreshable
import com.etesync.syncadapter.ui.importlocal.ResultFragment.ImportResult
import com.etesync.syncadapter.utils.TaskProviderHandling
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.launch
import java.io.InputStreamReader


class ImportFragment : DialogFragment() {
    private val model by lazy { ViewModelProvider(this)[ImportViewModel::class.java] }
    private var progressDialog: ImportProgressDialog? = null
    private var resultDeliveryEnqueued = false

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val args = requireArguments()
            model.start(FileImport(
                BundleCompat.getParcelable(args, KEY_ACCOUNT, Account::class.java)!!,
                args.getString(KEY_UID)!!,
                CollectionType.valueOf(args.getString(KEY_TYPE)!!),
                uri
            ))
        } else {
            dismissAllowingStateLoss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        if (savedInstanceState != null) {
            model.chooserLaunched = savedInstanceState.getBoolean(KEY_CHOOSER_LAUNCHED)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect { state ->
                    progressDialog?.render(state)
                    val result = state.result
                    if (result != null && !resultDeliveryEnqueued && !parentFragmentManager.isStateSaved) {
                        val refreshable = activity as? Refreshable
                        resultDeliveryEnqueued = true
                        // Deliver and remove the progress fragment in one non-state-loss transaction.
                        parentFragmentManager.beginTransaction()
                            .add(ResultFragment.newInstance(result), "importResult")
                            .remove(this@ImportFragment)
                            .runOnCommit {
                                model.resultDelivered()
                                refreshable?.refresh()
                            }
                            .commit()
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return ImportProgressDialog(requireContext()).also {
            progressDialog = it
            it.render(model.state.value)
        }.dialog
    }

    override fun onResume() {
        super.onResume()
        if (!model.chooserLaunched) {
            model.chooserLaunched = true
            chooseFile()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_CHOOSER_LAUNCHED, model.chooserLaunched)
    }

    override fun onDestroyView() {
        progressDialog = null
        super.onDestroyView()
    }

    private fun chooseFile() {
        val type = CollectionType.valueOf(requireArguments().getString(KEY_TYPE)!!)
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = if (type == CollectionType.ADDRESS_BOOK) "text/x-vcard" else "text/calendar"
        }
        try {
            fileChooser.launch(Intent.createChooser(intent, getString(R.string.choose_file)))
        } catch (e: ActivityNotFoundException) {
            model.complete(ImportResult().also {
                it.e = Exception("Failed to open file chooser.\nPlease install one.", e)
            })
        }
    }

    private class FileImport(
        private val account: Account,
        private val uid: String,
        private val enumType: CollectionType,
        private val uri: Uri
    ) : ImportRequest {
        override suspend fun load(context: Context, progress: ImportViewModel): ImportResult {
            val result = ImportResult()

            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException("Failed to open import file.")
                InputStreamReader(inputStream).use { importReader ->

                if (enumType == CollectionType.CALENDAR) {
                    val events = Event.eventsFromReader(importReader, null)
                    

                    if (events.isEmpty()) {
                        Logger.log.warning("Empty/invalid file.")
                        result.e = Exception("Empty/invalid file.")
                        return result
                    }

                    result.total = events.size.toLong()

                    progress.setTotal(events.size)

                    val provider = context.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)
                    if (provider == null) {
                        result.e = Exception("Failed to acquire calendar content provider.")
                        return result
                    }

                    val localCalendar: LocalCalendar?
                    try {
                        localCalendar = LocalCalendar.findByName(account, provider, LocalCalendar.Factory, uid)
                        if (localCalendar == null) {
                            result.e = FileNotFoundException("Failed to load local resource.")
                            return result
                        }
                    } catch (e: CalendarStorageException) {
                        Logger.log.info("Fail" + e.localizedMessage)
                        result.e = e
                        return result
                    } catch (e: FileNotFoundException) {
                        Logger.log.info("Fail" + e.localizedMessage)
                        result.e = e
                        return result
                    }

                    for (event in events) {
                        try {
                            var localEvent = localCalendar.findByUid(event.uid!!)
                            if (localEvent != null) {
                                localEvent.updateAsDirty(event)
                                result.updated++
                            } else {
                                localEvent = LocalEvent(localCalendar, event, event.uid, null)
                                localEvent.addAsDirty()
                                result.added++
                            }
                        } catch (e: CalendarStorageException) {
                            e.printStackTrace()
                        }

                        progress.entryProcessed()
                    }
                } else if (enumType == CollectionType.TASKS) {
                    val tasks = Task.tasksFromReader(importReader)
                    

                    if (tasks.isEmpty()) {
                        Logger.log.warning("Empty/invalid file.")
                        result.e = Exception("Empty/invalid file.")
                        return result
                    }

                    result.total = tasks.size.toLong()

                    progress.setTotal(tasks.size)

                    val provider = TaskProviderHandling.getWantedTaskSyncProvider(context)
                            .let {
                                if (it == null) {
                                    result.e = Exception("Failed to acquire tasks content provider.")
                                    null
                                } else {
                                    TaskProvider.acquire(context, it)
                                }
                            }

                    provider?.let {
                        val localTaskList: LocalTaskList?
                        try {
                            localTaskList = LocalTaskList.findByName(account, it, LocalTaskList.Factory, uid)
                            if (localTaskList == null) {
                                result.e = FileNotFoundException("Failed to load local resource.")
                                return result
                            }
                        } catch (e: FileNotFoundException) {
                            Logger.log.info("Fail" + e.localizedMessage)
                            result.e = e
                            return result
                        }

                        for (task in tasks) {
                            try {
                                var localTask = localTaskList.findByUid(task.uid!!)
                                if (localTask != null) {
                                    localTask.updateAsDirty(task)
                                    result.updated++
                                } else {
                                    localTask = LocalTask(localTaskList, task, task.uid, null)
                                    localTask.addAsDirty()
                                    result.added++
                                }
                            } catch (e: CalendarStorageException) {
                                e.printStackTrace()
                            }

                            progress.entryProcessed()
                        }
                    }
                } else if (enumType == CollectionType.ADDRESS_BOOK) {
                    val uidToLocalId = HashMap<String?, Long>()
                    val downloader = ContactsSyncManager.ResourceDownloader(context)
                    val contacts = Contact.fromReader(importReader, downloader)

                    if (contacts.isEmpty()) {
                        Logger.log.warning("Empty/invalid file.")
                        result.e = Exception("Empty/invalid file.")
                        return result
                    }

                    result.total = contacts.size.toLong()

                    progress.setTotal(contacts.size)

                    val provider = context.contentResolver.acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI)
                    if (provider == null) {
                        result.e = Exception("Failed to acquire contacts content provider.")
                        return result
                    }

                    val localAddressBook = LocalAddressBook.findByUid(context, provider, account, uid)
                    if (localAddressBook == null) {
                        result.e = FileNotFoundException("Failed to load local address book.")
                        return result
                    }

                    for (contact in contacts.filter { contact -> !contact.group }) {
                        try {
                            var localContact = localAddressBook.findByUid(contact.uid!!) as LocalContact?

                            if (localContact != null) {
                                localContact.updateAsDirty(contact)
                                result.updated++
                            } else {
                                localContact = LocalContact(localAddressBook, contact, contact.uid, null)
                                localContact.createAsDirty()
                                result.added++
                            }

                            uidToLocalId[contact.uid] = localContact.id!!

                            // Apply categories
                            val batch = BatchOperation(localAddressBook.provider!!)
                            for (category in contact.categories) {
                                localContact.addToGroup(batch, localAddressBook.findOrCreateGroup(category))
                            }
                            batch.commit()
                        } catch (e: ContactsStorageException) {
                            e.printStackTrace()
                        }

                        progress.entryProcessed()
                    }

                    for (contact in contacts.filter { contact -> contact.group }) {
                        try {
                            val memberIds = contact.members.mapNotNull { memberUid ->
                                uidToLocalId[memberUid]
                            }

                            val group = contact
                            var localGroup: LocalGroup? = localAddressBook.findByUid(group.uid!!) as LocalGroup?

                            if (localGroup != null) {
                                localGroup.updateAsDirty(group, memberIds)
                                result.updated++
                            } else {
                                localGroup = LocalGroup(localAddressBook, group, group.uid, null)
                                localGroup.createAsDirty(memberIds)
                                result.added++
                            }
                        } catch (e: ContactsStorageException) {
                            e.printStackTrace()
                        }

                        progress.entryProcessed()
                    }

                    provider.release()
                }
                }

                return result
            } catch (e: FileNotFoundException) {
                result.e = e
                return result
            } catch (e: InvalidCalendarException) {
                result.e = e
                return result
            } catch (e: IOException) {
                result.e = e
                return result
            } catch (e: ContactsStorageException) {
                result.e = e
                return result
            }

        }
    }

    companion object {
        private const val KEY_ACCOUNT = "account"
        private const val KEY_UID = "uid"
        private const val KEY_TYPE = "type"
        private const val KEY_CHOOSER_LAUNCHED = "chooserLaunched"

        fun newInstance(account: Account, cachedCollection: CachedCollection): ImportFragment {
            val enumType = when (cachedCollection.collectionType) {
                ETEBASE_TYPE_CALENDAR -> CollectionType.CALENDAR
                ETEBASE_TYPE_TASKS -> CollectionType.TASKS
                ETEBASE_TYPE_ADDRESS_BOOK -> CollectionType.ADDRESS_BOOK
                else -> throw Exception("Got unsupported collection type")
            }
            val ret = ImportFragment()
            ret.arguments = Bundle().apply {
                putParcelable(KEY_ACCOUNT, account)
                putString(KEY_UID, cachedCollection.col.uid)
                putString(KEY_TYPE, enumType.name)
            }
            return ret
        }
    }
}
