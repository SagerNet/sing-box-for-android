package io.nekohasekai.sfa.ui.dashboard



import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.databinding.FragmentDashboardOverviewBinding
import io.nekohasekai.sfa.databinding.ViewProfileItemBinding
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ui.MainActivity
import io.nekohasekai.sfa.utils.CommandClient
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class OverviewFragment : Fragment() {

    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/rtlvpn/junk/main/"
    }
    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var binding: FragmentDashboardOverviewBinding? = null
    private val statusClient =
        CommandClient(lifecycleScope, CommandClient.ConnectionType.Status, StatusClient())
//    private val clashModeClient =
//        CommandClient(lifecycleScope, CommandClient.ConnectionType.ClashMode, ClashModeClient())
    private lateinit var spinnerAdapter: SpinnerAdapter
    private var adapter: Adapter? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentDashboardOverviewBinding.inflate(inflater, container, false)
        this.binding = binding
        onCreate()
        return binding.root
    }

    private fun onCreate() {
        val activity = activity ?: return
        val binding = binding ?: return
        binding.profileList.adapter = Adapter(lifecycleScope, binding).apply {
            adapter = this
            reload()
        }
        spinnerAdapter = SpinnerAdapter(lifecycleScope, binding.spinner2)
        val addButton = binding.button
        addButton.setOnClickListener {
            showCreateProfileDialog()
        }
        binding.profileList.layoutManager = LinearLayoutManager(requireContext())
        val divider = MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL)
        divider.isLastItemDecorated = false
        binding.profileList.addItemDecoration(divider)
        activity.serviceStatus.observe(viewLifecycleOwner) {
            binding.statusContainer.isVisible = it == Status.Starting || it == Status.Started
            when (it) {
                Status.Stopped -> {
//                    binding.clashModeCard.isVisible = false
//                    binding.systemProxyCard.isVisible = false
//                    binding.lottieButton.setAnimation("off.json")
                    binding.lottieButtonanim.setBooleanState("State Machine 1", "on", false)
                    binding.lottieButtonanim.setBooleanState("State Machine 1", "onning", false)


                }

                Status.Starting -> {
//                    binding.lottieButton.setAnimation("off.json")
//                    binding.lottieButton.playAnimation()
                    binding.lottieButtonanim.setBooleanState("State Machine 1", "on", false)
                    binding.lottieButtonanim.setBooleanState("State Machine 1", "onning", true)
                }

                Status.Started -> {
                    statusClient.connect()
//                    clashModeClient.connect()
                    reloadSystemProxyStatus()
//                    binding.lottieButton.setAnimation("on.json")
//                    binding.lottieButton.playAnimation()
                    binding.lottieButtonanim.setBooleanState("State Machine 1", "on", true)
                    binding.lottieButtonanim.setBooleanState("State Machine 1", "onning", false)
                }

                Status.Stopping -> {
//                    binding.lottieButton.setAnimation("off.json")
//                    binding.lottieButton.pauseAnimation()
                    binding.lottieButtonanim.setBooleanState("State Machine 1", "on", false)
                    binding.lottieButtonanim.setBooleanState("State Machine 1", "onning", false)


                }

                else -> {}
            }
        }
        binding.lottieButton.setOnClickListener {
            if (binding.spinner2.adapter.isEmpty) {
                // Show your existing alert dialog that takes a user key to create a profile
                showCreateProfileDialog()
            } else {
            when (activity.serviceStatus.value) {
                Status.Stopped -> {
                    val savedTimestamp = getProfileTimestamp()
                    val currentTime = System.currentTimeMillis()
                    val twoDaysInMillis = 2 * 24 * 60 * 60 * 1000 // 2 days in

                    if (savedTimestamp > System.currentTimeMillis()) {
                        activity.startService()
                        if (savedTimestamp - currentTime < twoDaysInMillis) {
                        // Show a warning dialog that the key will expire soon
                        val hoursLeft = (savedTimestamp - currentTime) / (60 * 60 * 1000)
                        AlertDialog.Builder(activity)
                            .setTitle("Key Expiring Soon")
                            .setMessage("Your key will expire in approximately $hoursLeft hours. Please renew soon.")
                            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                            .show()
                    }
                    
                    }else {
                        // Show an alert dialog that the key is expired
                        AlertDialog.Builder(activity)
                            .setTitle("Key expired")
                            .setMessage("The key for profile has expired.")
                            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                            .show()
                    }
                }

                Status.Started -> {
                    val sharedPref = requireActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                    val androidId = sharedPref.getString("android_id", null)
                    if (androidId != null) {
                        val selectedProfileName = spinnerAdapter.getSelectedSpinnerItemText()
                        if (selectedProfileName != null) {
                            binding.lottieButton.isEnabled = false

                            try {
                                val downlinkTotalBytes = convertToBytes(binding.downlinkTotalText.text.toString())
                                val uplinkTotalBytes = convertToBytes(binding.uplinkTotalText.text.toString())
                                val usageSum = downlinkTotalBytes + uplinkTotalBytes

                                // Storing data in SharedPreferences
                                with(sharedPref.edit()) {
                                    putLong("$selectedProfileName-usage", usageSum + sharedPref.getLong("$selectedProfileName-usage", 0))
                                    apply()
                                }
                                Log.d("SharedPref", "Usage data successfully written!")
                                // Proceed with stopping the BoxService
                                binding.lottieButton.isEnabled = true
                                BoxService.stop()
                            } catch (e: Exception) {
                                Log.e("OverviewFragment", "Error converting data sizes: ${e.message}")
                                // If there's an exception before Firestore operation, stop BoxService here
                                binding.lottieButton.isEnabled = true
                                BoxService.stop()
                            }
                        } else {
                            // If selectedProfileName is null, stop BoxService
                            BoxService.stop()
                        }
                    } else {
                        // If androidId is null, stop BoxService
                        BoxService.stop()
                    }
                }

                else -> {
                    BoxService.stop()

                }
                }
            }
        }
        ProfileManager.registerCallback(this::updateProfiles)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
        binding = null
        statusClient.disconnect()
