package org.fossify.phone.dialogs

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

class VoiceDialLanguageDialog : BaseBottomSheetDialogFragment() {
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
        updateTitles()
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

    private fun updateTitles() {
        binding.apply {
            languageSelectionTitle.text = getString(R.string.select_voice_language)
            languageSelectionSubtitle.text = getString(R.string.choose_voice_language)
        }
    }

    private fun setupLanguageOptions() {
        val currentLanguage = requireActivity().config.voiceDialLanguage
        
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
        
        // Save voice dial language preference
        activity.config.voiceDialLanguage = languageCode
        
        // Mark that voice dial language has been selected
        activity.config.isVoiceDialLanguageSelected = true
        
        // Callback
        onLanguageSelected?.invoke(languageCode)
        
        // Dismiss dialog
        dismissAllowingStateLoss()
    }

    companion object {
        private const val TAG = "VoiceDialLanguageDialog"

        fun show(
            fragmentManager: FragmentManager,
            onLanguageSelected: ((String) -> Unit)? = null
        ): VoiceDialLanguageDialog {
            return VoiceDialLanguageDialog().apply {
                this.onLanguageSelected = onLanguageSelected
                show(fragmentManager, TAG)
            }
        }
    }
}
