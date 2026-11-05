/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.accounts.Account
import android.widget.ProgressBar
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.etebase.client.createClient
import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.HttpClient
import com.etesync.syncadapter.R
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.syncadapter.requestSync
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class ChangeEncryptionPasswordActivity : BaseActivity() {

    protected lateinit var account: Account
    private val model: PasswordChangeViewModel by viewModels()
    private var progress: AlertDialog? = null
    private var resultDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = IntentCompat.getParcelableExtra(intent, EXTRA_ACCOUNT, Account::class.java)!!

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        setContentView(R.layout.change_encryption_password)
        model.state.observe(this) { state ->
            progress?.dismiss()
            progress = null
            resultDialog?.dismiss()
            resultDialog = null
            when (state) {
                PasswordChangeViewModel.State.RUNNING -> {
                    progress = AlertDialog.Builder(this)
                        .setTitle(R.string.setting_up_encryption)
                        .setMessage(R.string.setting_up_encryption_content)
                        .setView(ProgressBar(this))
                        .setCancelable(false)
                        .create().also {
                            it.setCanceledOnTouchOutside(false)
                            it.show()
                        }
                }
                PasswordChangeViewModel.State.SUCCESS -> {
                    resultDialog = AlertDialog.Builder(this)
                        .setTitle(R.string.change_encryption_password_success_title)
                        .setMessage(R.string.change_encryption_password_success_body)
                        .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                        .show()
                }
                PasswordChangeViewModel.State.ERROR -> changePasswordError(model.error!!)
                PasswordChangeViewModel.State.IDLE -> Unit
            }
        }
    }

    fun onCancelClicked(v: View) {
        finish()
    }

    fun changePasswordError(e: Exception) {
        progress?.dismiss()
        progress = null
        resultDialog = AlertDialog.Builder(this)
                .setTitle(R.string.wrong_encryption_password)
                .setIcon(R.drawable.ic_error_dark)
                .setMessage(e.localizedMessage)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    model.state.value = PasswordChangeViewModel.State.IDLE
                }.show()
    }

    fun changePasswordDo(old_password: String, new_password: String) {
        model.changePassword(applicationContext, account, old_password, new_password)
    }

    fun changePasswordClicked(v: View) {
        val old_password_view = findViewById<TextInputLayout>(R.id.encryption_password)
        val new_password_view = findViewById<TextInputLayout>(R.id.new_encryption_password)

        var valid = true
        val old_password = old_password_view.editText?.text.toString()
        if (old_password.isEmpty()) {
            old_password_view.error = getString(R.string.login_password_required)
            valid = false
        } else {
            old_password_view.error = null
        }
        val new_password = new_password_view.editText?.text.toString()
        if (new_password.isEmpty()) {
            new_password_view.error = getString(R.string.login_password_required)
            valid = false
        } else {
            new_password_view.error = null
        }

        if (!valid) {
            return
        }

        AlertDialog.Builder(this)
                .setTitle(R.string.delete_collection_confirm_title)
                .setMessage(R.string.change_encryption_password_are_you_sure)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    changePasswordDo(old_password, new_password)
                    // Progress follows the retained operation state.
                }
                .setNegativeButton(android.R.string.no) { _, _ -> }
                .create().show()
    }

    override fun onDestroy() {
        progress?.dismiss()
        resultDialog?.dismiss()
        progress = null
        resultDialog = null
        super.onDestroy()
    }

    companion object {
        internal val EXTRA_ACCOUNT = "account"

        fun newIntent(context: Context, account: Account): Intent {
            val intent = Intent(context, ChangeEncryptionPasswordActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, account)
            return intent
        }
    }
}

class PasswordChangeViewModel : ViewModel() {
    enum class State { IDLE, RUNNING, SUCCESS, ERROR }
    val state = MutableLiveData(State.IDLE)
    var error: Exception? = null
        private set

    fun changePassword(context: Context, account: Account, oldPassword: String, newPassword: String) {
        if (state.value == State.RUNNING) return
        state.value = State.RUNNING
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val settings = AccountSettings(context, account)
                    val httpClient = HttpClient.Builder(context).setForeground(true).build().okHttpClient
                    Logger.log.info("Logging in with old password")
                    val client = createClient(httpClient, settings.uri.toString())
                    val etebase = com.etebase.client.Account.login(client, account.name, oldPassword)
                    Logger.log.info("Login successful")
                    etebase.changePassword(newPassword)
                    settings.etebaseSession = etebase.save(null)
                    requestSync(context, account)
                }
                state.value = State.SUCCESS
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
                state.value = State.ERROR
            }
        }
    }
}
