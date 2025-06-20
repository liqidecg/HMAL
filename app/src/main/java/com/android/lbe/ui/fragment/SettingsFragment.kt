package com.android.lbe.ui.fragment

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lbe.security.R
import com.lbe.security.databinding.FragmentSettingsBinding
import com.android.lbe.common.CommonUtils
import com.android.lbe.common.Constants
import com.android.lbe.sysApp
import com.android.lbe.service.ConfigManager
import com.android.lbe.service.PrefManager
import com.android.lbe.service.ServiceClient
import com.android.lbe.ui.util.makeToast
import com.android.lbe.ui.util.setupToolbar
import com.android.lbe.util.LangList
import com.android.lbe.util.SuUtils
import rikka.material.app.LocaleDelegate
import rikka.preference.SimpleMenuPreference
import java.util.*

class SettingsFragment : Fragment(R.layout.fragment_settings), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private val binding by viewBinding<FragmentSettingsBinding>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar(binding.toolbar, getString(R.string.title_settings))

        if (childFragmentManager.findFragmentById(R.id.settings_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsPreferenceFragment())
                .commit()
        }
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val fragment = childFragmentManager.fragmentFactory.instantiate(requireContext().classLoader, pref.fragment!!)
        fragment.arguments = pref.extras
        childFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
        return true
    }

    class SettingsPreferenceDataStore : PreferenceDataStore() {
        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return when (key) {
                "followSystemAccent" -> PrefManager.followSystemAccent
                "blackDarkTheme" -> PrefManager.blackDarkTheme
                "hideIcon" -> PrefManager.hideIcon
                "appDataIsolation" -> CommonUtils.isAppDataIsolationEnabled
                "voldAppDataIsolation" -> CommonUtils.isVoldAppDataIsolationEnabled
                "forceMountData" -> ConfigManager.forceMountData
                else -> throw IllegalArgumentException("Invalid key: $key")
            }
        }

        override fun getString(key: String, defValue: String?): String {
            return when (key) {
                "language" -> PrefManager.locale
                "themeColor" -> PrefManager.themeColor
                "darkTheme" -> PrefManager.darkTheme.toString()
                else -> throw IllegalArgumentException("Invalid key: $key")
            }
        }

        override fun putBoolean(key: String, value: Boolean) {
            when (key) {
                "followSystemAccent" -> PrefManager.followSystemAccent = value
                "blackDarkTheme" -> PrefManager.blackDarkTheme = value
                "forceMountData" -> ConfigManager.forceMountData = value
                "hideIcon" -> PrefManager.hideIcon = value
                "appDataIsolation" -> Unit
                "voldAppDataIsolation" -> Unit
                else -> throw IllegalArgumentException("Invalid key: $key")
            }
        }

        override fun putString(key: String, value: String?) {
            when (key) {
                "language" -> PrefManager.locale = value!!
                "themeColor" -> PrefManager.themeColor = value!!
                "darkTheme" -> PrefManager.darkTheme = value!!.toInt()
                else -> throw IllegalArgumentException("Invalid key: $key")
            }
        }
    }

    class DataIsolationPreferenceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = SettingsPreferenceDataStore()
            setPreferencesFromResource(R.xml.settings_data_isolation, rootKey)

            findPreference<SwitchPreference>("appDataIsolation")?.let {
                it.setOnPreferenceChangeListener { _, newValue ->
                    handleIsolationChange(
                        preference = it,
                        enabled = newValue as Boolean,
                        property = Constants.ANDROID_APP_DATA_ISOLATION_ENABLED_PROPERTY,
                        checker = CommonUtils::isAppDataIsolationEnabled
                    )
                    false
                }
            }

            findPreference<SwitchPreference>("voldAppDataIsolation")?.let {
                it.setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    if (enabled) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.settings_warning)
                            .setMessage(R.string.settings_vold_warning)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                handleIsolationChange(
                                    preference = it,
                                    enabled = true,
                                    property = Constants.ANDROID_VOLD_APP_DATA_ISOLATION_ENABLED_PROPERTY,
                                    checker = CommonUtils::isVoldAppDataIsolationEnabled
                                )
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ ->
                                it.isChecked = false
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        handleIsolationChange(
                            preference = it,
                            enabled = false,
                            property = Constants.ANDROID_VOLD_APP_DATA_ISOLATION_ENABLED_PROPERTY,
                            checker = CommonUtils::isVoldAppDataIsolationEnabled
                        )
                    }
                    false
                }
            }
        }

        private fun handleIsolationChange(preference: SwitchPreference, enabled: Boolean, property: String, checker: () -> Boolean) {
            val value = if (enabled) 1 else 0
            val result = SuUtils.execPrivileged("setprop $property $value")
            if (result) makeToast(R.string.settings_need_reboot)
            else makeToast(R.string.settings_permission_denied)
            preference.isChecked = checker()
        }
    }

    class SettingsPreferenceFragment : PreferenceFragmentCompat() {
        private fun Boolean.enabledString(): String {
            return if (this) getString(R.string.enabled)
            else getString(R.string.disabled)
        }

        private fun configureDataIsolation() {
            findPreference<Preference>("dataIsolation")?.let {
                it.isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                it.summary = when {
                    it.isEnabled -> getString(
                        R.string.settings_data_isolation_summary,
                        CommonUtils.isAppDataIsolationEnabled.enabledString(),
                        CommonUtils.isVoldAppDataIsolationEnabled.enabledString(),
                        ConfigManager.forceMountData.enabledString()
                    )
                    else -> getString(R.string.settings_data_isolation_unsupported)
                }
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = SettingsPreferenceDataStore()
            setPreferencesFromResource(R.xml.settings, rootKey)

            @Suppress("DEPRECATION")
            findPreference<SimpleMenuPreference>("language")?.let {
                val userLocale = sysApp.getLocale(PrefManager.locale)
                val entries = buildList {
                    for (lang in LangList.LOCALES) {
                        if (lang == "SYSTEM") add(getString(rikka.core.R.string.follow_system))
                        else {
                            val locale = Locale.forLanguageTag(lang)
                            add(HtmlCompat.fromHtml(locale.getDisplayName(locale), HtmlCompat.FROM_HTML_MODE_LEGACY))
                        }
                    }
                }
                it.entries = entries.toTypedArray()
                it.entryValues = LangList.LOCALES
                if (it.value == "SYSTEM") {
                    it.summary = getString(rikka.core.R.string.follow_system)
                } else {
                    val locale = Locale.forLanguageTag(it.value)
                    it.summary = if (!TextUtils.isEmpty(locale.script)) locale.getDisplayScript(userLocale) else locale.getDisplayName(userLocale)
                }
                it.setOnPreferenceChangeListener { _, newValue ->
                    val locale = sysApp.getLocale(newValue as String)
                    val config = resources.configuration
                    config.setLocale(locale)
                    LocaleDelegate.defaultLocale = locale
                    sysApp.resources.updateConfiguration(config, resources.displayMetrics)
                    activity?.recreate()
                    true
                }
            }

            findPreference<SwitchPreference>("followSystemAccent")?.setOnPreferenceChangeListener { _, _ ->
                activity?.recreate()
                true
            }

            findPreference<SimpleMenuPreference>("themeColor")?.setOnPreferenceChangeListener { _, _ ->
                activity?.recreate()
                true
            }

            findPreference<SimpleMenuPreference>("darkTheme")?.setOnPreferenceChangeListener { _, newValue ->
                val newMode = (newValue as String).toInt()
                if (PrefManager.darkTheme != newMode) {
                    AppCompatDelegate.setDefaultNightMode(newMode)
                    activity?.recreate()
                }
                true
            }

            findPreference<SwitchPreference>("blackDarkTheme")?.setOnPreferenceChangeListener { _, _ ->
                activity?.recreate()
                true
            }

            configureDataIsolation()

            findPreference<Preference>("stopSystemService")?.setOnPreferenceClickListener {
                if (ServiceClient.serviceVersion != 0) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.settings_is_clean_env)
                        .setMessage(R.string.settings_is_clean_env_summary)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            ServiceClient.stopService(true)
                            makeToast(R.string.settings_stop_system_service)
                        }
                        .setNegativeButton(R.string.no) { _, _ ->
                            ServiceClient.stopService(false)
                            makeToast(R.string.settings_stop_system_service)
                        }
                        .setNeutralButton(android.R.string.cancel, null)
                        .show()
                } else makeToast(R.string.home_xposed_service_off)
                true
            }

            findPreference<Preference>("forceCleanEnv")?.setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.settings_force_clean_env)
                    .setMessage(R.string.settings_is_clean_env_summary)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val result = SuUtils.execPrivileged("rm -rf /data/misc/com.lbe.security.*")
                        if (result) makeToast(R.string.settings_force_clean_env_toast_successful)
                        else makeToast(R.string.settings_permission_denied)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
        }

        override fun onResume() {
            super.onResume()
            configureDataIsolation()
        }
    }
}
