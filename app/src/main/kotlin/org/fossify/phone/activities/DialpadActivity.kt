package org.fossify.phone.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony.Sms.Intents.SECRET_CODE_ACTION
import android.telephony.TelephonyManager
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.getColorStateList
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isDefaultDialer
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.KeypadHelper
import org.fossify.commons.helpers.LOWER_ALPHA_INT
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.REQUEST_CODE_SET_DEFAULT_DIALER
import org.fossify.commons.helpers.isOreoPlus
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.adapters.ContactsAdapter
import org.fossify.phone.databinding.ActivityDialpadBinding
import org.fossify.phone.extensions.addCharacter
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.boundingBox
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.deduplicate
import org.fossify.phone.extensions.disableKeyboard
import org.fossify.phone.dialogs.VoiceDialLanguageDialog
import org.fossify.phone.dialogs.VideoCallOptionsDialog
import org.fossify.phone.extensions.getKeyEvent
import org.fossify.phone.extensions.setupWithContacts
import org.fossify.phone.extensions.startCallIntent
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.helpers.DIALPAD_TONE_LENGTH_MS
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.helpers.ToneGeneratorHelper
import org.fossify.phone.models.SpeedDial
import org.fossify.phone.services.ContactResolver
import org.fossify.phone.services.DialerService
import android.widget.Toast
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.roundToInt
import org.fossify.phone.helpers.JitsiMeetHelper

class DialpadActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDialpadBinding::inflate)

    companion object {
        private const val REQUEST_VOICE_RECOGNITION = 1021
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 1022
    }

    private var allContacts = ArrayList<Contact>()
    private var speedDialValues = ArrayList<SpeedDial>()
    private var privateCursor: Cursor? = null
    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private var t9SearchJob: kotlinx.coroutines.Job? = null
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()
    private lateinit var jitsiMeetHelper: JitsiMeetHelper

    private var hasRussianLocale = false
    private val russianCharsMap by lazy {
        hashMapOf(
            'а' to 2, 'б' to 2, 'в' to 2, 'г' to 2,
            'д' to 3, 'е' to 3, 'ё' to 3, 'ж' to 3, 'з' to 3,
            'и' to 4, 'й' to 4, 'к' to 4, 'л' to 4,
            'м' to 5, 'н' to 5, 'о' to 5, 'п' to 5,
            'р' to 6, 'с' to 6, 'т' to 6, 'у' to 6,
            'ф' to 7, 'х' to 7, 'ц' to 7, 'ч' to 7,
            'ш' to 8, 'щ' to 8, 'ъ' to 8, 'ы' to 8,
            'ь' to 9, 'э' to 9, 'ю' to 9, 'я' to 9
        )
    }

    @Suppress("LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        hasRussianLocale = Locale.getDefault().language == "ru"
        
        // Initialize Jitsi Meet helper for video calling
        jitsiMeetHelper = JitsiMeetHelper(this)

        binding.apply {
            updateMaterialActivityViews(
                mainCoordinatorLayout = dialpadCoordinator,
                nestedView = dialpadHolder,
                useTransparentNavigation = false,
                useTopSearchMenu = false
            )
            setupMaterialScrollListener(dialpadList, dialpadToolbar)
            dialpadList.setHasFixedSize(true)
            dialpadList.setItemViewCacheSize(20)
        }

        updateNavigationBarColor(getProperBackgroundColor())

        if (checkAppSideloading()) {
            return
        }

        binding.dialpadWrapper.apply {
            if (config.hideDialpadNumbers) {
                dialpad1Holder.isVisible = false
                dialpad2Holder.isVisible = false
                dialpad3Holder.isVisible = false
                dialpad4Holder.isVisible = false
                dialpad5Holder.isVisible = false
                dialpad6Holder.isVisible = false
                dialpad7Holder.isVisible = false
                dialpad8Holder.isVisible = false
                dialpad9Holder.isVisible = false
                dialpadPlusHolder.isVisible = true
                dialpad0Holder.visibility = View.INVISIBLE
            }

            styleDialpadKeys()
        }

        setupOptionsMenu()
        speedDialValues = config.getSpeedDialValues()
        privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)

        toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)

        binding.dialpadWrapper.apply {
            if (hasRussianLocale) {
                dialpad2Letters.append("\nАБВГ")
                dialpad3Letters.append("\nДЕЁЖЗ")
                dialpad4Letters.append("\nИЙКЛ")
                dialpad5Letters.append("\nМНОП")
                dialpad6Letters.append("\nРСТУ")
                dialpad7Letters.append("\nФХЦЧ")
                dialpad8Letters.append("\nШЩЪЫ")
                dialpad9Letters.append("\nЬЭЮЯ")

                val fontSize = resources.getDimension(R.dimen.small_text_size)
                arrayOf(
                    dialpad2Letters,
                    dialpad3Letters,
                    dialpad4Letters,
                    dialpad5Letters,
                    dialpad6Letters,
                    dialpad7Letters,
                    dialpad8Letters,
                    dialpad9Letters
                ).forEach {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                }
            }

            setupCharClick(dialpad1Holder, '1')
            setupCharClick(dialpad2Holder, '2')
            setupCharClick(dialpad3Holder, '3')
            setupCharClick(dialpad4Holder, '4')
            setupCharClick(dialpad5Holder, '5')
            setupCharClick(dialpad6Holder, '6')
            setupCharClick(dialpad7Holder, '7')
            setupCharClick(dialpad8Holder, '8')
            setupCharClick(dialpad9Holder, '9')
            setupCharClick(dialpad0Holder, '0')
            setupCharClick(dialpadPlusHolder, '+', longClickable = false)
            setupCharClick(dialpadAsteriskHolder, '*', longClickable = false)
            setupCharClick(dialpadHashtagHolder, '#', longClickable = false)
        }

        binding.apply {
            dialpadClearChar.setOnClickListener { clearChar(it) }
            dialpadClearChar.setOnLongClickListener { clearInput(); true }
            dialpadCallButton.setOnClickListener { initCall(dialpadInput.value) }
            dialpadCallButton.setOnLongClickListener { initCallWithSimSelector() }
            dialpadVideoCallButton.setOnClickListener { initVideoCall(dialpadInput.value) }

            // Tactile micro-scale touch feedback
            val touchScaleListener = View.OnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
                false
            }
            dialpadCallButton.setOnTouchListener(touchScaleListener)
            dialpadVoiceButton.setOnTouchListener(touchScaleListener)
            dialpadVideoCallButton.setOnTouchListener(touchScaleListener)
            dialpadVoiceButton.setOnClickListener {
                // Request RECORD_AUDIO permission if not granted
                if (ContextCompat.checkSelfPermission(this@DialpadActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@DialpadActivity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
                } else {
                    // Show language selection dialog before starting voice recognition
                    showVoiceLanguageSelectionDialog()
                }
            }
            dialpadInput.onTextChangeListener { text ->
                dialpadValueChanged(text)
                dialpadClearChar.beVisibleIf(text.isNotEmpty())
                updateSmartBar(text)
            }
            dialpadAddContactChip.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                addNumberToContact()
            }
            dialpadInput.requestFocus()
            dialpadInput.disableKeyboard()
            if (config.isSimpleMode) {
                dialpadInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            }
        }

        org.fossify.phone.helpers.ContactsRepository.loadContacts(this) { allContacts ->
            gotContacts(allContacts)
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.apply {
            styleActionButtons()

            letterFastscroller.textColor = getProperTextColor().getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    override fun onResume() {
        super.onResume()
        updateTextColors(binding.dialpadHolder)
        binding.dialpadClearChar.applyColorFilter(getProperTextColor())
        binding.dialpadClearChar.beVisibleIf(binding.dialpadInput.value.isNotEmpty())
        updateNavigationBarColor(getProperBackgroundColor())
        setupToolbar(binding.dialpadToolbar, NavigationIcon.Arrow)
        binding.dialpadToolbar.popupTheme = R.style.ThemeOverlay_SimplyDial_PopupMenu
        styleDialpadKeys()
        styleActionButtons()
        updateSmartBar(binding.dialpadInput.value)
    }

    private fun setupOptionsMenu() {
        binding.dialpadToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_number_to_contact -> addNumberToContact()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun checkDialIntent(): Boolean {
        return if (
            (intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW)
            && intent.data != null && intent.dataString?.contains("tel:") == true
        ) {
            val number = Uri.decode(intent.dataString).substringAfter("tel:")
            binding.dialpadInput.setText(number)
            binding.dialpadInput.setSelection(number.length)
            true
        } else {
            false
        }
    }

    private fun addNumberToContact() {
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, binding.dialpadInput.value)
            launchActivityIntent(this)
        }
    }

    private fun dialpadPressed(char: Char, view: View?) {
        binding.dialpadInput.addCharacter(char)
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearChar(view: View) {
        binding.dialpadInput.dispatchKeyEvent(binding.dialpadInput.getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearInput() {
        binding.dialpadInput.setText("")
    }

    private fun clearInputWithDelay() {
        lifecycleScope.launch {
            delay(1000)
            clearInput()
        }
    }

    private fun gotContacts(newContacts: ArrayList<Contact>) {
        allContacts = newContacts

        val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
        if (privateContacts.isNotEmpty()) {
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }
        allContacts = allContacts.deduplicate()

        runOnUiThread {
            if (!checkDialIntent() && binding.dialpadInput.value.isEmpty()) {
                dialpadValueChanged("")
            }
        }
    }

    private fun dialpadValueChanged(text: String) {
        if (text.isEmpty()) {
            checkClipboardForNumber()
        } else {
            binding.dialpadSmartBar.beGone()
        }
        val len = text.length
        if (len > 8 && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            val secretCode = text.substring(4, text.length - 4)
            if (isOreoPlus()) {
                if (isDefaultDialer()) {
                    getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
                } else {
                    launchSetDefaultDialerIntent()
                }
            } else {
                val intent =
                    Intent(SECRET_CODE_ACTION, "android_secret_code://$secretCode".toUri())
                sendBroadcast(intent)
            }
            return
        }

        (binding.dialpadList.adapter as? ContactsAdapter)?.finishActMode()

        t9SearchJob?.cancel()
        if (text.isEmpty()) {
            binding.dialpadList.adapter = null
            binding.dialpadPlaceholder.beGone()
            binding.letterFastscroller.beGone()
            return
        }

        t9SearchJob = lifecycleScope.launch(Dispatchers.Default) {
            val filtered = allContacts.filter { contact ->
                var convertedName = KeypadHelper.convertKeypadLettersToDigits(
                    contact.name.normalizeString()
                ).filterNot { it.isWhitespace() }

                if (hasRussianLocale) {
                    var currConvertedName = ""
                    convertedName.lowercase(Locale.getDefault()).forEach { char ->
                        val convertedChar = russianCharsMap.getOrElse(char) { char }
                        currConvertedName += convertedChar
                    }
                    convertedName = currConvertedName
                }

                contact.doesContainPhoneNumber(text) || (convertedName.contains(text, true))
            }.sortedWith(compareBy {
                !it.doesContainPhoneNumber(text)
            }).toMutableList() as ArrayList<Contact>

            withContext(Dispatchers.Main) {
                if (!isActive) return@withContext
                binding.letterFastscroller.setupWithContacts(binding.dialpadList, filtered)

                ContactsAdapter(
                    activity = this@DialpadActivity,
                    contacts = filtered,
                    recyclerView = binding.dialpadList,
                    highlightText = text,
                    itemClick = {
                        val contact = it as Contact
                        if (config.showQuickActionsSheet && !config.directOneTapCalling) {
                            (binding.dialpadList.adapter as? ContactsAdapter)?.showContactQuickActions(contact)
                        } else {
                            startCallWithConfirmationCheck(contact)
                            clearInputWithDelay()
                        }
                    },
                    profileIconClick = {
                        if (config.enableProfilePictureClick) {
                            startContactDetailsIntent(it as Contact)
                        }
                    }).apply {
                    binding.dialpadList.adapter = this
                }

                binding.dialpadPlaceholder.beVisibleIf(filtered.isEmpty())
                binding.dialpadList.beVisibleIf(filtered.isNotEmpty())
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER && isDefaultDialer()) {
            dialpadValueChanged(binding.dialpadInput.value)
        }
        if (requestCode == REQUEST_VOICE_RECOGNITION && resultCode == Activity.RESULT_OK && resultData != null) {
            val matches = resultData.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim() ?: ""
            if (spoken.isNotEmpty()) {
                handleSpokenDialRequest(spoken)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showVoiceLanguageSelectionDialog()
            } else {
                // permission denied — could show a Snackbar or dialog; keep it simple for now
            }
        }
    }

    private fun showVoiceLanguageSelectionDialog() {
        // Check if voice dial language has already been selected
        if (config.isVoiceDialLanguageSelected) {
            // Use saved language preference directly
            startVoiceRecognition(config.voiceDialLanguage)
        } else {
            // Show dialog for first-time selection
            VoiceDialLanguageDialog.show(supportFragmentManager) { selectedLanguage ->
                // Language selected, now start voice recognition with selected language
                startVoiceRecognition(selectedLanguage)
            }
        }
    }

    private fun startVoiceRecognition(languageCode: String) {
        try {
            // visual feedback: reduce alpha to indicate listening
            binding.dialpadVoiceButton.alpha = 0.6f
            
            // Map language code to locale for voice recognition
            val locale = when (languageCode) {
                "hi" -> "hi-IN"  // Hindi (India)
                "en" -> "en-US"  // English (US)
                else -> "en-US"
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, locale)
                putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_dial))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            startActivityForResult(intent, REQUEST_VOICE_RECOGNITION)
        } catch (e: Exception) {
            binding.dialpadVoiceButton.alpha = 1f
            Toast.makeText(this, "Voice recognition not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSpokenDialRequest(spoken: String) {
        // Restore UI
        binding.dialpadVoiceButton.alpha = 1f

        // If the spoken text looks like a phone number (contains digits), extract digits and call
        val digits = spoken.filter { it.isDigit() || it == '+' }
        if (digits.isNotEmpty()) {
            initCall(digits, spoken)
            return
        }

        // Normalize spoken text: strip common verbs and politeness so matching works better
        val cleaned = spoken
            .lowercase(Locale.getDefault())
            .replace("please", "")
            .replace("kindly", "")
            .replace("could you", "")
            .replace("call to", "")
            .replace("call", "")
            .replace("dial", "")
            .replace("phone", "")
            .replace("make a call to", "")
            .replace("make a call", "")
            .replace("please call", "")
            .trim()

        val searchQuery = if (cleaned.isNotEmpty()) cleaned else spoken.trim()

        lifecycleScope.launch(Dispatchers.IO) {
            val matches = ContactResolver.findMatches(this@DialpadActivity, searchQuery)
            withContext(Dispatchers.Main) {
                when {
                    matches.isEmpty() -> {
                        binding.dialpadInput.setText(spoken)
                        binding.dialpadInput.setSelection(spoken.length)
                        Toast.makeText(this@DialpadActivity, getString(R.string.no_contact_found), Toast.LENGTH_SHORT).show()
                    }
                    matches.size == 1 -> {
                        val chosen = matches[0]
                        startCallIntent(chosen.number)
                        clearInputWithDelay()
                    }
                    else -> {
                        DialerService.promptUserToChoose(this@DialpadActivity, matches) { chosen ->
                            startCallIntent(chosen.number)
                            clearInputWithDelay()
                        }
                    }
                }
            }
        }
    }

    private fun initCall(number: String = binding.dialpadInput.value, name: String? = null) {
        if (number.isNotEmpty()) {
            startCallWithConfirmationCheck(number, name ?: number)
            clearInputWithDelay()
        } else {
            RecentsHelper(this).getRecentCalls(queryLimit = 1) {
                val mostRecentNumber = it.firstOrNull()?.phoneNumber
                if (!mostRecentNumber.isNullOrEmpty()) {
                    runOnUiThread {
                        binding.dialpadInput.setText(mostRecentNumber)
                    }
                }
            }
        }
    }

    private fun initCallWithSimSelector(): Boolean {
        val number = binding.dialpadInput.value
        return if (areMultipleSIMsAvailable() && number.isNotEmpty()) {
            startCallWithConfirmationCheck(
                recipient = number,
                name = number,
                forceSimSelector = true
            )
            true
        } else {
            false
        }
    }

    private fun speedDial(id: Int): Boolean {
        if (binding.dialpadInput.value.length == 1) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                initCall(speedDial.number, speedDial.getName(this))
                return true
            }
        }
        return false
    }

    private fun startDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            pressedKeys.add(char)
            toneGeneratorHelper?.startTone(char)
        }
    }

    private fun stopDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            if (!pressedKeys.remove(char)) return
            if (pressedKeys.isEmpty()) {
                toneGeneratorHelper?.stopTone()
            } else {
                startDialpadTone(pressedKeys.last())
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        if (config.dialpadVibration) {
            view?.performHapticFeedback()
        }
    }

    private fun performLongClick(view: View, char: Char) {
        if (char == '0') {
            clearChar(view)
            dialpadPressed('+', view)
        } else {
            val result = speedDial(char.digitToInt())
            if (result) {
                stopDialpadTone(char)
                clearChar(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCharClick(view: View, char: Char, longClickable: Boolean = true) {
        view.isClickable = true
        view.isLongClickable = true
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.90f).scaleY(0.90f).setDuration(60).start()
                    dialpadPressed(char, view)
                    startDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        longPressHandler.postDelayed({
                            performLongClick(view, char)
                        }, longPressTimeout)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    stopDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val viewContainsTouchEvent = if (event.rawX.isNaN() || event.rawY.isNaN()) {
                        false
                    } else {
                        view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
                    }

                    if (!viewContainsTouchEvent) {
                        stopDialpadTone(char)
                        if (longClickable) {
                            longPressHandler.removeCallbacksAndMessages(null)
                        }
                    }
                }
            }
            false
        }
    }
    
    /**
     * Initiate a video call - shows options dialog to choose call method
     */
    private fun initVideoCall(number: String = binding.dialpadInput.value) {
        if (number.isEmpty()) {
            Toast.makeText(this, "Please enter a number", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if this is a contact or just a number
        val matchingContact = allContacts.firstOrNull { contact ->
            contact.doesContainPhoneNumber(number)
        }
        
        val contactName = matchingContact?.name ?: number
        
        // Show video call options dialog
        VideoCallOptionsDialog(
            activity = this,
            contactName = contactName,
            contactNumber = number
        )
    }
    private fun styleDialpadKeys() {
        val isSimple = config.isSimpleMode
        val properPrimaryColor = getProperPrimaryColor()
        val properTextColor = getProperTextColor()
        val isDarkTheme = org.fossify.phone.helpers.ThemeManager.isDarkThemeEnabled(this)
        val density = resources.displayMetrics.density
        val keyCornerRadius = (if (isSimple) 18f else 22f) * density
        val strokeWidth = ((if (isSimple) 2 else 1) * density).toInt()

        val keyBgColor = if (isSimple) {
            if (isDarkTheme) Color.parseColor("#1C1C1E") else Color.WHITE
        } else {
            if (isDarkTheme) Color.parseColor("#121316") else Color.parseColor("#F3F5FA")
        }

        val strokeColor = if (isSimple) {
            if (isDarkTheme) Color.parseColor("#38383A") else Color.parseColor("#CBD5E1")
        } else {
            if (isDarkTheme) properPrimaryColor.adjustAlpha(0.22f) else Color.parseColor("#E2E8F0")
        }

        val rippleColor = properPrimaryColor.adjustAlpha(0.25f)

        val keys = arrayOf(
            binding.dialpadWrapper.dialpad0Holder,
            binding.dialpadWrapper.dialpad1Holder,
            binding.dialpadWrapper.dialpad2Holder,
            binding.dialpadWrapper.dialpad3Holder,
            binding.dialpadWrapper.dialpad4Holder,
            binding.dialpadWrapper.dialpad5Holder,
            binding.dialpadWrapper.dialpad6Holder,
            binding.dialpadWrapper.dialpad7Holder,
            binding.dialpadWrapper.dialpad8Holder,
            binding.dialpadWrapper.dialpad9Holder,
            binding.dialpadWrapper.dialpadPlusHolder,
            binding.dialpadWrapper.dialpadAsteriskHolder,
            binding.dialpadWrapper.dialpadHashtagHolder
        )

        keys.forEach { holder ->
            val contentDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = keyCornerRadius
                setColor(keyBgColor)
                setStroke(strokeWidth, strokeColor)
            }

            val maskDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = keyCornerRadius
                setColor(Color.WHITE)
            }

            holder.background = RippleDrawable(
                ColorStateList.valueOf(rippleColor),
                contentDrawable,
                maskDrawable
            )
            holder.elevation = if (isSimple) (4 * density) else (2 * density)
        }

        // Sub-letters coloring & typography
        val letterColor = if (isSimple) properTextColor.adjustAlpha(0.80f) else properPrimaryColor.adjustAlpha(0.90f)
        val numberSize = if (isSimple) 34f else 28f
        val letterSize = if (isSimple) 13f else 11f

        val numberViews = arrayOf(
            binding.dialpadWrapper.dialpad0,
            binding.dialpadWrapper.dialpad1,
            binding.dialpadWrapper.dialpad2,
            binding.dialpadWrapper.dialpad3,
            binding.dialpadWrapper.dialpad4,
            binding.dialpadWrapper.dialpad5,
            binding.dialpadWrapper.dialpad6,
            binding.dialpadWrapper.dialpad7,
            binding.dialpadWrapper.dialpad8,
            binding.dialpadWrapper.dialpad9,
            binding.dialpadWrapper.dialpadAsterisk,
            binding.dialpadWrapper.dialpadHashtag,
            binding.dialpadWrapper.dialpadPlusAlt
        )

        numberViews.forEach { tv ->
            tv.setTextColor(properTextColor)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, numberSize)
            tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        }

        val letterViews = arrayOf(
            binding.dialpadWrapper.dialpad2Letters,
            binding.dialpadWrapper.dialpad3Letters,
            binding.dialpadWrapper.dialpad4Letters,
            binding.dialpadWrapper.dialpad5Letters,
            binding.dialpadWrapper.dialpad6Letters,
            binding.dialpadWrapper.dialpad7Letters,
            binding.dialpadWrapper.dialpad8Letters,
            binding.dialpadWrapper.dialpad9Letters,
            binding.dialpadWrapper.dialpadPlusLetters
        )

        letterViews.forEach { tv ->
            tv.setTextColor(letterColor)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, letterSize)
            if (!isSimple) {
                tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
            }
        }
    }

    private fun styleActionButtons() {
        val isSimple = config.isSimpleMode
        val isDark = org.fossify.phone.helpers.ThemeManager.isDarkThemeEnabled(this)
        val properPrimaryColor = getProperPrimaryColor()
        val properTextColor = getProperTextColor()
        val contrastColor = properPrimaryColor.getContrastColor()
        val density = resources.displayMetrics.density

        binding.apply {
            // Smart chips styling
            dialpadPasteChip.setTextColor(properTextColor)
            dialpadPasteChip.chipIconTint = android.content.res.ColorStateList.valueOf(properPrimaryColor)
            dialpadAddContactChip.setTextColor(properTextColor)
            dialpadAddContactChip.chipIconTint = android.content.res.ColorStateList.valueOf(properPrimaryColor)

            // Call button styling
            val callBtnSize = ((if (isSimple) 74 else 68) * density).toInt()
            dialpadCallButton.layoutParams.width = callBtnSize
            dialpadCallButton.layoutParams.height = callBtnSize

            val callBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(properPrimaryColor)
            }
            dialpadCallButton.background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
                callBg,
                null
            )
            dialpadCallButton.elevation = 6 * density
            dialpadCallButton.setImageDrawable(
                resources.getColoredDrawableWithColor(R.drawable.ic_phone_vector, contrastColor)
            )

            // Voice button styling
            if (isSimple) {
                dialpadVoiceButton.beGone()
            } else {
                dialpadVoiceButton.beVisible()
                val voiceBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    val bgColor = if (isDark) Color.parseColor("#1C1D21") else Color.parseColor("#F1F5F9")
                    setColor(bgColor)
                    val strokeCol = if (isDark) properPrimaryColor.adjustAlpha(0.35f) else Color.parseColor("#CBD5E1")
                    setStroke((1.5f * density).toInt(), strokeCol)
                }
                dialpadVoiceButton.background = RippleDrawable(
                    ColorStateList.valueOf(properPrimaryColor.adjustAlpha(0.25f)),
                    voiceBg,
                    null
                )
                dialpadVoiceButton.setImageDrawable(
                    resources.getColoredDrawableWithColor(R.drawable.ic_mic_vector, properPrimaryColor)
                )
            }

            // Video button styling
            if (isSimple) {
                dialpadVideoCallButton.beGone()
            } else {
                dialpadVideoCallButton.beVisible()
                val videoBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    val bgColor = if (isDark) Color.parseColor("#1C1D21") else Color.parseColor("#F1F5F9")
                    setColor(bgColor)
                    val strokeCol = if (isDark) properPrimaryColor.adjustAlpha(0.35f) else Color.parseColor("#CBD5E1")
                    setStroke((1.5f * density).toInt(), strokeCol)
                }
                dialpadVideoCallButton.background = RippleDrawable(
                    ColorStateList.valueOf(properPrimaryColor.adjustAlpha(0.25f)),
                    videoBg,
                    null
                )
                dialpadVideoCallButton.setImageDrawable(
                    resources.getColoredDrawableWithColor(R.drawable.ic_video_call_vector, properPrimaryColor)
                )
            }
        }
    }

    private fun updateSmartBar(text: String) {
        if (config.isSimpleMode) {
            binding.dialpadSmartBar.beGone()
            return
        }

        if (text.isEmpty()) {
            binding.dialpadAddContactChip.beGone()
            checkClipboardForNumber()
        } else {
            binding.dialpadPasteChip.beGone()
            val digitsOnly = text.filter { it.isDigit() || it == '+' }
            val hasExactContact = allContacts.any { c -> c.phoneNumbers.any { it.normalizedNumber == digitsOnly } }
            if (!hasExactContact && digitsOnly.length >= 3) {
                binding.dialpadAddContactChip.beVisible()
                binding.dialpadSmartBar.beVisible()
            } else {
                binding.dialpadAddContactChip.beGone()
                binding.dialpadSmartBar.beGone()
            }
        }
    }
    private fun checkClipboardForNumber() {
        if (config.isSimpleMode) {
            binding.dialpadSmartBar.beGone()
            return
        }

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                val digitsOnly = clipText.filter { it.isDigit() || it == '+' }
                if (digitsOnly.length >= 3 && binding.dialpadInput.value.isEmpty()) {
                    val displayText = if (clipText.length > 15) "${clipText.take(15)}..." else clipText
                    binding.dialpadPasteChip.text = "Paste: $displayText"
                    binding.dialpadPasteChip.setOnClickListener {
                        it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        binding.dialpadInput.setText(digitsOnly)
                        binding.dialpadInput.setSelection(digitsOnly.length)
                        binding.dialpadSmartBar.beGone()
                    }
                    binding.dialpadSmartBar.beVisible()
                    return
                }
            }
        } catch (_: Exception) {}

        binding.dialpadSmartBar.beGone()
    }
}
