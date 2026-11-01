package org.fossify.phone.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Outline
import android.graphics.drawable.Icon
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.phone.databinding.ItemContactWithoutNumberBinding
import org.fossify.phone.databinding.ItemContactWithoutNumberGridBinding
import org.fossify.phone.databinding.DialogContactQuickActionsBinding
import androidx.core.content.ContextCompat
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FeatureLockedDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.interfaces.ItemMoveCallback
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.commons.interfaces.StartReorderDragListener
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.extensions.addAnimatedGradientRing
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.callContactWithSim
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.commons.extensions.getPhoneNumberTypeText
import org.fossify.phone.dialogs.VideoCallOptionsDialog
import org.fossify.phone.helpers.JitsiMeetHelper
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.phone.interfaces.RefreshItemsListener
import org.fossify.phone.models.RecentCall
import android.provider.CallLog
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Collections

class ContactsAdapter(
    activity: SimpleActivity,
    var contacts: MutableList<Contact>,
    recyclerView: MyRecyclerView,
    highlightText: String = "",
    private val refreshItemsListener: RefreshItemsListener? = null,
    var viewType: Int = VIEW_TYPE_LIST,
    private val showDeleteButton: Boolean = true,
    private val enableDrag: Boolean = false,
    private val allowLongClick: Boolean = true,
    itemClick: (Any) -> Unit,
    val profileIconClick: ((Any) -> Unit)? = null
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick),
    ItemTouchHelperContract, MyRecyclerView.MyZoomListener {

    private var textToHighlight = highlightText
    var fontSize: Float = activity.getTextSize()
    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener? = null
    var onDragEndListener: (() -> Unit)? = null
    var onSpanCountListener: (Int) -> Unit = {}
    



    init {
        setupDragListener(true)
        recyclerView.itemAnimator?.changeDuration = 0

        if (recyclerView.layoutManager is GridLayoutManager) {
            setupZoomListener(this)
        }

        if (enableDrag) {
            touchHelper = ItemTouchHelper(ItemMoveCallback(this, viewType == VIEW_TYPE_GRID))
            touchHelper!!.attachToRecyclerView(recyclerView)

            startReorderDragListener = object : StartReorderDragListener {
                override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                    touchHelper?.startDrag(viewHolder)
                }
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_contacts

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val isOneItemSelected = isOneItemSelected()
        val selectedNumber = getSelectedPhoneNumber().orEmpty()

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && (activity.config.getCustomSIM(selectedNumber) ?: "") != ""

            findItem(R.id.cab_delete).isVisible = showDeleteButton
            findItem(R.id.cab_create_shortcut).title = activity.getString(R.string.create_shortcut)
            findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected && isOreoPlus()
            findItem(R.id.cab_view_details).isVisible = isOneItemSelected
            findItem(R.id.cab_block_unblock_contact).isVisible = isOneItemSelected && isNougatPlus()
            getCabBlockContactTitle { title ->
                findItem(R.id.cab_block_unblock_contact).title = title
            }
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_block_unblock_contact -> tryBlockingUnblocking()
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
            R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_view_details -> viewContactDetails()
            R.id.cab_create_shortcut -> tryCreateShortcut()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = contacts.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = contacts.getOrNull(position)?.rawId

    override fun getItemKeyPosition(key: Int) = contacts.indexOfFirst { it.rawId == key }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = Binding.getByItemViewType(viewType).inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun getItemViewType(position: Int): Int {
        return viewType
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, true, allowLongClick) { itemView, _ ->
            val viewType = getItemViewType(position)
            setupView(Binding.getByItemViewType(viewType).bind(itemView), contact, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size

    private fun getCabBlockContactTitle(callback: (String) -> Unit) {
        val contact = getSelectedItems().firstOrNull() ?: return callback("")

        activity.isContactBlocked(contact) { blocked ->
            val cabItemTitleRes = if (blocked) {
                R.string.unblock_contact
            } else {
                R.string.block_contact
            }

            callback(activity.getString(cabItemTitleRes))
        }
    }

    private fun tryBlockingUnblocking() {
        val contact = getSelectedItems().firstOrNull() ?: return

        activity.isContactBlocked(contact) { blocked ->
            if (blocked) {
                tryUnblocking(contact)
            } else {
                askConfirmBlock(contact) { contactBlocked ->
                    if (contactBlocked) {
                        finishActMode()
                        refreshItemsListener?.refreshItems()
                    }
                }
            }
        }
    }

    private fun tryBlocking(contact: Contact) {
        askConfirmBlock(contact) { contactBlocked ->
            val resultMsg = if (contactBlocked) {
                R.string.block_contact_success
            } else {
                R.string.block_contact_fail
            }

            activity.toast(resultMsg)
            finishActMode()
        }
    }

    private fun tryUnblocking(contact: Contact) {
        val contactUnblocked = activity.unblockContact(contact)
        val resultMsg = if (contactUnblocked) {
            R.string.unblock_contact_success
        } else {
            R.string.unblock_contact_fail
        }

        activity.toast(resultMsg)
        finishActMode()
    }

    private fun askConfirmBlock(contact: Contact, callback: (Boolean) -> Unit) {
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), contact.name)

        ConfirmationDialog(activity, question) {
            val contactBlocked = activity.blockContact(contact)
            callback(contactBlocked)
        }
    }

    fun updateItems(newItems: List<Contact>, highlightText: String = "") {
        val oldItems = contacts
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldItems.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition].rawId == newItems[newItemPosition].rawId
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val o = oldItems[oldItemPosition]
                val n = newItems[newItemPosition]
                // Compare core fields used on UI for minimal invalidation
                val oNum = o.getPrimaryNumber()
                val nNum = n.getPrimaryNumber()
                return o.getNameToDisplay() == n.getNameToDisplay() && o.photoUri == n.photoUri && oNum == nNum
            }
        })
        contacts = ArrayList(newItems)
        textToHighlight = highlightText
        diff.dispatchUpdatesTo(this)
        finishActMode()
    }

    @SuppressLint("MissingPermission")
    private fun callContact(useSimOne: Boolean) {
        val number = getSelectedPhoneNumber() ?: return
        activity.callContactWithSim(number, useSimOne)
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.config.removeCustomSIM(phoneNumber)
        finishActMode()
    }

    private fun sendSMS() {
        val numbers = ArrayList<String>()
        getSelectedItems().map { simpleContact ->
            val contactNumbers = simpleContact.phoneNumbers
            val primaryNumber = contactNumbers.firstOrNull { it.isPrimary }
            val normalizedNumber = primaryNumber?.normalizedNumber ?: contactNumbers.firstOrNull()?.normalizedNumber

            if (normalizedNumber != null) {
                numbers.add(normalizedNumber)
            }
        }

        val recipient = TextUtils.join(";", numbers)
        activity.launchSendSMSIntent(recipient)
    }

    private fun viewContactDetails() {
        val contact = getSelectedItems().firstOrNull() ?: return
        activity.startContactDetailsIntent(contact)
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().firstOrNull() ?: return
        val items = if (itemsCnt == 1) {
            "\"${firstItem.getNameToDisplay()}\""
        } else {
            resources.getQuantityString(R.plurals.delete_contacts, itemsCnt, itemsCnt)
        }

        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            activity.handlePermission(PERMISSION_WRITE_CONTACTS) {
                deleteContacts()
            }
        }
    }

    private fun deleteContacts() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val contactsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        contacts.removeAll(contactsToRemove)
        val idsToRemove = contactsToRemove.map { it.rawId }.toMutableList() as ArrayList<Int>

        SimpleContactsHelper(activity).deleteContactRawIDs(idsToRemove) {
            activity.runOnUiThread {
                if (contacts.isEmpty()) {
                    refreshItemsListener?.refreshItems()
                    finishActMode()
                } else {
                    removeSelectedItems(positions)
                }
            }
        }
    }

    private fun getSelectedItems() = contacts.filter { selectedKeys.contains(it.rawId) } as ArrayList<Contact>

    private fun getSelectedPhoneNumber(): String? {
        return getSelectedItems().firstOrNull()?.getPrimaryNumber()
    }

    private fun tryCreateShortcut() {
        createShortcut()
    }

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val contact = contacts.firstOrNull { selectedKeys.contains(it.rawId) } ?: return
        val manager = activity.shortcutManager
        if (manager.isRequestPinShortcutSupported) {
            SimpleContactsHelper(activity).getShortcutImage(contact.photoUri, contact.getNameToDisplay()) { image ->
                activity.runOnUiThread {
                    activity.handlePermission(PERMISSION_CALL_PHONE) { hasPermission ->
                        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                        val intent = Intent(action).apply {
                            data = Uri.fromParts("tel", getSelectedPhoneNumber(), null)
                        }

                        val shortcut = ShortcutInfo.Builder(activity, contact.hashCode().toString())
                            .setShortLabel(contact.getNameToDisplay())
                            .setIcon(Icon.createWithBitmap(image))
                            .setIntent(intent)
                            .build()

                        manager.requestPinShortcut(shortcut, null)
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Binding.getByItemViewType(holder.itemViewType).bind(holder.itemView).apply {
                Glide.with(activity).clear(itemContactImage)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(binding: ItemViewBinding, contact: Contact, holder: ViewHolder) {
        binding.apply {
            root.setupViewBackground(activity)
            itemContactFrame.isSelected = selectedKeys.contains(contact.rawId)

            itemContactImage.apply {
                if (profileIconClick != null && viewType != VIEW_TYPE_GRID && activity.config.enableProfilePictureClick) {
                    setBackgroundResource(R.drawable.selector_clickable_circle)

                    setOnClickListener {
                        if (!actModeCallback.isSelectable) {
                            profileIconClick.invoke(contact)
                        } else {
                            holder.viewClicked(contact)
                        }
                    }
                    setOnLongClickListener {
                        holder.viewLongClicked()
                        true
                    }
                } else {
                    background = null
                    setOnClickListener(null)
                    setOnLongClickListener(null)
                }
            }

            itemContactName.apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                val name = contact.getNameToDisplay()
                text = if (textToHighlight.isEmpty()) {
                    name
                } else {
                    if (name.contains(textToHighlight, true)) {
                        name.highlightTextPart(textToHighlight, properPrimaryColor)
                    } else {
                        var spacedTextToHighlight = textToHighlight
                        val strippedName = name.filterNot { it.isWhitespace() }
                        val strippedDigits = KeypadHelper.convertKeypadLettersToDigits(strippedName)
                        val startIndex = strippedDigits.indexOf(textToHighlight)

                        if (strippedDigits.contains(textToHighlight)) {
                            for (i in spacedTextToHighlight.indices) {
                                if (startIndex + i < name.length && name[startIndex + i].isWhitespace()) {
                                    spacedTextToHighlight = spacedTextToHighlight.replaceRange(i, i, " ")
                                }
                            }
                        }

                        name.highlightTextFromNumbers(spacedTextToHighlight, properPrimaryColor)
                    }
                }
            }

            // Visible primary phone number & label (Accessible at a glance)
            itemContactNumber?.apply {
                val primaryNum = contact.getPrimaryNumber() ?: contact.phoneNumbers.firstOrNull()?.normalizedNumber ?: ""
                if (primaryNum.isNotEmpty()) {
                    val phoneObj = contact.phoneNumbers.firstOrNull { it.normalizedNumber == primaryNum || it.value == primaryNum }
                    val label = phoneObj?.let { activity.getPhoneNumberTypeText(it.type, it.label) }
                    text = if (!label.isNullOrEmpty()) "$label • $primaryNum" else primaryNum
                    val alphaVal = if (activity.config.isSimpleMode) 0.95f else 0.7f
                    setTextColor(textColor.adjustAlpha(alphaVal))
                    if (activity.config.isSimpleMode) {
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.82f)
                    }
                    beVisible()
                } else {
                    beGone()
                }
            }

            // Direct Quick Call action
            itemContactCallButton?.apply {
                beVisibleIf(selectedKeys.isEmpty())
                setOnClickListener {
                    if (!actModeCallback.isSelectable) {
                        (activity as SimpleActivity).startCallWithConfirmationCheck(contact)
                    } else {
                        holder.viewClicked(contact)
                    }
                }
            }

            // Quick Options / Sheet trigger (3 dots)
            itemContactActionButton?.apply {
                beVisibleIf(selectedKeys.isEmpty())
                setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_three_dots_vector))
                applyColorFilter(activity.getProperTextColor().adjustAlpha(0.75f))
                setOnClickListener {
                    if (!actModeCallback.isSelectable) {
                        showContactQuickActions(contact)
                    } else {
                        holder.viewClicked(contact)
                    }
                }
            }

            // Long-press works the EXACT SAME as tapping 3-dots
            root.setOnLongClickListener {
                if (!actModeCallback.isSelectable) {
                    showContactQuickActions(contact)
                    true
                } else {
                    holder.viewLongClicked()
                    true
                }
            }

            if (enableDrag && textToHighlight.isEmpty()) {
                dragHandleIcon.apply {
                    beVisibleIf(selectedKeys.isNotEmpty())
                    applyColorFilter(textColor)
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            startReorderDragListener?.requestDrag(holder)
                        }
                        false
                    }
                }
            } else {
                dragHandleIcon.apply {
                    beGone()
                    setOnTouchListener(null)
                }
            }

            if (!activity.isDestroyed) {
                SimpleContactsHelper(root.context).loadContactImage(contact.photoUri, itemContactImage, contact.getNameToDisplay())
                itemContactImage.addAnimatedGradientRing()
            }
        }
    }

    fun showContactQuickActions(contact: Contact) {
        if (activity.isDestroyed || activity.isFinishing) return

        val dialog = BottomSheetDialog(activity)
        val sheetBinding = DialogContactQuickActionsBinding.inflate(activity.layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val primaryNumber = contact.getPrimaryNumber() ?: contact.phoneNumbers.firstOrNull()?.normalizedNumber ?: ""

        sheetBinding.apply {
            // Avatar with animated gradient ring
            SimpleContactsHelper(activity).loadContactImage(contact.photoUri, contactSheetAvatar, contact.getNameToDisplay())
            contactSheetAvatar.addAnimatedGradientRing()
            contactSheetAvatar.setOnClickListener {
                dialog.dismiss()
                activity.startContactDetailsIntent(contact)
            }

            // Name & Number
            contactSheetName.text = contact.getNameToDisplay()
            if (primaryNumber.isNotEmpty()) {
                val phoneObj = contact.phoneNumbers.firstOrNull { it.normalizedNumber == primaryNumber || it.value == primaryNumber }
                val label = phoneObj?.let { activity.getPhoneNumberTypeText(it.type, it.label) }
                val numberText = if (!label.isNullOrEmpty()) "$label • $primaryNumber" else primaryNumber
                contactSheetNumber.text = numberText
                contactSheetNumber.beVisible()
                contactSheetNumber.setOnClickListener {
                    activity.copyToClipboard(primaryNumber)
                }
            } else {
                contactSheetNumber.beGone()
            }

            // Action: Call
            contactActionCall.setOnClickListener {
                dialog.dismiss()
                (activity as SimpleActivity).startCallWithConfirmationCheck(contact)
            }

            // Action: SMS
            contactActionSms.setOnClickListener {
                dialog.dismiss()
                sendSMSToContact(contact)
            }

            // Action: Video Call
            contactActionVideo.setOnClickListener {
                dialog.dismiss()
                sendVideoCallLinkToContact(contact)
            }

            // Action: History
            contactActionHistory.setOnClickListener {
                dialog.dismiss()
                showFullHistory(contact)
            }

            // Action: Details / Edit
            contactActionDetails.setOnClickListener {
                dialog.dismiss()
                activity.startContactDetailsIntent(contact)
            }

            // Additional Phone Numbers List
            val additionalNumbers = contact.phoneNumbers.filter { it.normalizedNumber != primaryNumber && it.normalizedNumber.isNotEmpty() }
            if (additionalNumbers.isNotEmpty()) {
                contactNumbersSection.beVisible()
                contactNumbersList.removeAllViews()
                additionalNumbers.forEach { phoneNum ->
                    val numRow = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundResource(R.drawable.contact_action_pill_bg)
                        val pad = (10 * resources.displayMetrics.density).toInt()
                        setPadding(pad, pad, pad, pad)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            dialog.dismiss()
                            (activity as SimpleActivity).startCallWithConfirmationCheck(phoneNum.normalizedNumber, contact.getNameToDisplay())
                        }
                    }

                    val infoLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val numTextView = TextView(activity).apply {
                        text = phoneNum.normalizedNumber
                        textSize = 14f
                        setTextColor(activity.getProperTextColor())
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }

                    val labelText = activity.getPhoneNumberTypeText(phoneNum.type, phoneNum.label)
                    val labelTextView = TextView(activity).apply {
                        text = if (labelText.isNotEmpty()) labelText else "Other"
                        textSize = 12f
                        setTextColor(activity.getProperTextColor().adjustAlpha(0.7f))
                    }

                    infoLayout.addView(numTextView)
                    infoLayout.addView(labelTextView)

                    val callIcon = ImageView(activity).apply {
                        setImageResource(R.drawable.ic_phone_vector)
                        val iconSize = (20 * resources.displayMetrics.density).toInt()
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                        setColorFilter(ContextCompat.getColor(activity, R.color.color_primary))
                    }

                    numRow.addView(infoLayout)
                    numRow.addView(callIcon)
                    contactNumbersList.addView(numRow, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (6 * resources.displayMetrics.density).toInt()
                    })
                }
            } else {
                contactNumbersSection.beGone()
            }

            // Action: Copy Number
            contactActionCopy.setOnClickListener {
                dialog.dismiss()
                if (primaryNumber.isNotEmpty()) {
                    activity.copyToClipboard(primaryNumber)
                }
            }

            // Action: WhatsApp Direct Chat (Modern & Advanced Mode)
            contactActionWhatsapp.setOnClickListener {
                dialog.dismiss()
                val cleanNumber = primaryNumber.filter { it.isDigit() }
                if (cleanNumber.isNotEmpty()) {
                    try {
                        val waIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
                            `package` = "com.whatsapp"
                        }
                        activity.startActivity(waIntent)
                    } catch (e: Exception) {
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber"))
                            activity.startActivity(browserIntent)
                        } catch (e2: Exception) {
                            activity.toast("Could not open WhatsApp")
                        }
                    }
                } else {
                    activity.toast("No valid number found")
                }
            }

            // Action: Share Contact
            contactActionShare.setOnClickListener {
                dialog.dismiss()
                val shareText = "${contact.getNameToDisplay()}: $primaryNumber"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                activity.startActivity(Intent.createChooser(shareIntent, "Share Contact"))
            }

            // Action: Share vCard (.vcf)
            contactActionVcard.setOnClickListener {
                dialog.dismiss()
                org.fossify.phone.helpers.QuickActionsHelper.shareVCard(activity, contact.getNameToDisplay(), primaryNumber)
            }

            // Note / Reminder Binding
            val currentNote = activity.config.getContactNote(primaryNumber)
            if (currentNote.isNotEmpty()) {
                contactNoteHolder.beVisible()
                contactNoteText.text = "📝 $currentNote"
                contactActionNote.text = "📝 Edit Note"
            } else {
                contactNoteHolder.beGone()
                contactActionNote.text = "📝 Add Note"
            }

            contactNoteClear.setOnClickListener {
                activity.config.setContactNote(primaryNumber, "")
                contactNoteHolder.beGone()
                contactActionNote.text = "📝 Add Note"
                notifyDataSetChanged()
            }

            contactActionNote.setOnClickListener {
                val input = android.widget.EditText(activity).apply {
                    hint = "e.g. Call back about project"
                    setText(activity.config.getContactNote(primaryNumber))
                    setSelection(text.length)
                    val pad = (16 * activity.resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                    setTextColor(activity.getProperTextColor())
                    setHintTextColor(activity.getProperTextColor().adjustAlpha(0.5f))
                }

                androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle("Note for ${contact.getNameToDisplay()}")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        val newNote = input.text.toString().trim()
                        activity.config.setContactNote(primaryNumber, newNote)
                        if (newNote.isNotEmpty()) {
                            contactNoteHolder.beVisible()
                            contactNoteText.text = "📝 $newNote"
                            contactActionNote.text = "📝 Edit Note"
                        } else {
                            contactNoteHolder.beGone()
                            contactActionNote.text = "📝 Add Note"
                        }
                        notifyDataSetChanged()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }

            // Block / Unblock Check
            activity.isContactBlocked(contact) { isBlocked ->
                activity.runOnUiThread {
                    if (isBlocked) {
                        contactActionBlock.text = activity.getString(R.string.unblock_contact)
                        contactActionBlock.setTextColor(ContextCompat.getColor(activity, R.color.color_primary))
                        contactActionBlock.setOnClickListener {
                            dialog.dismiss()
                            tryUnblocking(contact)
                        }
                    } else {
                        contactActionBlock.text = activity.getString(R.string.block_contact)
                        contactActionBlock.setTextColor(ContextCompat.getColor(activity, R.color.color_missed_call))
                        contactActionBlock.setOnClickListener {
                            dialog.dismiss()
                            tryBlocking(contact)
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun sendSMSToContact(contact: Contact) {
        val phoneNumber = contact.phoneNumbers.firstOrNull()?.normalizedNumber ?: return
        activity.launchSendSMSIntent(phoneNumber)
    }

    private fun showFullHistory(contact: Contact) {
        val phoneNumbers = contact.phoneNumbers.map { it.normalizedNumber }
        if (phoneNumbers.isEmpty()) {
            activity.toast(R.string.no_phone_number_found)
            return
        }

        ensureBackgroundThread {
            val recentsHelper = RecentsHelper(activity)
            recentsHelper.getRecentCalls { allCalls ->
                val contactCalls = allCalls.filter { call ->
                    phoneNumbers.any { number -> number == call.phoneNumber }
                }.sortedByDescending { it.startTS }

                activity.runOnUiThread {
                    showCallHistoryDialog(contact, contactCalls)
                }
            }
        }
    }

    private fun sendVideoCallLinkToContact(contact: Contact) {
        val phoneNumber = contact.phoneNumbers.firstOrNull()?.normalizedNumber
        if (phoneNumber == null) {
            activity.toast("No phone number available for this contact")
            return
        }

        VideoCallOptionsDialog(
            activity = activity as SimpleActivity,
            contactName = contact.getNameToDisplay(),
            contactNumber = phoneNumber
        )
    }

    private fun showCallHistoryDialog(contact: Contact, calls: List<RecentCall>) {
        if (activity.isDestroyed || activity.isFinishing) return
        val dialog = BottomSheetDialog(activity)

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.contact_bottom_sheet_bg)
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding)
        }

        // Top drag handle
        View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                (36 * resources.displayMetrics.density).toInt(),
                (4 * resources.displayMetrics.density).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * resources.displayMetrics.density).toInt()
            }
            setBackgroundResource(R.drawable.settings_chip_bg)
            alpha = 0.6f
            layout.addView(this)
        }

        // Title
        TextView(activity).apply {
            text = "${contact.getNameToDisplay()} • Call History"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(activity.getProperTextColor())
            val marginBottom = (14 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, 0, marginBottom)
            layout.addView(this)
        }

        if (calls.isEmpty()) {
            TextView(activity).apply {
                text = "No call history found"
                textSize = 15f
                setTextColor(activity.getProperTextColor().adjustAlpha(0.6f))
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(0, pad, 0, pad)
                layout.addView(this)
            }
        } else {
            calls.take(15).forEach { call ->
                val callView = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val padding = (12 * resources.displayMetrics.density).toInt()
                    setPadding(padding, padding, padding, padding)
                    setBackgroundResource(R.drawable.contact_action_pill_bg)
                    val marginBottom = (6 * resources.displayMetrics.density).toInt()
                    (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = marginBottom
                }

                val typeIcon = ImageView(activity).apply {
                    val iconRes = when (call.type) {
                        CallLog.Calls.OUTGOING_TYPE -> R.drawable.ic_call_made_vector
                        CallLog.Calls.INCOMING_TYPE -> R.drawable.ic_call_received_vector
                        CallLog.Calls.MISSED_TYPE -> R.drawable.ic_call_missed_vector
                        else -> R.drawable.ic_phone_vector
                    }
                    setImageResource(iconRes)
                    val tintColor = if (call.type == CallLog.Calls.MISSED_TYPE) {
                        ContextCompat.getColor(activity, R.color.color_missed_call)
                    } else {
                        ContextCompat.getColor(activity, R.color.color_primary)
                    }
                    setColorFilter(tintColor)
                    val iconSize = (20 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        marginEnd = (12 * resources.displayMetrics.density).toInt()
                    }
                }
                callView.addView(typeIcon)

                val detailsLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val callTypeText = when (call.type) {
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing Call"
                    CallLog.Calls.INCOMING_TYPE -> "Incoming Call"
                    CallLog.Calls.MISSED_TYPE -> "Missed Call"
                    else -> "Call"
                }

                TextView(activity).apply {
                    text = "$callTypeText • ${call.startTS.formatDateOrTime(activity, false, false, false)}"
                    textSize = 14f
                    setTextColor(activity.getProperTextColor())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    detailsLayout.addView(this)
                }

                if (call.duration > 0) {
                    TextView(activity).apply {
                        text = "Duration: ${activity.formatSecondsToShortTimeString(call.duration)}"
                        textSize = 12f
                        setTextColor(activity.getProperTextColor().adjustAlpha(0.7f))
                        detailsLayout.addView(this)
                    }
                }
                callView.addView(detailsLayout)

                layout.addView(callView, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (6 * resources.displayMetrics.density).toInt()
                })
            }
        }

        dialog.setContentView(layout)
        dialog.show()
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        activity.config.isCustomOrderSelected = true

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(contacts, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(contacts, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onRowClear(myViewHolder: ViewHolder?) {
        onDragEndListener?.invoke()
    }

    override fun zoomIn() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            val currentSpanCount = layoutManager.spanCount
            val newSpanCount = (currentSpanCount - 1).coerceIn(1, CONTACTS_GRID_MAX_COLUMNS_COUNT)
            layoutManager.spanCount = newSpanCount
            recyclerView.requestLayout()
            onSpanCountListener(newSpanCount)
        }
    }

    override fun zoomOut() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            val currentSpanCount = layoutManager.spanCount
            val newSpanCount = (currentSpanCount + 1).coerceIn(1, CONTACTS_GRID_MAX_COLUMNS_COUNT)
            layoutManager.spanCount = newSpanCount
            recyclerView.requestLayout()
            onSpanCountListener(newSpanCount)
        }
    }

    private sealed interface Binding {
        companion object {
            fun getByItemViewType(viewType: Int): Binding {
                return when (viewType) {
                    VIEW_TYPE_GRID -> ItemContactGrid
                    else -> ItemContact
                }
            }
        }

        fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding

        fun bind(view: View): ItemViewBinding

        data object ItemContactGrid : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemContactGridBindingAdapter(ItemContactWithoutNumberGridBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemContactGridBindingAdapter(ItemContactWithoutNumberGridBinding.bind(view))
            }
        }

        data object ItemContact : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemContactBindingAdapter(ItemContactWithoutNumberBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemContactBindingAdapter(ItemContactWithoutNumberBinding.bind(view))
            }
        }
    }

    private interface ItemViewBinding : ViewBinding {
        val itemContactName: TextView
        val itemContactImage: ImageView
        val itemContactFrame: ConstraintLayout
        val dragHandleIcon: ImageView
        val itemContactNumber: TextView? get() = null
        val itemContactCallButton: ImageView? get() = null
        val itemContactActionButton: ImageView? get() = null
    }

    private class ItemContactGridBindingAdapter(val binding: ItemContactWithoutNumberGridBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon
        override val itemContactNumber: TextView? = null
        override val itemContactCallButton: ImageView? = null
        override val itemContactActionButton = binding.itemContactActionButton

        override fun getRoot(): View = binding.root
    }

    private class ItemContactBindingAdapter(val binding: ItemContactWithoutNumberBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon
        override val itemContactNumber = binding.itemContactNumber
        override val itemContactCallButton = binding.itemContactCallButton
        override val itemContactActionButton = binding.itemContactActionButton

        override fun getRoot(): View = binding.root
    }
}
