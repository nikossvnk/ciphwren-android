/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.ciphwren.android.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.ListFragment
import androidx.lifecycle.LiveData
import com.ciphwren.android.App
import com.ciphwren.android.R

class AccountListFragment : ListFragment(), AdapterView.OnItemClickListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        listAdapter = AccountListAdapter(requireContext())

        return inflater.inflate(R.layout.account_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AccountsLiveData(requireContext()).observe(viewLifecycleOwner) { accounts ->
            val adapter = listAdapter as AccountListAdapter
            adapter.clear()
            adapter.addAll(accounts)
        }

        val list = listView
        list.onItemClickListener = this
        list.choiceMode = AbsListView.CHOICE_MODE_SINGLE
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val account = listAdapter?.getItem(position) as Account

        val intent = Intent(context, AccountActivity::class.java)
        intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
        startActivity(intent)
    }


    /** Main accounts, updated when accounts are added or removed */
    private class AccountsLiveData(context: Context) : LiveData<List<Account>>(), OnAccountsUpdateListener {
        private val accountManager = AccountManager.get(context)

        override fun onActive() =
                accountManager.addOnAccountsUpdatedListener(this, null, true)

        override fun onInactive() {
            try {
                accountManager.removeOnAccountsUpdatedListener(this)
            } catch (e: IllegalArgumentException) {
                // Do nothing. Just handle the case where for some reason the listener is not registered.
            }
        }

        override fun onAccountsUpdated(accounts: Array<Account>) {
            value = accounts.filter { it.type == App.accountType }
        }
    }


    // list adapter

    internal class AccountListAdapter(context: Context) : ArrayAdapter<Account>(context, R.layout.account_list_item) {

        override fun getView(position: Int, _v: View?, parent: ViewGroup): View {
            var v = _v
            if (v == null)
                v = LayoutInflater.from(context).inflate(R.layout.account_list_item, parent, false)

            val account = getItem(position)

            val tv = v!!.findViewById<View>(R.id.account_name) as TextView
            tv.text = account!!.name

            return v
        }
    }

}