//        clashModeClient.disconnect()
        ProfileManager.unregisterCallback(this::updateProfiles)
    }
    private fun convertToBytes(dataSize: String): Long {
        val number = dataSize.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
        return when {
            dataSize.contains("TB", ignoreCase = true) -> (number * 1024 * 1024 * 1024 * 1024).toLong()
            dataSize.contains("GB", ignoreCase = true) -> (number * 1024 * 1024 * 1024).toLong()
            dataSize.contains("MB", ignoreCase = true) -> (number * 1024 * 1024).toLong()
            dataSize.contains("kB", ignoreCase = true) -> (number * 1024).toLong()
            else -> number.toLong() // Assuming the value is already in bytes if no unit is specified
        }
    }
    private fun updateProfiles() {
        adapter?.reload()
    }

    private fun getProfileTimestamp(profile: Profile? = null): Long {
        val profileName = profile?.name ?: spinnerAdapter.getSelectedSpinnerItemText()
        val sharedPref = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        return sharedPref.getLong(profileName + "stamp", 0L)
    }

    private fun reloadSystemProxyStatus() {
        val binding = binding ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val status = Libbox.newStandaloneCommandClient().systemProxyStatus
            withContext(Dispatchers.Main) {
                binding.systemProxyCard.isVisible = status.available
                binding.systemProxySwitch.setOnClickListener(null)
                binding.systemProxySwitch.isChecked = status.enabled
                binding.systemProxySwitch.isEnabled = true
                binding.systemProxySwitch.setOnCheckedChangeListener { buttonView, isChecked ->
                    binding.systemProxySwitch.isEnabled = false
                    lifecycleScope.launch(Dispatchers.IO) {
                        Settings.systemProxyEnabled = isChecked
                        runCatching {
                            Libbox.newStandaloneCommandClient().setSystemProxyEnabled(isChecked)
                        }.onFailure {
                            buttonView.context.errorDialogBuilder(it).show()
                        }
                    }
                }
            }
        }
    }

    inner class StatusClient : CommandClient.Handler {

        override fun onConnected() {
            val binding = binding ?: return
            lifecycleScope.launch(Dispatchers.Main) {
                binding.memoryText.text = getString(R.string.loading)
                binding.goroutinesText.text = getString(R.string.loading)
            }
        }

        override fun onDisconnected() {
            val binding = binding ?: return
            lifecycleScope.launch(Dispatchers.Main) {
                binding.memoryText.text = getString(R.string.loading)
                binding.goroutinesText.text = getString(R.string.loading)
            }
        }

        override fun updateStatus(status: StatusMessage) {
            val binding = binding ?: return
            lifecycleScope.launch(Dispatchers.Main) {
                binding.memoryText.text = Libbox.formatBytes(status.memory)
                binding.goroutinesText.text = status.goroutines.toString()
                val trafficAvailable = status.trafficAvailable
                binding.trafficContainer.isVisible = trafficAvailable
                if (trafficAvailable) {
                    binding.inboundConnectionsText.text = status.connectionsIn.toString()
                    binding.outboundConnectionsText.text = status.connectionsOut.toString()
                    binding.uplinkText.text = Libbox.formatBytes(status.uplink) + "/s"
                    binding.downlinkText.text = Libbox.formatBytes(status.downlink) + "/s"
                    binding.uplinkTotalText.text = Libbox.formatBytes(status.uplinkTotal)
                    binding.downlinkTotalText.text = Libbox.formatBytes(status.downlinkTotal)

                }
            }
        }

    }

