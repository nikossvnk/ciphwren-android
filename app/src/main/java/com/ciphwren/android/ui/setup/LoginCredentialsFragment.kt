/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.ciphwren.android.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import com.ciphwren.android.BuildConfig
import com.ciphwren.android.R
import com.ciphwren.android.ui.etebase.SignupFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

class LoginCredentialsFragment : Fragment() {
    internal lateinit var editUserName: EditText
    internal lateinit var editUrlPassword: TextInputLayout

    internal lateinit var server: EditText

    internal var initialUsername: String? = null
    internal var initialPassword: String? = null
    internal var initialServer: String? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.login_credentials_fragment, container, false)

        editUserName = v.findViewById<TextInputEditText>(R.id.user_name)
        editUrlPassword = v.findViewById<TextInputLayout>(R.id.url_password)
        server = v.findViewById<TextInputEditText>(R.id.server)

        if (savedInstanceState == null) {
            editUserName.setText(initialUsername ?: "")
            editUrlPassword.editText?.setText(initialPassword ?: "")
            server.setText(initialServer ?: BuildConfig.DEBUG_REMOTE_URL ?: "")
        }

        val createAccount = v.findViewById<View>(R.id.create_account) as Button
        createAccount.setOnClickListener {
            parentFragmentManager.commit {
                replace(android.R.id.content, SignupFragment.newInstance(editUserName.text.toString(), editUrlPassword.editText?.text.toString(), server.text.toString()))
            }
        }

        val login = v.findViewById<View>(R.id.login) as Button
        login.setOnClickListener {
            val credentials = validateLoginData()
            if (credentials != null)
                DetectConfigurationFragment.newInstance(credentials).show(parentFragmentManager, null)
        }

        return v
    }

    protected fun validateLoginData(): LoginCredentials? {
        var valid = true

        val userName = editUserName.text.toString()
        if (userName.isEmpty()) {
            editUserName.error = getString(R.string.login_email_address_error)
            valid = false
        } else {
            editUserName.error = null
        }

        val password = editUrlPassword.editText?.text.toString()
        if (password.isEmpty()) {
            editUrlPassword.error = getString(R.string.login_password_required)
            valid = false
        } else {
            editUrlPassword.error = null
        }

        val uri = parseServerUrl(server)

        return if (valid && uri != null) LoginCredentials(uri, userName, password) else null
    }

    companion object {
        fun newInstance(initialUsername: String?, initialPassword: String?, initialServer: String?): LoginCredentialsFragment {
            val ret = LoginCredentialsFragment()
            ret.initialUsername = initialUsername
            ret.initialPassword = initialPassword
            ret.initialServer = initialServer
            return ret
        }
    }
}

/**
 * Returns the Etebase server URL entered in [server]. The URL is mandatory: there is no default
 * server. Sets the field error and returns null when it is empty or invalid.
 */
internal fun parseServerUrl(server: EditText): URI? {
    val text = server.text.toString().trim()
    val url = text.toHttpUrlOrNull()
    server.error = when {
        text.isEmpty() -> server.context.getString(R.string.login_server_required)
        url == null -> server.context.getString(R.string.login_custom_server_error)
        else -> null
    }
    return url?.toUri()
}
