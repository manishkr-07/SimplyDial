package org.fossify.phone.dialogs

import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.fragments.BaseBottomSheetDialogFragment
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogLanguageSelectionBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.LANGUAGE_ENGLISH
import org.fossify.phone.helpers.LANGUAGE_HINDI
import java.util.Locale

class LanguageBottomSheetDialog : BaseBottomSheetDialogFragment() {
    private lateinit var binding: DialogLanguageSelectionBinding
    
    var onLanguageSelected: ((String) -> Unit)? = null

    override fun setupContentView(parent: ViewGroup) {
        binding = DialogLanguageSelectionBinding.inflate(layoutInflater, parent, false)
        parent.addView(binding.root)
        setupLanguageOptions()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupColors()
    }

    private fun setupColors() {
        val activity = requireActivity()
        val properTextColor = activity.getProperTextColor()
        val properPrimaryColor = activity.getProperPrimaryColor()
        
        activity.updateTextColors(binding.root)
        
        binding.apply {
            languageSelectionTitle.setTextColor(properTextColor)
            languageSelectionSubtitle.setTextColor(properTextColor)
            languageEnglishName.setTextColor(properTextColor)
            languageHindiName.setTextColor(properTextColor)
            
            // Update radio button colors
            radioEnglish.buttonTintList = android.content.res.ColorStateList.valueOf(properPrimaryColor)
            radioHindi.buttonTintList = android.content.res.ColorStateList.valueOf(properPrimaryColor)
        }
    }

    private fun setupLanguageOptions() {
        val currentLanguage = requireActivity().config.appLanguage
        
        binding.apply {
            // Set initial selection
            when (currentLanguage) {
                LANGUAGE_ENGLISH -> radioEnglish.isChecked = true
                LANGUAGE_HINDI -> radioHindi.isChecked = true
            }
            
            // English option click
            languageOptionEnglish.setOnClickListener {
                radioEnglish.isChecked = true
                radioHindi.isChecked = false
                selectLanguage(LANGUAGE_ENGLISH)
            }
            
            // Hindi option click
            languageOptionHindi.setOnClickListener {
                radioHindi.isChecked = true
                radioEnglish.isChecked = false
                selectLanguage(LANGUAGE_HINDI)
            }
        }
    }

    private fun selectLanguage(languageCode: String) {
        val activity = requireActivity()
        
        // Save language preference
        activity.config.appLanguage = languageCode
        activity.config.isLanguageSelected = true
        
        // Apply language change
        setAppLanguage(languageCode)
        
        // Callback
        onLanguageSelected?.invoke(languageCode)
        
        // Dismiss dialog
        dismissAllowingStateLoss()
        
        // Restart activity to apply language
        activity.recreate()
    }

    private fun setAppLanguage(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val config = Configuration(requireActivity().resources.configuration)
        config.setLocale(locale)
        
        requireActivity().resources.updateConfiguration(config, requireActivity().resources.displayMetrics)
    }

    companion object {
        private const val TAG = "LanguageBottomSheetDialog"

        fun show(
            fragmentManager: FragmentManager,
            onLanguageSelected: ((String) -> Unit)? = null
        ): LanguageBottomSheetDialog {
            return LanguageBottomSheetDialog().apply {
                this.onLanguageSelected = onLanguageSelected
                show(fragmentManager, TAG)
            }
        }
    }
}
