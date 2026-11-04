package com.etesync.syncadapter.ui.etebase

import android.app.Dialog
import android.widget.ProgressBar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.etebase.client.CollectionAccessLevel
import com.etebase.client.Utils
import com.etebase.client.exceptions.EtebaseException
import com.etebase.client.exceptions.NotFoundException
import com.etesync.syncadapter.CachedCollection
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.syncadapter.requestSync
import com.etesync.syncadapter.ui.BaseActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CollectionMembersFragment : Fragment() {
    private val model: AccountViewModel by activityViewModels()
    private val collectionModel: CollectionViewModel by activityViewModels()
    private var isAdmin: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ret = if (collectionModel.value!!.col.accessLevel == CollectionAccessLevel.Admin) {
            isAdmin = true
            inflater.inflate(R.layout.etebase_view_collection_members, container, false)
        } else {
            inflater.inflate(R.layout.etebase_view_collection_members_no_access, container, false)
        }


        return ret
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectionModel.observe(viewLifecycleOwner) {
            (activity as? BaseActivity)?.supportActionBar?.setTitle(R.string.collection_members_title)
            initUi(layoutInflater, view, it)
        }
    }


    private fun initUi(inflater: LayoutInflater, v: View, cachedCollection: CachedCollection) {
        val meta = cachedCollection.meta
        val collectionType = cachedCollection.collectionType
        val colorSquare = v.findViewById<View>(R.id.color)
        val color = LocalCalendar.parseColor(meta.color)
        when (collectionType) {
            Constants.ETEBASE_TYPE_CALENDAR -> {
                colorSquare.setBackgroundColor(color)
            }
            Constants.ETEBASE_TYPE_TASKS -> {
                colorSquare.setBackgroundColor(color)
            }
            Constants.ETEBASE_TYPE_ADDRESS_BOOK -> {
                colorSquare.visibility = View.GONE
            }
        }

        val title = v.findViewById<View>(R.id.display_name) as TextView
        title.text = meta.name

        val desc = v.findViewById<View>(R.id.description) as TextView
        desc.text = meta.description

        if (isAdmin) {
            v.findViewById<View>(R.id.add_member).setOnClickListener {
                addMemberClicked()
            }
        } else {
            v.findViewById<Button>(R.id.leave).setOnClickListener {
                val accountHolder = model.value ?: return@setOnClickListener
                val applicationContext = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val membersManager = accountHolder.colMgr.getMemberManager(cachedCollection.col)
                        membersManager.leave()
                        requestSync(applicationContext, accountHolder.account)
                    }
                    activity?.finish()
                }
            }
        }

        v.findViewById<View>(R.id.progressBar).visibility = View.GONE
    }

    private fun addMemberClicked() {
        val view = View.inflate(requireContext(), R.layout.add_member_fragment, null)
        val dialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.collection_members_add)
                .setIcon(R.drawable.ic_account_add_dark)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    val username = view.findViewById<EditText>(R.id.username).text.toString()
                    val readOnly = view.findViewById<CheckBox>(R.id.read_only).isChecked

                    val frag = AddMemberFragment.newInstance(username, if (readOnly) CollectionAccessLevel.ReadOnly else CollectionAccessLevel.ReadWrite)
                    frag.show(childFragmentManager, null)
                }
                .setNegativeButton(android.R.string.no) { _, _ -> }
        dialog.setView(view)
        dialog.show()
    }
}