//    inner class ClashModeClient : CommandClient.Handler {
//
//        override fun initializeClashMode(modeList: List<String>, currentMode: String) {
//            val binding = binding ?: return
//            if (modeList.size > 1) {
//                lifecycleScope.launch(Dispatchers.Main) {
//                    binding.clashModeCard.isVisible = true
//                    binding.clashModeList.adapter = ClashModeAdapter(modeList, currentMode)
//                    binding.clashModeList.layoutManager =
//                        GridLayoutManager(
//                            requireContext(),
//                            if (modeList.size < 3) modeList.size else 3
//                        )
//                }
//            } else {
//                lifecycleScope.launch(Dispatchers.Main) {
//                    binding.clashModeCard.isVisible = false
//                }
//            }
//        }
//
//        @SuppressLint("NotifyDataSetChanged")
//        override fun updateClashMode(newMode: String) {
//            val binding = binding ?: return
//            val adapter = binding.clashModeList.adapter as? ClashModeAdapter ?: return
//            adapter.selected = newMode
//            lifecycleScope.launch(Dispatchers.Main) {
//                adapter.notifyDataSetChanged()
//            }
//        }
//
//    }

//    private inner class ClashModeAdapter(
//        val items: List<String>,
//        var selected: String
//    ) :
//        RecyclerView.Adapter<ClashModeItemView>() {
//        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClashModeItemView {
//            val view = ClashModeItemView(
//                ViewClashModeButtonBinding.inflate(
//                    LayoutInflater.from(parent.context),
//                    parent,
//                    false
//                )
//            )
//            view.binding.clashModeButton.clipToOutline = true
//            return view
//        }
//
//        override fun getItemCount(): Int {
//            return items.size
//        }
//
//        override fun onBindViewHolder(holder: ClashModeItemView, position: Int) {
//            holder.bind(items[position], selected)
//        }
//    }

