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
import org.fossify.commons.databinding.ItemContactWithoutNumberBinding
import org.fossify.commons.databinding.ItemContactWithoutNumberGridBinding
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
    
    // Expandable section tracking
    private val expandedContacts = HashSet<Int>()


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
            
            // Apply gold border and pale yellow glass blur background
            itemContactFrame.apply {
                setBackgroundResource(R.drawable.contact_item_glass_background)
                val marginInDp = 8
                val marginInPx = (marginInDp * resources.displayMetrics.density).toInt()
                
                val topMargin = if (holder.bindingAdapterPosition == 0) {
                    marginInPx
                } else {
                    marginInPx / 2
                }
                
                val params = layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(marginInPx, topMargin, marginInPx, marginInPx / 2)
                layoutParams = params
                setPadding(marginInPx, marginInPx, marginInPx, marginInPx)
                elevation = 4f
                isSelected = selectedKeys.contains(contact.rawId)
                
                // Allow circular button to extend outside container
                clipToPadding = false
                clipChildren = false
            }

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
                    // Remove click listeners and background when disabled
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
                // Add animated gradient ring around profile photo
                itemContactImage.addAnimatedGradientRing()
            }
            
            // Add expandable section
            setupExpandableSection(binding, contact, holder)
        }
    }
    
    private fun setupExpandableSection(binding: ItemViewBinding, contact: Contact, holder: ViewHolder) {
        binding.apply {
            val isExpanded = expandedContacts.contains(contact.rawId)
            
            // Hide original profile image and name when expanded to avoid duplicates
            itemContactImage.visibility = if (isExpanded) View.GONE else View.VISIBLE
            itemContactName.visibility = if (isExpanded) View.GONE else View.VISIBLE
            
            // Remove ALL old expandable views to prevent recycling issues
            // This removes views from previously recycled items
            val viewsToRemove = mutableListOf<View>()
            for (i in 0 until itemContactFrame.childCount) {
                val child = itemContactFrame.getChildAt(i)
                val tag = child.tag as? String
                if (tag != null && (tag.startsWith("expand_button_") || 
                                    tag.startsWith("phone_number_") || 
                                    tag.startsWith("expanded_section_") ||
                                    tag.startsWith("expanded_content_"))) {
                    viewsToRemove.add(child)
                }
            }
            viewsToRemove.forEach { itemContactFrame.removeView(it) }
            
            // Create expand button - HALF IN/HALF OUT but FULLY VISIBLE
            val expandButton = ImageView(activity).apply {
                tag = "expand_button_${contact.rawId}"
                setImageResource(R.drawable.ic_expand_arrow)
                scaleType = ImageView.ScaleType.CENTER
                setBackgroundResource(R.drawable.expand_circle_gradient)
                elevation = 4f  // Subtle elevation, no dark shadow
                outlineProvider = null  // Remove shadow
                
                val size = (48 * resources.displayMetrics.density).toInt()
                layoutParams = ConstraintLayout.LayoutParams(size, size).apply {
                    // Position at TOP-RIGHT corner
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    topMargin = (12 * resources.displayMetrics.density).toInt()
                    marginEnd = (-size / 2)  // Half outside
                }
                
                setOnClickListener {
                    toggleExpanded(contact.rawId, holder)
                }
                
                // Set rotation based on state
                rotation = if (isExpanded) 180f else 0f
            }
            itemContactFrame.addView(expandButton)
            
            // Create expanded content section - only when expanded
            if (isExpanded) {
                // Create vertical layout for expanded content
                val expandedContent = LinearLayout(activity).apply {
                    tag = "expanded_content_${contact.rawId}"
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    
                    layoutParams = ConstraintLayout.LayoutParams(
                        0,  // Match constraint width
                        ConstraintLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        topMargin = (8 * resources.displayMetrics.density).toInt()
                        bottomMargin = (12 * resources.displayMetrics.density).toInt()
                    }
                    
                    val contentPadding = (12 * resources.displayMetrics.density).toInt()
                    setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
                }
                
                // Profile Header - Image + Name Horizontal Layout
                val profileHeader = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (8 * resources.displayMetrics.density).toInt()
                    }
                    
                    // Profile Image
                    val profileImage = ImageView(activity).apply {
                        val imageSize = (48 * resources.displayMetrics.density).toInt()
                        layoutParams = LinearLayout.LayoutParams(imageSize, imageSize).apply {
                            marginEnd = (12 * resources.displayMetrics.density).toInt()
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        
                        // Circular shape
                        clipToOutline = true
                        outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: Outline) {
                                outline.setOval(0, 0, view.width, view.height)
                            }
                        }
                        
                        // Load contact image
                        if (!activity.isDestroyed) {
                            SimpleContactsHelper(context).loadContactImage(
                                contact.photoUri,
                                this,
                                contact.getNameToDisplay()
                            )
                            // Add animated gradient ring
                            this.addAnimatedGradientRing()
                        }
                    }
                    addView(profileImage)
                    
                    // Profile Name
                    val profileName = TextView(activity).apply {
                        text = contact.getNameToDisplay()
                        textSize = 20f
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setTextColor(activity.getProperTextColor())
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    addView(profileName)
                }
                expandedContent.addView(profileHeader)
                
                // Phone Number - Left-aligned, Below Name
                val phoneNumberText = TextView(activity).apply {
                    tag = "phone_number_${contact.rawId}"
                    val primaryNumber = contact.phoneNumbers.firstOrNull()?.normalizedNumber ?: ""
                    text = primaryNumber
                    textSize = 14f
                    gravity = Gravity.START
                    setTextColor(textColor.adjustAlpha(0.75f))
                    
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (16 * resources.displayMetrics.density).toInt()
                        marginStart = (60 * resources.displayMetrics.density).toInt()  // Align with name
                    }
                }
                expandedContent.addView(phoneNumberText)
            
                // Buttons Container - Side by Side
                val buttonsContainer = LinearLayout(activity).apply {
                    tag = "expanded_section_${contact.rawId}"
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (8 * resources.displayMetrics.density).toInt()
                    }
                    
                    // SMS Button - Moderate Size
                    val smsButton = TextView(activity).apply {
                        text = "💬\nSMS"
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setTextColor(activity.getProperTextColor())
                        setBackgroundResource(R.drawable.action_button_background)
                        val btnPadding = (12 * resources.displayMetrics.density).toInt()
                        setPadding(btnPadding, btnPadding, btnPadding, btnPadding)
                        minHeight = (50 * resources.displayMetrics.density).toInt()
                        elevation = 2f
                        
                        setOnClickListener {
                            sendSMSToContact(contact)
                        }
                    }
                    
                    // Full History Button - Moderate Size
                    val historyButton = TextView(activity).apply {
                        text = "📜\nHistory"
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setTextColor(activity.getProperTextColor())
                        setBackgroundResource(R.drawable.action_button_background)
                        val btnPadding = (12 * resources.displayMetrics.density).toInt()
                        setPadding(btnPadding, btnPadding, btnPadding, btnPadding)
                        minHeight = (50 * resources.displayMetrics.density).toInt()
                        elevation = 2f
                        
                        setOnClickListener {
                            showFullHistory(contact)
                        }
                    }
                    
                    // Video Call Button - Moderate Size
                    val videoCallButton = TextView(activity).apply {
                        text = "📹\nVideo"
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setTextColor(activity.getProperTextColor())
                        setBackgroundResource(R.drawable.action_button_background)
                        val btnPadding = (12 * resources.displayMetrics.density).toInt()
                        setPadding(btnPadding, btnPadding, btnPadding, btnPadding)
                        minHeight = (50 * resources.displayMetrics.density).toInt()
                        elevation = 2f
                        
                        setOnClickListener {
                            sendVideoCallLinkToContact(contact)
                        }
                    }
                    
                    // Add buttons with equal weight
                    addView(smsButton, LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f  // Equal width
                    ))
                    
                    addView(historyButton, LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f  // Equal width
                    ).apply {
                        marginStart = (8 * resources.displayMetrics.density).toInt()
                    })
                    
                    addView(videoCallButton, LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f  // Equal width
                    ).apply {
                        marginStart = (8 * resources.displayMetrics.density).toInt()
                    })
                }
                expandedContent.addView(buttonsContainer)
                
                itemContactFrame.addView(expandedContent)
            }
        }
    }
    
    private fun toggleExpanded(contactId: Int, holder: ViewHolder) {
        if (expandedContacts.contains(contactId)) {
            expandedContacts.remove(contactId)
        } else {
            expandedContacts.add(contactId)
        }
        notifyItemChanged(holder.bindingAdapterPosition)
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
        
        // Fetch call history for this contact
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
        
        val contactName = contact.getNameToDisplay()
        
        // Show video call options dialog (same as in CallActivity)
        VideoCallOptionsDialog(
            activity = activity as SimpleActivity,
            contactName = contactName,
            contactNumber = phoneNumber
        )
    }
    
    private fun showCallHistoryDialog(contact: Contact, calls: List<RecentCall>) {
        val dialog = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
        
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        
        // Title
        TextView(activity).apply {
            text = "${contact.getNameToDisplay()} - Call History"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(activity.getProperTextColor())
            val marginBottom = (16 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, 0, marginBottom)
            layout.addView(this)
        }
        
        if (calls.isEmpty()) {
            TextView(activity).apply {
                text = "No call history found"
                textSize = 16f
                setTextColor(activity.getProperTextColor())
                layout.addView(this)
            }
        } else {
            calls.forEach { call ->
                val callView = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    val padding = (12 * resources.displayMetrics.density).toInt()
                    setPadding(padding, padding, padding, padding)
                    setBackgroundResource(R.drawable.action_button_background)
                    val marginBottom = (8 * resources.displayMetrics.density).toInt()
                    (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = marginBottom
                }
                
                val callType = when (call.type) {
                    CallLog.Calls.OUTGOING_TYPE -> "📞 Outgoing"
                    CallLog.Calls.INCOMING_TYPE -> "📱 Incoming"
                    CallLog.Calls.MISSED_TYPE -> "❌ Missed"
                    else -> "📞 Call"
                }
                
                TextView(activity).apply {
                    text = "$callType - ${call.startTS.formatDateOrTime(activity, false, false, false)}"
                    textSize = 16f
                    setTextColor(activity.getProperTextColor())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    callView.addView(this)
                }
                
                if (call.duration > 0) {
                    TextView(activity).apply {
                        text = "Duration: ${activity.formatSecondsToShortTimeString(call.duration)}"
                        textSize = 14f
                        setTextColor(activity.getProperTextColor())
                        callView.addView(this)
                    }
                }
                
                layout.addView(callView, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (8 * resources.displayMetrics.density).toInt()
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
    }

    private class ItemContactGridBindingAdapter(val binding: ItemContactWithoutNumberGridBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon

        override fun getRoot(): View = binding.root
    }

    private class ItemContactBindingAdapter(val binding: ItemContactWithoutNumberBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon

        override fun getRoot(): View = binding.root
    }
}
