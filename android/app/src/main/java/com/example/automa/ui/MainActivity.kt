package com.example.automa.ui

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.automa.R
import com.example.automa.accessibility.AutomationAccessibilityService
import com.example.automa.clipboard.AutomaClipboardProvider
import com.example.automa.engine.*
import com.example.automa.model.*
import com.example.automa.overlay.OverlayService
import com.example.automa.prefs.Prefs
import com.example.automa.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var recorder: MacroRecorder
    private lateinit var player: MacroPlayer
    private lateinit var scriptEngine: ScriptEngine
    private lateinit var recognition: RecognitionEngine
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var macroAdapter: MacroAdapter
    private var pickedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbarTitle.text = getString(R.string.app_name)

        binding.navView.setNavigationItemSelectedListener(this)

        prefs = Prefs(this)
        val acc = AutomationAccessibilityService.instance
        recorder = MacroRecorder(this, prefs)
        player = MacroPlayer(acc)
        scriptEngine = ScriptEngine(this, acc)
        recognition = RecognitionEngine(acc)

        recorder.onStateChanged = { recording ->
            runOnUiThread {
                if (recording) toast(getString(R.string.toast_recording_started))
                else toast(getString(R.string.toast_recording_stopped))
                updateChips()
            }
        }
        player.onFinished = { completed ->
            runOnUiThread {
                binding.btnPlay.isEnabled = true
                binding.btnStop.visibility = View.GONE
                updateChips()
                toast(if (completed) getString(R.string.toast_playback_finished)
                else "Playback stopped")
            }
        }

        binding.btnRecord.setOnClickListener { onRecordClicked() }
        binding.btnPlay.setOnClickListener { onPlayClicked() }
        binding.btnStop.setOnClickListener { onStopClicked() }

        updateChips()
        showSection(R.id.nav_dashboard)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> showSection(R.id.nav_dashboard)
            R.id.nav_phone -> showSection(R.id.nav_phone)
            R.id.nav_macros -> showSection(R.id.nav_macros)
            R.id.nav_scripts -> showSection(R.id.nav_scripts)
            R.id.nav_recognition -> showSection(R.id.nav_recognition)
            R.id.nav_keyboard -> showSection(R.id.nav_keyboard)
            R.id.nav_clipboard -> showSection(R.id.nav_clipboard)
            R.id.nav_settings -> showSection(R.id.nav_settings)
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showSection(id: Int) {
        binding.sectionsHost.removeAllViews()
        val view = when (id) {
            R.id.nav_dashboard -> buildDashboard()
            R.id.nav_phone -> buildPhone()
            R.id.nav_macros -> buildMacros()
            R.id.nav_scripts -> buildScripts()
            R.id.nav_recognition -> buildRecognition()
            R.id.nav_keyboard -> buildKeyboard()
            R.id.nav_clipboard -> buildClipboard()
            R.id.nav_settings -> buildSettings()
            else -> buildDashboard()
        }
        binding.sectionsHost.addView(view)
        binding.toolbarTitle.text = when (id) {
            R.id.nav_dashboard -> getString(R.string.nav_dashboard)
            R.id.nav_phone -> "Phone Screen"
            R.id.nav_macros -> getString(R.string.nav_macros)
            R.id.nav_scripts -> getString(R.string.nav_scripts)
            R.id.nav_recognition -> getString(R.string.nav_recognition)
            R.id.nav_keyboard -> getString(R.string.nav_keyboard)
            R.id.nav_clipboard -> getString(R.string.nav_clipboard)
            R.id.nav_settings -> getString(R.string.nav_settings)
            else -> getString(R.string.app_name)
        }
    }

    fun updateChips() {
        binding.chipOverlay.text = "OVERLAY: ${if (OverlayService.isShowing) "on" else "off"}"
        binding.chipClip.text = "CLIPBOARD: ${prefs.getClipboard().size}"
        binding.chipScript.text = "SCRIPT: ${if (scriptEngine.isRunning()) "running" else "ready"}"
    }

    // ---------- record / play / stop ----------
    private fun onRecordClicked() {
        if (!isAccessibilityEnabled()) { requestAccessibility(); return }
        if (recorder.isRecording()) {
            recorder.stopRecording()
            AutomationAccessibilityService.instance?.setRecording(false)
            binding.btnStop.visibility = View.GONE
        } else {
            val name = promptSimpleInput("New macro name", "Untitled macro")
            recorder.startRecording(if (name.isNotEmpty()) name else "Untitled macro")
            AutomationAccessibilityService.instance?.setRecording(true)
            AutomationAccessibilityService.instance?.setRecorderStepListener { step ->
                when (step.t) {
                    "click" -> recorder.recordClick(step.target ?: "", step.x, step.y)
                    "type" -> recorder.recordType(step.text ?: "")
                    "press" -> recorder.recordPress(step.key ?: "back")
                }
            }
            binding.btnStop.visibility = View.VISIBLE
        }
        updateChips()
    }

    private fun onPlayClicked() {
        val m = macroAdapter.selected
        if (m == null) { toast(getString(R.string.toast_no_macro)); return }
        if (!isAccessibilityEnabled()) { requestAccessibility(); return }
        binding.btnPlay.isEnabled = false
        binding.btnStop.visibility = View.VISIBLE
        player.play(m.steps)
    }

    private fun onStopClicked() {
        player.stop()
        recorder.stopRecording()
        AutomationAccessibilityService.instance?.setRecording(false)
        binding.btnStop.visibility = View.GONE
        binding.btnPlay.isEnabled = true
        updateChips()
    }

    // ---------- permissions ----------
    private fun isAccessibilityEnabled(): Boolean {
        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return services != null &&
            services.contains("com.example.automa/.accessibility.AutomationAccessibilityService")
    }

    private fun requestAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        toast("Enable Automa in Accessibility settings, then return")
    }

    private fun requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
                toast("Grant 'Draw over other apps' permission")
            } else {
                toggleOverlay()
            }
        } else {
            toggleOverlay()
        }
    }

    private fun toggleOverlay() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_TOGGLE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // state is flipped inside the service; read it back
        handler.postDelayed({ updateChips() }, 500)
        toast("Overlay toggled")
    }

    // ---------- macros section ----------
    private fun buildMacros(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.section_macros, binding.sectionsHost, false)

        val editor = v.findViewById<LinearLayout>(R.id.macroEditor)
        val editName = v.findViewById<TextInputEditText>(R.id.macroEditName)
        val editSteps = v.findViewById<TextInputEditText>(R.id.macroEditSteps)
        val editTitle = v.findViewById<TextView>(R.id.macroEditorTitle)
        var editing: Macro? = null

        fun openMacroEditor(m: Macro) {
            editing = m
            editTitle.text = "Edit: ${m.name}"
            editName.setText(m.name)
            editSteps.setText(stepsToJson(m.steps))
            editor.visibility = View.VISIBLE
        }

        v.findViewById<MaterialButton>(R.id.macroEditCancel).setOnClickListener {
            editor.visibility = View.GONE
            editing = null
        }
        v.findViewById<MaterialButton>(R.id.macroEditSave).setOnClickListener {
            val m = editing ?: return@setOnClickListener
            val all = prefs.getMacros().toMutableList()
            val idx = all.indexOfFirst { it.id == m.id }
            if (idx >= 0) {
                val steps = parseStepsJson(editSteps.text.toString())
                all[idx] = Macro(
                    id = m.id,
                    name = editName.text.toString().takeIf { it.isNotEmpty() } ?: m.name,
                    steps = steps
                )
                prefs.saveMacros(all)
                macroAdapter.setItems(all)
            }
            editor.visibility = View.GONE
            editing = null
            toast(getString(R.string.toast_saved))
        }

        val list = v.findViewById<RecyclerView>(R.id.macroList)
        list.layoutManager = LinearLayoutManager(this)
        macroAdapter = MacroAdapter(prefs.getMacros(), { m -> openMacroEditor(m) }, { m ->
            onPlayClicked()
        })
        list.adapter = macroAdapter

        v.findViewById<MaterialButton>(R.id.macroNew).setOnClickListener {
            val name = promptSimpleInput("New macro name", "Untitled macro")
            if (name.isNotEmpty()) {
                val m = Macro(name = name, steps = emptyList())
                val all = prefs.getMacros().toMutableList()
                all.add(m)
                prefs.saveMacros(all)
                macroAdapter.setItems(all)
                toast(getString(R.string.toast_saved))
            }
        }
        v.findViewById<MaterialButton>(R.id.macroPlaySelected).setOnClickListener { onPlayClicked() }
        v.findViewById<MaterialButton>(R.id.macroDeleteSelected).setOnClickListener {
            val m = macroAdapter.selected
            if (m == null) { toast("Select a macro"); return@setOnClickListener }
            AlertDialog.Builder(this).setMessage("Delete ${m.name}?")
                .setPositiveButton("Delete") { _, _ ->
                    val all = prefs.getMacros().toMutableList()
                    all.removeAll { it.id == m.id }
                    prefs.saveMacros(all)
                    macroAdapter.setItems(all)
                    macroAdapter.selected = null
                    toast("Deleted")
                }
                .setNegativeButton("Cancel", null).show()
        }
        v.findViewById<MaterialButton>(R.id.macroExport).setOnClickListener { exportData() }

        return v
    }

    private fun parseStepsJson(json: String): List<MacroStep> {
        return try {
            val type = object : TypeToken<List<MacroStep>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun stepsToJson(steps: List<MacroStep>): String {
        return Gson().toJson(steps)
    }

    private fun promptSimpleInput(title: String, hint: String): String {
        var result = ""
        val input = EditText(this).apply { setHint(hint) }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("OK") { _, _ -> result = input.text.toString() }
            .setNegativeButton("Cancel", null).show()
        return result
    }

    private fun exportData() {
        val all = prefs.getMacros()
        val json = Gson().toJson(all)
        val file = java.io.File(externalCacheDir, "automa_export.json")
        file.writeText(json)
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Export macros"))
        toast(getString(R.string.toast_exported))
    }

    // ---------- dashboard ----------
    private fun buildDashboard(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.section_dashboard, binding.sectionsHost, false)
        val recent = prefs.getMacros().reversed().take(5)
        val rv = v.findViewById<RecyclerView>(R.id.dashboardRecentMacros)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = MacroAdapter(recent, { m ->
            showSection(R.id.nav_macros)
            handler.post { macroAdapter.select(m) }
        }, { m ->
            onPlayClicked()
        })
        v.findViewById<MaterialButton>(R.id.dashboardOpenMacros).setOnClickListener {
            showSection(R.id.nav_macros)
        }
        v.findViewById<MaterialButton>(R.id.dashboardOpenScripts).setOnClickListener {
            showSection(R.id.nav_scripts)
        }
        v.findViewById<MaterialButton>(R.id.dashboardOpenRecognition).setOnClickListener {
            showSection(R.id.nav_recognition)
        }
        v.findViewById<MaterialButton>(R.id.dashboardOpenSettings).setOnClickListener {
            showSection(R.id.nav_settings)
        }
        return v
    }

    // ---------- phone section ----------
    private fun buildPhone(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.section_phone, binding.sectionsHost, false)
        val container = v.findViewById<FrameLayout>(R.id.phoneScreenContainer)

        val info = TextView(this)
        info.text = "Accessibility-driven automation\nControl any app with macros & scripts"
        info.setTextColor(android.graphics.Color.WHITE)
        info.textSize = 16f
        info.setPadding(16, 16, 16, 16)
        container.addView(info)

        AutomationAccessibilityService.instance?.setEventListener { event ->
            runOnUiThread {
                info.text = "Event: ${event.eventType} | ${event.text?.firstOrNull() ?: ""} | ${event.packageName ?: ""}"
            }
        }

        val chips = listOf(
            R.id.phoneChipHome to "Home",
            R.id.phoneChipCalc to "Calculator",
            R.id.phoneChipSettings to "Settings",
            R.id.phoneChipNotes to "Notes"
        )
        for ((resId, label) in chips) {
            v.findViewById<Chip>(resId).setOnClickListener {
                toast("Launch $label via accessibility")
            }
        }

        v.findViewById<TextView>(R.id.phoneHint).text =
            "Record taps & typing on the real screen, save as a macro, and replay."

        return v
    }

    // ---------- scripts ----------
    private fun buildScripts(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.section_scripts, binding.sectionsHost, false)
        val template = v.findViewById<Spinner>(R.id.scriptTemplate)
        val editor = v.findViewById<EditText>(R.id.scriptEditor)
        val output = v.findViewById<TextView>(R.id.scriptOutput)

        ArrayAdapter.createFromResource(this, R.array.script_templates,
            android.R.layout.simple_spinner_item).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            template.adapter = adapter
        }
        template.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                when (p2) {
                    1 -> editor.setText("click \"Calculator\"\nwait 400\nclick \"2\"\nclick \"+\"\nclick \"2\"\nclick \"=\"\nnotify \"4\"")
                    2 -> editor.setText("click \"Settings\"\nwait 500\nclick \"Wi-Fi\"\nnotify \"toggled\"")
                    3 -> editor.setText("click \"Notes\"\nwait 400\ntype \"Automated note\"\nclick \"Save\"\nnotify \"saved\"")
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        editor.setText(prefs.getScript())

        v.findViewById<MaterialButton>(R.id.scriptRun).setOnClickListener {
            if (!isAccessibilityEnabled()) { requestAccessibility(); return@setOnClickListener }
            binding.btnPlay.isEnabled = false
            binding.btnStop.visibility = View.VISIBLE
            output.text = ""
            scriptEngine.run(editor.text.toString())
            prefs.saveScript(editor.text.toString())
        }
        v.findViewById<MaterialButton>(R.id.scriptClear).setOnClickListener {
            editor.setText("")
            output.text = ""
        }
        v.findViewById<MaterialButton>(R.id.scriptSave).setOnClickListener {
            prefs.saveScript(editor.text.toString())
            toast(getString(R.string.toast_saved))
        }

        val origLog = scriptEngine.onLog
        scriptEngine.onLog = { msg, isErr ->
            origLog?.invoke(msg, isErr)
            runOnUiThread {
                val cur = output.text.toString()
                output.text = if (cur.isEmpty()) msg else "$cur\n$msg"
            }
        }

        return v
    }

    // ---------- recognition ----------
    private fun buildRecognition(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.section_recognition, binding.sectionsHost, false)

        v.findViewById<MaterialButton>(R.id.recFindText).setOnClickListener {
            val q = (v.findViewById<TextInputEditText>(R.id.recTextQuery)).text.toString()
            if (q.isEmpty()) { toast("Enter text"); return@setOnClickListener }
            val results = recognition.findText(q)
            v.findViewById<TextView>(R.id.recImageResult).text = "Found ${results.size} node(s) for '$q'"
        }
        v.findViewById<MaterialButton>(R.id.recFindType).setOnClickListener {
            val role = (v.findViewById<Spinner>(R.id.recTypeSpinner)).selectedItem.toString()
            val results = recognition.detectByRole(role)
            v.findViewById<TextView>(R.id.recTrackResult).text = "${results.size} $role node(s) detected"
        }
        v.findViewById<Button>(R.id.recImagePick).setOnClickListener { pickImage() }
        v.findViewById<MaterialButton>(R.id.recMatchImage).setOnClickListener {
            if (pickedImageUri == null) { toast("Select an image first"); return@setOnClickListener }
            try {
                val bmp = android.provider.MediaStore.Images.Media.getBitmap(contentResolver, pickedImageUri)
                val results = recognition.matchTemplate(bmp)
                v.findViewById<TextView>(R.id.recImageResult).text =
                    "Template matched ${results.size} region(s)"
                toast("Matched ${results.size} region(s)")
                bmp.recycle()
            } catch (e: Exception) { toast("Match failed") }
        }
        v.findViewById<MaterialButton>(R.id.recTrackStop).setOnClickListener {
            v.findViewById<TextView>(R.id.recTrackResult).text = "Tracking stopped"
        }

        val trackSpinner = v.findViewById<Spinner>(R.id.recTrackSelect)
        trackSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("Select target…")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        return v
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == android.app.Activity.RESULT_OK) {
            pickedImageUri = data?.data
            toast("Image selected")
        } else if (requestCode == 1002 && resultCode == android.app.Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val stream = contentResolver.openInputStream(uri)
                    val json = if (stream != null) stream.use { it.readBytes() }.let { String(it) } else ""
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                    val d = Gson().fromJson(json, type) as? Map<*, *>
                    if (d != null) importFromMap(d)
                    toast(getString(R.string.toast_imported))
                } catch (e: Exception) { toast("Import failed") }
            }
        }
    }

    private fun importFromMap(d: Map<*, *>) {
        val macros = d["macros"] as? List<*> ?: return
        val list = mutableListOf<Macro>()
        for (m in macros) {
            val mm = m as? Map<*, *> ?: continue
            list.add(Macro(
                id = mm["id"]?.toString() ?: java.util.UUID.randomUUID().toString(),
                name = mm["name"]?.toString() ?: "Untitled",
                steps = emptyList()
            ))
        }
        prefs.saveMacros(list)
        macroAdapter.setItems(list)
    }

    // ---------- keyboard ----------
    private fun buildKeyboard(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.section_keyboard, binding.sectionsHost, false)

        v.findViewById<MaterialButton>(R.id.kbToggleMic).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
            } else {
                toast("Mic input active in keyboard IME")
            }
        }

        v.findViewById<Chip>(R.id.kbChipIme).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            toast("Enable 'Automa Keyboard' and switch to it while typing")
        }

        val layoutSpinner = v.findViewById<Spinner>(R.id.kbLayoutSpinner)
        ArrayAdapter.createFromResource(this, R.array.kb_layouts,
            android.R.layout.simple_spinner_item).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            layoutSpinner.adapter = adapter
        }

        val preview = v.findViewById<FrameLayout>(R.id.kbPreview)
        preview.addView(KeyboardPreview(this))

        return v
    }

    private inner class KeyboardPreview(context: Context) : LinearLayout(context) {
        init {
            orientation = VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, R.color.automa_background))
            addView(TextView(context).apply {
                text = "Automa Keyboard (IME)\nQWERTY · symbols · numeric · mic"
                setTextColor(ContextCompat.getColor(context, R.color.automa_text))
                textSize = 16f
            })
        }
    }

    // ---------- clipboard ----------
    private fun buildClipboard(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.section_clipboard, binding.sectionsHost, false)
        val input = v.findViewById<TextInputEditText>(R.id.clipTextInput)
        val list = v.findViewById<RecyclerView>(R.id.clipList)
        list.layoutManager = LinearLayoutManager(this)
        val adapter = ClipAdapter(prefs.getClipboard()) { item ->
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("automa", item.text))
            toast("Copied: ${item.text}")
        }
        list.adapter = adapter

        val refresh = {
            adapter.setItems(prefs.getClipboard())
            binding.chipClip.text = "CLIPBOARD: ${prefs.getClipboard().size}"
        }
        refresh.invoke()

        v.findViewById<MaterialButton>(R.id.clipCopy).setOnClickListener {
            val t = input.text.toString()
            if (t.isEmpty()) { toast("Nothing"); return@setOnClickListener }
            val all = prefs.getClipboard().toMutableList()
            all.removeAll { it.text == t }
            all.add(0, ClipItem(t))
            if (all.size > 50) all.removeAt(all.size - 1)
            prefs.saveClipboard(all)
            refresh.invoke()
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("automa", t))
            toast(getString(R.string.toast_saved))
        }
        v.findViewById<MaterialButton>(R.id.clipPaste).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            input.setText(t)
            toast("Pasted")
        }
        v.findViewById<MaterialButton>(R.id.clipClear).setOnClickListener {
            prefs.saveClipboard(emptyList())
            refresh.invoke()
        }

        return v
    }

    // ---------- settings ----------
    private fun buildSettings(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.section_settings, binding.sectionsHost, false)

        val swOverlay = v.findViewById<SwitchCompat>(R.id.setOverlay)
        val swAcc = v.findViewById<SwitchCompat>(R.id.setAccessibility)
        val swMic = v.findViewById<SwitchCompat>(R.id.setMic)
        val swIme = v.findViewById<SwitchCompat>(R.id.setKeyboardIme)
        val swClip = v.findViewById<SwitchCompat>(R.id.setClipboardPersist)

        swOverlay.isChecked = OverlayService.isShowing
        swOverlay.setOnCheckedChangeListener { _, checked ->
            if (checked) requestOverlay() else toggleOverlay()
        }
        swAcc.setOnCheckedChangeListener { _, checked ->
            if (checked) requestAccessibility()
        }
        swMic.setOnCheckedChangeListener { _, checked ->
            if (checked && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
            }
        }
        swIme.setOnCheckedChangeListener { _, _ ->
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        swClip.isChecked = prefs.getBool("clip_persist", true)

        v.findViewById<MaterialButton>(R.id.settingsExport).setOnClickListener { exportData() }
        v.findViewById<MaterialButton>(R.id.settingsImport).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).setType("application/json")
            startActivityForResult(intent, 1002)
        }
        v.findViewById<MaterialButton>(R.id.settingsReset).setOnClickListener {
            AlertDialog.Builder(this).setMessage("Reset all Automa data?")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.saveMacros(emptyList())
                    prefs.saveClipboard(emptyList())
                    prefs.saveScript("")
                    prefs.saveNotes("")
                    macroAdapter.setItems(emptyList())
                    toast("Reset")
                }
                .setNegativeButton("Cancel", null).show()
        }

        return v
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// ---------- adapters ----------
class MacroAdapter(
    items: List<Macro>,
    private val onSelect: (Macro) -> Unit,
    private val onPlay: (Macro) -> Unit
) : RecyclerView.Adapter<MacroAdapter.ViewHolder>() {

    private var data = items.toMutableList()
    var selected: Macro? = null

    fun setItems(newItems: List<Macro>) { data = newItems.toMutableList(); notifyDataSetChanged() }
    fun select(m: Macro) { selected = m; notifyDataSetChanged() }

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_macro, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val m = data[position]
        holder.view.findViewById<TextView>(R.id.itemMacroName).text = m.name
        holder.view.findViewById<TextView>(R.id.itemMacroSteps).text = "${m.steps.size} steps"
        holder.view.findViewById<TextView>(R.id.itemMacroLast).text =
            m.lastRun?.let { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(it) } ?: "—"

        val ctx = holder.view.context
        val isSel = selected?.id == m.id
        holder.view.setBackgroundColor(
            if (isSel) ContextCompat.getColor(ctx, R.color.automa_primary)
            else ContextCompat.getColor(ctx, android.R.color.transparent))

        holder.view.findViewById<Chip>(R.id.itemMacroEdit).setOnClickListener { onSelect(m) }
        holder.view.findViewById<Chip>(R.id.itemMacroPlay).setOnClickListener { onPlay(m) }
    }

    override fun getItemCount(): Int = data.size
}

class ClipAdapter(
    items: List<ClipItem>,
    private val onClick: (ClipItem) -> Unit
) : RecyclerView.Adapter<ClipAdapter.ViewHolder>() {
    private var data = items.toMutableList()
    fun setItems(n: List<ClipItem>) { data = n.toMutableList(); notifyDataSetChanged() }
    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(v)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val c = data[position]
        holder.view.findViewById<TextView>(android.R.id.text1).text = c.text
        holder.view.findViewById<TextView>(android.R.id.text2).text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(c.ts)
        holder.view.setOnClickListener { onClick(c) }
    }
    override fun getItemCount(): Int = data.size
}