//    private inner class ClashModeItemView(val binding: ViewClashModeButtonBinding) :
//        RecyclerView.ViewHolder(binding.root) {
//
//        fun bind(item: String, selected: String) {
//            binding.clashModeButtonText.text = item
//            if (item != selected) {
//                binding.clashModeButtonText.setTextColor(
//                    binding.root.context.getAttrColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
//                )
//                binding.clashModeButton.setBackgroundResource(R.drawable.bg_rounded_rectangle)
//                binding.clashModeButton.setOnClickListener {
//                    runCatching {
//                        Libbox.newStandaloneCommandClient().setClashMode(item)
//                        clashModeClient.connect()
//                    }.onFailure {
//                        GlobalScope.launch(Dispatchers.Main) {
//                            binding.root.context.errorDialogBuilder(it).show()
//                        }
//                    }
//                }
//            } else {
//                binding.clashModeButtonText.setTextColor(
//                    binding.root.context.getAttrColor(com.google.android.material.R.attr.colorOnPrimary)
//                )
//                binding.clashModeButton.setBackgroundResource(R.drawable.bg_rounded_rectangle_active)
//                binding.clashModeButton.isClickable = false
//            }
//
//        }
//    }

    class SpinnerAdapter(
        internal val scope: CoroutineScope,
        internal val spinner: Spinner
    ) {
        internal var items: MutableList<Profile> = mutableListOf()
        internal var selectedProfileID = -1L
        internal var lastSelectedIndex: Int? = null
        internal val spinnerAdapter: ArrayAdapter<Profile>
        private val holder: SpinnerHolder

        init {
            spinnerAdapter = object : ArrayAdapter<Profile>(spinner.context, android.R.layout.simple_spinner_item, items) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.text = getItem(position)?.name
                    return view
                }

                override fun getDropDownView(position: Int, view: View?, parent: ViewGroup): View {
                    val inflater = LayoutInflater.from(parent.context)
                    val dropdownView = view ?: inflater.inflate(R.layout.spinner_dropdown_item, parent, false)

                    val textView = dropdownView.findViewById<TextView>(R.id.spinner_text)
                    val removeButton = dropdownView.findViewById<ImageButton>(R.id.remove_button)

                    val item = getItem(position)
                    textView.text = item?.name

                    removeButton.isFocusable = false

                    removeButton.setOnClickListener {
                        // Remove the item from your data set and update the spinner.
                        val removedProfile = items.removeAt(position)
                        notifyDataSetChanged()

                        // If the removed item was the selected item, select the first item instead.
                        if (removedProfile.id == selectedProfileID) {
                            selectedProfileID = items.firstOrNull()?.id ?: -1L
                            lastSelectedIndex = items.indices.firstOrNull()
                            Settings.selectedProfile = selectedProfileID
                        }

                        // Update the spinner selection.
                        // Delete the item from the database on a background thread.
                        scope.launch(Dispatchers.IO) {
                            ProfileManager.delete(removedProfile)

                            // Update the spinner selection.
                            withContext(Dispatchers.Main) {
                                reload()
                            }
                        }
                    }

                    return dropdownView
                }
            }
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = spinnerAdapter
            holder = SpinnerHolder(this, spinner)
            reload()
        }
        fun getSelectedSpinnerItemText(): String? {
            val selectedProfile = items.find { it.id == selectedProfileID }
            return selectedProfile?.name
        }
        internal fun reload() {
            scope.launch(Dispatchers.IO) {
                items = ProfileManager.list().toMutableList()
                if (items.isNotEmpty()) {
                    selectedProfileID = Settings.selectedProfile
                    for ((index, profile) in items.withIndex()) {
                        if (profile.id == selectedProfileID) {
                            lastSelectedIndex = index
                            break
                        }
                    }
                    if (lastSelectedIndex == null) {
                        lastSelectedIndex = 0
                        selectedProfileID = items[0].id
                        Settings.selectedProfile = selectedProfileID
                    }
                }
                withContext(Dispatchers.Main) {
                    spinnerAdapter.clear()
                    if (items.isNotEmpty()) {
                        spinnerAdapter.addAll(items)
                        spinnerAdapter.notifyDataSetChanged()
                        holder.bind(items[lastSelectedIndex ?: 0])
                    }
                }
            }
        }
    }
    class SpinnerHolder(
        private val adapter: SpinnerAdapter,
        private val spinner: Spinner
    ) {
        internal fun bind(profile: Profile) {
            spinner.onItemSelectedListener = null
            spinner.setSelection(adapter.items.indexOf(profile))
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    val selectedProfile = adapter.spinnerAdapter.getItem(position)
                    if (selectedProfile != null) {
                        val sharedPref = spinner.context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                        val profileTimestamp = sharedPref.getLong("${selectedProfile.name}stamp", 0L)
                        Log.d("SpinnerHolder", "Profile Timestamp: $profileTimestamp")
                        if (profileTimestamp < System.currentTimeMillis()) {
                            // Profile is expired, show alert and revert selection
                            AlertDialog.Builder(spinner.context)
                                .setTitle("Profile expired")
                                .setMessage("The profile ${selectedProfile.name} has expired.")
                                .setPositiveButton("OK") { dialog, _ ->
                                    dialog.dismiss()
                                    // Revert to the last valid selection
                                    spinner.setSelection(adapter.lastSelectedIndex ?: 0)
                                }
                                .show()
                        } else {
                            // Profile is not expired, proceed with switch
                            if (selectedProfile.id != adapter.selectedProfileID) {
                                adapter.selectedProfileID = selectedProfile.id
                                adapter.lastSelectedIndex = position
                                adapter.scope.launch(Dispatchers.IO) {
                                    switchProfile(selectedProfile)
                                }
                            }
                        }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>) {
                    // Handle the case where nothing is selected
                }
            }
        }
        private suspend fun switchProfile(profile: Profile) {

            Settings.selectedProfile = profile.id
            val mainActivity = (spinner.context as? MainActivity) ?: return
            val started = mainActivity.serviceStatus.value == Status.Started
            if (!started) {
                return
            }
            val restart = Settings.rebuildServiceMode()
            if (restart) {
                mainActivity.reconnect()
                BoxService.stop()
                delay(1000L)
                mainActivity.startService()
                return
            }
            runCatching {
                Libbox.newStandaloneCommandClient().serviceReload()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    mainActivity.errorDialogBuilder(it).show()
                }
            }
        }
    }
        private fun showCreateProfileDialog() {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.your_dialog_layout, null)
            val urlInput = dialogView.findViewById<TextInputLayout>(R.id.your_url_input)
            val addButton = dialogView.findViewById<Button>(R.id.your_add_button)
            val cancelButton = dialogView.findViewById<Button>(R.id.your_cancel_button)
            context?.let { it1 ->
                val dialog = AlertDialog.Builder(it1)
                    .setView(dialogView)
    //                .setPositiveButton("Add", null) // Set to null for now
    //                .setNegativeButton("Cancel", null)
                    .create()
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                dialog.setOnShowListener {
    //                val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    addButton.setOnClickListener {
                        var secretText = urlInput.editText?.text.toString()
                        if (secretText.isNotBlank()) {
                            secretText = secretText.replace("\\s".toRegex(), "") // Remove whitespaces
                            secretText = secretText.replace("\\d".toRegex(), "")
                            secretText = secretText.replace("\\W".toRegex(), "")
                            lifecycleScope.launch(Dispatchers.IO) {
                                val md5Hash = md5(secretText)
                                val fileURL = "$BASE_URL$md5Hash"
                                val url = URL(fileURL)
                                val connection = url.openConnection() as HttpURLConnection
                                connection.requestMethod = "HEAD"

                                val fileExists: Boolean = connection.responseCode == HttpURLConnection.HTTP_OK


                                if (fileExists) {
                                    val encryptedContent = HTTPClient().use { it.getString(fileURL) }
                                    val keySpec = SecretKeySpec(secretText.toByteArray(), "AES")
                                    val json = JSONObject(encryptedContent)
                                    val iv = Base64.decode(json.getString("iv"), Base64.DEFAULT)
                                    val ct = Base64.decode(json.getString("ciphertext"), Base64.DEFAULT)
                                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                                    val ivSpec = IvParameterSpec(iv)
                                    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                                    val decryptedContent = String(cipher.doFinal(ct))
                                    // Extract timestamp from the end of decrypted content
                                    val timestampRegex = "(?<=//)\\d{13}(?=$)".toRegex()
                                    val timestampStr = timestampRegex.find(decryptedContent)?.value
                                    if (timestampStr != null) {
                                        val timestamp = timestampStr.toLong()
                                        if (timestamp < System.currentTimeMillis()) {
                                            // If timestamp is in the past, show error and stop operation
                                            withContext(Dispatchers.Main) {
                                                urlInput.error = "The Key has expired"
                                            }
                                        } else {
                                            // Continue with your existing code...
                                            val sharedPref = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                                            with(sharedPref.edit()) {
                                                putLong("${secretText}stamp", timestamp)
                                                apply()
                                            }

                                            val typedProfile = TypedProfile()
                                            typedProfile.type = TypedProfile.Type.Remote // Set the type to remote
                                            val profile = Profile(name = secretText, typed = typedProfile)
                                            profile.userOrder = ProfileManager.nextOrder()
                                            val fileID = ProfileManager.nextFileID()
                                            val configDirectory = File(requireContext().filesDir, "configs").also { it.mkdirs() }
                                            val configFile = File(configDirectory, "$fileID.json")
                                            typedProfile.path = configFile.path
                                            configFile.writeText(decryptedContent)
                                            typedProfile.remoteURL = fileURL
                                            typedProfile.lastUpdated = Date()
                                            typedProfile.autoUpdate = true
                                            typedProfile.autoUpdateInterval = 15
                                            ProfileManager.create(profile)
                                            spinnerAdapter.reload()
                                            dialog.dismiss()
                                        }

                                    }else {
                                        // Handle the case where the timestamp is not found
                                        withContext(Dispatchers.Main) {
                                            urlInput.error = "The Key is Correct but Has Damaged"
                                        }
                                    }
//                                    val typedProfile = TypedProfile()
//                                    typedProfile.type = TypedProfile.Type.Remote // Set the type to remote
//                                    val profile = Profile(name = secretText, typed = typedProfile)
//                                    profile.userOrder = ProfileManager.nextOrder()
//                                    val fileID = ProfileManager.nextFileID()
//                                    val configDirectory = File(requireContext().filesDir, "configs").also { it.mkdirs() }
//                                    val configFile = File(configDirectory, "$fileID.json")
//                                    typedProfile.path = configFile.path
//                                    configFile.writeText(decryptedContent)
//                                    typedProfile.remoteURL = fileURL
//                                    typedProfile.lastUpdated = Date()
//                                    typedProfile.autoUpdate = true
//                                    typedProfile.autoUpdateInterval = 15
//                                    ProfileManager.create(profile)
//                                    spinnerAdapter.reload()
//                                    dialog.dismiss()
                                } else {
                                    // Handle the case where the file does not exist
                                    withContext(Dispatchers.Main) {
                                        urlInput.error = "Key does not exist"
                                    }
                                }
                            }
                        } else {
                            // Show error: Secret text cannot be blank
                            urlInput.error = "Secret text cannot be blank"
                        }
                    }
                    cancelButton.setOnClickListener {
                        dialog.dismiss()
                    }
                }
                dialog.show()
        }
    }
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.fold("", { str, it -> str + "%02x".format(it) })
    }
    class Adapter(
        internal val scope: CoroutineScope,
        internal val parent: FragmentDashboardOverviewBinding
    ) :
        RecyclerView.Adapter<Holder>() {

        internal var items: MutableList<Profile> = mutableListOf()
        internal var selectedProfileID = -1L
        internal var lastSelectedIndex: Int? = null
        internal fun reload() {
            scope.launch(Dispatchers.IO) {
                items = ProfileManager.list().toMutableList()
                if (items.isNotEmpty()) {
                    selectedProfileID = Settings.selectedProfile
                    for ((index, profile) in items.withIndex()) {
                        if (profile.id == selectedProfileID) {
                            lastSelectedIndex = index
                            break
                        }
                    }
                    if (lastSelectedIndex == null) {
                        lastSelectedIndex = 0
                        selectedProfileID = items[0].id
                        Settings.selectedProfile = selectedProfileID
                    }
                }
                withContext(Dispatchers.Main) {
                    parent.statusText.isVisible = items.isEmpty()
//                    parent.container.isVisible = items.isNotEmpty()
                    notifyDataSetChanged()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                this,
                ViewProfileItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int {
            return items.size
        }

    }

    class Holder(
        private val adapter: Adapter,
        private val binding: ViewProfileItemBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {

        internal fun bind(profile: Profile) {
            binding.profileName.text = profile.name
            binding.profileSelected.setOnCheckedChangeListener(null)
            binding.profileSelected.isChecked = profile.id == adapter.selectedProfileID
            binding.profileSelected.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    adapter.parent.profileList.isClickable = false
                    adapter.selectedProfileID = profile.id
                    adapter.lastSelectedIndex?.let { index ->
                        adapter.notifyItemChanged(index)
                    }
                    adapter.lastSelectedIndex = adapterPosition
                    adapter.scope.launch(Dispatchers.IO) {
                        switchProfile(profile)
                        withContext(Dispatchers.Main) {
                            adapter.parent.profileList.isEnabled = true
                        }
                    }
                }
            }
            binding.root.setOnClickListener {
                binding.profileSelected.toggle()
            }
        }

        private suspend fun switchProfile(profile: Profile) {
            Settings.selectedProfile = profile.id
            val mainActivity = (binding.root.context as? MainActivity) ?: return
            val started = mainActivity.serviceStatus.value == Status.Started
            if (!started) {
                return
            }
            val restart = Settings.rebuildServiceMode()
            if (restart) {
                mainActivity.reconnect()
                BoxService.stop()
                delay(1000L)
                mainActivity.startService()
                return
            }
            runCatching {
                Libbox.newStandaloneCommandClient().serviceReload()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    mainActivity.errorDialogBuilder(it).show()
                }
            }
        }
    }

}