class AddMemberFragment : DialogFragment() {
    private val accountModel: AccountViewModel by activityViewModels()
    private val collectionModel: CollectionViewModel by activityViewModels()
    private val model: AddMemberViewModel by viewModels()
    private var prompt: AlertDialog? = null
    private val username: String
        get() = requireArguments().getString("username")!!
    private val accessLevel: CollectionAccessLevel
        get() = CollectionAccessLevel.valueOf(requireArguments().getString("accessLevel")!!)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.collection_members_adding)
            .setMessage(R.string.please_wait)
            .setView(ProgressBar(requireContext()))
            .create().apply { setCanceledOnTouchOutside(false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountModel.observe(this) { startLoading() }
        collectionModel.observe(this) { startLoading() }
        model.state.observe(this) { render(it) }
    }

    private fun startLoading() {
        val holder = accountModel.value ?: return
        val collection = collectionModel.value ?: return
        model.load(holder, collection, username, accessLevel)
    }

    private fun render(state: AddMemberViewModel.State) {
        prompt?.dismiss()
        prompt = null
        when (state) {
            AddMemberViewModel.State.LOADING, AddMemberViewModel.State.INVITING -> Unit
            AddMemberViewModel.State.CONFIRM -> {
                val view = LayoutInflater.from(requireContext()).inflate(R.layout.fingerprint_alertdialog, null)
                view.findViewById<TextView>(R.id.body).text = getString(R.string.trust_fingerprint_body, username)
                view.findViewById<TextView>(R.id.fingerprint).text = Utils.prettyFingerprint(model.pubkey!!)
                prompt = AlertDialog.Builder(requireContext())
                    .setIcon(R.drawable.ic_fingerprint_dark)
                    .setTitle(R.string.trust_fingerprint_title)
                    .setView(view)
                    .setPositiveButton(android.R.string.ok) { _, _ -> model.invite() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> dismiss() }
                    .setOnCancelListener { dismiss() }
                    .show()
            }
            AddMemberViewModel.State.SUCCESS -> {
                prompt = AlertDialog.Builder(requireContext())
                    .setTitle(R.string.collection_members_add)
                    .setIcon(R.drawable.ic_account_add_dark)
                    .setMessage(R.string.collection_members_add_success)
                    .setPositiveButton(android.R.string.yes) { _, _ -> dismiss() }
                    .setOnCancelListener { dismiss() }
                    .show()
            }
            AddMemberViewModel.State.ERROR -> {
                val message = if (model.error is NotFoundException) {
                    getString(R.string.collection_members_error_user_not_found, username)
                } else {
                    model.error?.localizedMessage
                }
                prompt = AlertDialog.Builder(requireContext())
                    .setIcon(R.drawable.ic_error_dark)
                    .setTitle(R.string.collection_members_add_error)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.yes) { _, _ -> dismiss() }
                    .setOnCancelListener { dismiss() }
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        prompt?.dismiss()
        prompt = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(username: String, accessLevel: CollectionAccessLevel): AddMemberFragment =
            AddMemberFragment().apply {
                arguments = Bundle().apply {
                    putString("username", username)
                    putString("accessLevel", accessLevel.name)
                }
            }
    }
}

class AddMemberViewModel : ViewModel() {
    enum class State { LOADING, CONFIRM, INVITING, SUCCESS, ERROR }
    val state = MutableLiveData(State.LOADING)
    var pubkey: ByteArray? = null
        private set
    var error: EtebaseException? = null
        private set
    private var started = false
    private lateinit var holder: AccountHolder
    private lateinit var collection: CachedCollection
    private lateinit var username: String
    private lateinit var accessLevel: CollectionAccessLevel

    fun load(holder: AccountHolder, collection: CachedCollection, username: String, accessLevel: CollectionAccessLevel) {
        if (started) return
        started = true
        this.holder = holder
        this.collection = collection
        this.username = username
        this.accessLevel = accessLevel
        viewModelScope.launch {
            try {
                pubkey = withContext(Dispatchers.IO) {
                    holder.etebase.invitationManager.fetchUserProfile(username).pubkey
                }
                state.value = State.CONFIRM
            } catch (e: EtebaseException) {
                error = e
                state.value = State.ERROR
            }
        }
    }

    fun invite() {
        if (state.value != State.CONFIRM) return
        state.value = State.INVITING
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    holder.etebase.invitationManager.invite(collection.col, username, pubkey!!, accessLevel)
                }
                state.value = State.SUCCESS
            } catch (e: EtebaseException) {
                error = e
                state.value = State.ERROR
            }
        }
    }
}