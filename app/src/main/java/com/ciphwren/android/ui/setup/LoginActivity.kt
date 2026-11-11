/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.ciphwren.android.ui.setup

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem

import com.ciphwren.android.Constants
import com.ciphwren.android.R
import com.ciphwren.android.ui.BaseActivity

/**
 * Activity to initially connect to a server and create an account.
 * Fields for server/user data can be pre-filled with extras in the Intent.
 */
class LoginActivity : BaseActivity() {


    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        padContentForSystemBars()

        if (savedInstanceState == null)
        // first call, add fragment
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, LoginCredentialsFragment())
                    .commit()

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_login, menu)
        return true
    }

    fun showHelp(item: MenuItem) {
        startActivity(Intent(Intent.ACTION_VIEW, Constants.sourceCodeUri))
    }
}
