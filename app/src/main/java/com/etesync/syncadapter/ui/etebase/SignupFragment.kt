/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.etebase

import android.app.Dialog
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckedTextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etebase.client.Account
import com.etebase.client.EtebaseException
import com.etebase.client.InternalException
import com.etebase.client.User
import com.etebase.client.createClient
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.HttpClient
import com.etesync.syncadapter.R
import com.etesync.syncadapter.ui.setup.*
import com.etesync.syncadapter.utils.setExpanded
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

class SignupFragment : Fragment() {
    internal var initialUsername: String? = null
    internal var initialPassword: String? = null
    internal lateinit var editUserName: TextInputLayout
    internal lateinit var editEmail: TextInputLayout
    internal lateinit var editPassword: TextInputLayout

    internal lateinit var showAdvanced: CheckedTextView
    internal lateinit var customServer: TextInputEditText


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.signup_fragment, container, false)

        editUserName = v.findViewById(R.id.user_name)
        editEmail = v.findViewById(R.id.email)
        editPassword = v.findViewById(R.id.url_password)
        showAdvanced = v.findViewById(R.id.show_advanced)
        customServer = v.findViewById(R.id.custom_server)

        if (savedInstanceState == null) {
            editUserName.editText?.setText(initialUsername ?: "")
            editPassword.editText?.setText(initialPassword ?: "")
        }

        val login = v.findViewById<Button>(R.id.login)
        login.setOnClickListener {
            parentFragmentManager.commit {
                replace(android.R.id.content, LoginCredentialsFragment.newInstance(editUserName.editText?.text.toString(), editPassword.editText?.text.toString()))
            }
        }

        val createAccount = v.findViewById<Button>(R.id.create_account)
        createAccount.setOnClickListener {
            val credentials = validateData()
            if (credentials != null) {
                SignupDoFragment.newInstance(credentials).show(parentFragmentManager, null)
            }
        }

        val advancedLayout = v.findViewById<View>(R.id.advanced_layout)

        showAdvanced.setOnClickListener {
            showAdvanced.isChecked = !showAdvanced.isChecked
            advancedLayout.setExpanded(showAdvanced.isChecked)
        }

        return v
    }

    protected fun validateData(): SignupCredentials? {
        var valid = true

        val userName = editUserName.editText?.text.toString()
        // FIXME: this validation should only be done in the server, we are doing it here until the Java library supports field errors
        if ((userName.length < 6) || (!userName.matches(Regex("""^[\w.-]+$""")))) {
            editUserName.error = getString(R.string.login_username_error)
            valid = false
        } else {
            editUserName.error = null
        }

        val email = editEmail.editText?.text.toString()
        if (email.isEmpty()) {
            editEmail.error = getString(R.string.login_email_address_error)
            valid = false
        } else {
            editEmail.error = null
        }

        val password = editPassword.editText?.text.toString()
        if (password.length < 8) {
            editPassword.error = getString(R.string.signup_password_restrictions)
            valid = false
        } else {
            editPassword.error = null
        }

        var uri: URI? = null
        if (showAdvanced.isChecked) {
            val server = customServer.text.toString()
            // If this field is null, just use the default
            if (!server.isEmpty()) {
                val url = server.toHttpUrlOrNull()
                if (url != null) {
                    uri = url.toUri()
                    customServer.error = null
                } else {
                    customServer.error = getString(R.string.login_custom_server_error)
                    valid = false
                }
            }
        }

        return if (valid) SignupCredentials(uri, userName, email, password) else null
    }

    companion object {
        fun newInstance(initialUsername: String?, initialPassword: String?): SignupFragment {
            val ret = SignupFragment()
            ret.initialUsername = initialUsername
            ret.initialPassword = initialPassword
            return ret
        }
    }
}



class SignupDoFragment: DialogFragment() {
    private val model: ConfigurationViewModel by viewModels()

    private val signupCredentials: SignupCredentials
        get() = BundleCompat.getSerializable(requireArguments(), "credentials", SignupCredentials::class.java)!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_up_encryption)
            .setMessage(R.string.setting_up_encryption_content)
            .setView(ProgressBar(requireContext()))
            .create()
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.signup(requireContext().applicationContext, signupCredentials)
        model.observe(this) {
                if (it.isFailed) {
                    // no service found: show error message
                    parentFragmentManager.beginTransaction()
                            .add(DetectConfigurationFragment.NothingDetectedFragment.newInstance(it.error!!.localizedMessage ?: it.error.toString()), null)
                            .commitAllowingStateLoss()
                } else {
                    parentFragmentManager.beginTransaction()
                            .replace(android.R.id.content, CreateAccountFragment.newInstance(it))
                            .addToBackStack(null)
                            .commitAllowingStateLoss()
                }
                dismissAllowingStateLoss()
            }
    }

    companion object {
        fun newInstance(signupCredentials: SignupCredentials): SignupDoFragment {
            val ret = SignupDoFragment()
            ret.arguments = Bundle().apply { putSerializable("credentials", signupCredentials) }
            return ret
        }
    }
}

class ConfigurationViewModel : ViewModel() {
    val account = MutableLiveData<BaseConfigurationFinder.Configuration>()
    private var asyncTask: Job? = null

    fun signup(context: Context, credentials: SignupCredentials) {
        if (asyncTask != null) return
        asyncTask = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val httpClient = HttpClient.Builder(context).build().okHttpClient
                val uri = credentials.uri ?: URI(Constants.etebaseServiceUrl)
                var etebaseSession: String? = null
                var exception: Throwable? = null
                try {
                    val client = createClient(httpClient, uri.toString())
                    val user = User(credentials.userName, credentials.email)
                    val etebase = Account.signup(client, user, credentials.password)
                    etebaseSession = etebase.save(null)
                } catch (e: EtebaseException) {
                    exception = e
                } catch (e: InternalException) {
                    // Shown like any other signup failure, as login does
                    exception = e
                }

                BaseConfigurationFinder.Configuration(
                        uri,
                        credentials.userName,
                        etebaseSession,
                        exception
                )
            }
            account.value = result
        }
    }

    fun cancelLoad() {
        asyncTask?.cancel()
    }

    fun observe(owner: LifecycleOwner, observer: (BaseConfigurationFinder.Configuration) -> Unit) =
            account.observe(owner) { observer(it) }
}

data class SignupCredentials(val uri: URI?, val userName: String, val email: String, val password: String) : java.io.Serializable