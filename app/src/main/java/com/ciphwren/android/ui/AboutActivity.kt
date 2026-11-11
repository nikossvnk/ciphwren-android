/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.ciphwren.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Spanned
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.ciphwren.android.App
import com.ciphwren.android.BuildConfig
import com.ciphwren.android.Constants
import com.ciphwren.android.R
import com.ciphwren.android.log.Logger
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import ezvcard.Ezvcard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.logging.Level

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        setSupportActionBar(findViewById<View>(R.id.toolbar) as Toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val viewPager = findViewById<ViewPager2>(R.id.viewpager)
        viewPager.adapter = TabsAdapter(this)

        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = components[position].title
        }.attach()
    }

    private class ComponentInfo internal constructor(internal val title: String, internal val version: String?, internal val website: String, internal val copyright: String, internal val licenseInfo: Int, internal val licenseTextFile: String)


    private class TabsAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int {
            return components.size
        }

        override fun createFragment(position: Int): Fragment {
            return ComponentFragment.instantiate(position)
        }
    }

    class ComponentFragment : Fragment() {

        @SuppressLint("SetTextI18n")
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val info = components[requireArguments().getInt(KEY_POSITION)]

            val v = inflater.inflate(R.layout.about_component, container, false)

            var tv = v.findViewById<View>(R.id.title) as TextView
            tv.text = info.title + if (info.version != null) " " + info.version else ""

            tv = v.findViewById<View>(R.id.website) as TextView
            tv.autoLinkMask = Linkify.WEB_URLS
            tv.text = info.website

            tv = v.findViewById<View>(R.id.copyright) as TextView
            tv.text = "© " + info.copyright

            tv = v.findViewById<View>(R.id.license_info) as TextView
            tv.setText(info.licenseInfo)

            // load and format license text
            val context = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val license = withContext(Dispatchers.IO) {
                    loadLicense(context, info.licenseTextFile)
                }
                val tv = view?.findViewById<TextView>(R.id.license_text)
                if (tv != null) {
                    tv.autoLinkMask = Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS
                    tv.text = license
                }
            }

            return v
        }

        private fun loadLicense(context: Context, fileName: String): Spanned? {
            Logger.log.fine("Loading license file $fileName")
            return try {
                val raw = context.resources.assets.open(fileName).use { it.readBytes() }
                HtmlCompat.fromHtml(String(raw), HtmlCompat.FROM_HTML_MODE_LEGACY)
            } catch (e: IOException) {
                Logger.log.log(Level.SEVERE, "Couldn't read license file", e)
                null
            }
        }

        companion object {
            private const val KEY_POSITION = "position"

            fun instantiate(position: Int): ComponentFragment {
                val frag = ComponentFragment()
                val args = Bundle(1)
                args.putInt(KEY_POSITION, position)
                frag.arguments = args
                return frag
            }
        }
    }

    companion object {

        private val components = arrayOf(ComponentInfo(
                App.appName, BuildConfig.VERSION_NAME, Constants.sourceCodeUri.toString(),
                "Tom Hacohen",
                R.string.about_license_info_no_warranty, "gpl-3.0-standalone.html"
        ), ComponentInfo(
                "DAVdroid", "(forked from)", "https://syncadapter.bitfire.at",
                "Ricki Hirner, Bernhard Stockmann (bitfire web engineering)",
                R.string.about_license_info_no_warranty, "gpl-3.0-standalone.html"
        ), ComponentInfo(
                "Apache Commons", null, "http://commons.apache.org/",
                "Apache Software Foundation", R.string.about_license_info_no_warranty, "apache2.html"
        ), ComponentInfo(
                "ez-vcard", Ezvcard.VERSION, "https://github.com/mangstadt/ez-vcard",
                "Michael Angstadt", R.string.about_license_info_no_warranty, "bsd.html"
        ), ComponentInfo(
                "ical4j", "4.x", "https://ical4j.github.io/",
                "Ben Fortuna", R.string.about_license_info_no_warranty, "bsd-3clause.html"
        ), ComponentInfo(
                "OkHttp", null, "https://square.github.io/okhttp/",
                "Square, Inc.", R.string.about_license_info_no_warranty, "apache2.html"
        ))
    }

}
