/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.ciphwren.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.ical4android.TaskProvider.Companion.TASK_PROVIDERS
import at.bitfire.ical4android.TaskProvider.ProviderName
import com.ciphwren.android.Constants
import com.ciphwren.android.R
import com.ciphwren.android.resource.LocalTaskList

class PermissionsActivity : BaseActivity() {
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    protected fun refresh() {
        val noCalendarPermissions = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
        findViewById<View>(R.id.calendar_permissions).visibility = if (noCalendarPermissions) View.VISIBLE else View.GONE

        val noContactsPermissions = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED
        findViewById<View>(R.id.contacts_permissions).visibility = if (noContactsPermissions) View.VISIBLE else View.GONE

        val needOpenTaskPermissions = setupPermissions(ProviderName.OpenTasks, R.id.opentasks_permissions)
        val needTasksOrgPermissions = setupPermissions(ProviderName.TasksOrg, R.id.tasksorg_permissions)

        if (!noCalendarPermissions && !noContactsPermissions && !(needOpenTaskPermissions || needTasksOrgPermissions)) {
            val nm = NotificationManagerCompat.from(this)
            nm.cancel(Constants.NOTIFICATION_PERMISSIONS)

            finish()
        }
    }

    private fun setupPermissions(provider: ProviderName, @IdRes id: Int): Boolean {
        val providerAvailable = LocalTaskList.tasksProviderAvailable(this, provider)
        val hasPermissions = providerAvailable && provider.permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        findViewById<View>(id).visibility = if (hasPermissions) View.GONE else View.VISIBLE
        return providerAvailable && !hasPermissions
    }

    fun requestCalendarPermissions(v: View) {
        requestPermissions.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }

    fun requestContactsPermissions(v: View) {
        requestPermissions.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
    }

    fun requestOpenTasksPermissions(v: View) {
        requestPermissions.launch(ProviderName.OpenTasks.permissions)
    }

    fun requestTasksOrgPermissions(v: View) {
        requestPermissions.launch(ProviderName.TasksOrg.permissions)
    }

    companion object {
        /** All runtime permissions Ciphwren uses */
        val allPermissions: Array<String>
            get() {
                val permissions = mutableListOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                TASK_PROVIDERS.flatMapTo(permissions) { it.permissions.toList() }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    permissions += Manifest.permission.POST_NOTIFICATIONS
                return permissions.toTypedArray()
            }
    }
}
