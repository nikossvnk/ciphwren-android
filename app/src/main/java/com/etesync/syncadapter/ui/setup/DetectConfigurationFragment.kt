/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.setup

import android.app.Dialog
import android.content.Context
import android.widget.ProgressBar
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etesync.syncadapter.R
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.ui.setup.BaseConfigurationFinder.Configuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetectConfigurationFragment : DialogFragment() {
    private val model: DetectionViewModel by viewModels()

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

        Logger.log.fine("DetectConfigurationFragment: loading")

        model.findConfiguration(requireContext().applicationContext,
            BundleCompat.getParcelable(requireArguments(), ARG_LOGIN_CREDENTIALS, LoginCredentials::class.java)!!)
        model.result.observe(this) { onLoadFinished(it) }
    }


    private fun onLoadFinished(data: Configuration) {
        if (data.isFailed) {
            Logger.log.warning("Failed login configuration ${data.error?.localizedMessage}")
            // no service found: show error message
            parentFragmentManager.beginTransaction()
                    .add(NothingDetectedFragment.newInstance(data.error!!.localizedMessage ?: data.error.toString()), null)
                    .commitAllowingStateLoss()
        } else {
            Logger.log.info("Found Etebase account account")
            parentFragmentManager.beginTransaction()
                    .replace(android.R.id.content, CreateAccountFragment.newInstance(data))
                    .addToBackStack(null)
                    .commitAllowingStateLoss()
        }

        dismissAllowingStateLoss()
    }

    class NothingDetectedFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.setting_up_encryption)
                    .setIcon(R.drawable.ic_error_dark)
                    .setMessage(requireArguments().getString(KEY_LOGS))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        // dismiss
                    }
                    .create()
        }

        companion object {
            private val KEY_LOGS = "logs"

            fun newInstance(logs: String): NothingDetectedFragment {
                val args = Bundle()
                args.putString(KEY_LOGS, logs)
                val fragment = NothingDetectedFragment()
                fragment.arguments = args
                return fragment
            }
        }
    }

    companion object {
        private const val ARG_LOGIN_CREDENTIALS = "credentials"

        fun newInstance(credentials: LoginCredentials): DetectConfigurationFragment {
            val frag = DetectConfigurationFragment()
            val args = Bundle(1)
            args.putParcelable(ARG_LOGIN_CREDENTIALS, credentials)
            frag.arguments = args
            return frag
        }
    }
}

class DetectionViewModel : ViewModel() {
    val result = MutableLiveData<Configuration>()
    private var started = false

    fun findConfiguration(context: Context, credentials: LoginCredentials) {
        if (started) return
        started = true
        viewModelScope.launch {
            result.value = withContext(Dispatchers.IO) {
                BaseConfigurationFinder(context, credentials).findInitialConfiguration()
            }
        }
    }
}
