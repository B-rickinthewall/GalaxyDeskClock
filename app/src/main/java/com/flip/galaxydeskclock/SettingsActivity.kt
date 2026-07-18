package com.flip.galaxydeskclock

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.time.ZoneId

class SettingsActivity : Activity() {
    private lateinit var appSettings: AppSettings
    private lateinit var backgroundRepository: BackgroundRepository
    private lateinit var content: LinearLayout
    private lateinit var photoStrip: LinearLayout
    private lateinit var photoCount: TextView

    private lateinit var use24Hour: Switch
    private lateinit var showDate: Switch
    private lateinit var showWeekday: Switch
    private lateinit var showSeconds: Switch
    private lateinit var mainScale: SeekBar
    private lateinit var textColor: Spinner
    private lateinit var backgroundDim: SeekBar

    private val worldEnabled = mutableListOf<Switch>()
    private val worldZones = mutableListOf<Spinner>()
    private val worldLabels = mutableListOf<EditText>()
    private val worldCustomZones = mutableListOf<EditText>()
    private val worldTypes = mutableListOf<Spinner>()
    private lateinit var showWorldOffset: Switch
    private lateinit var showWorldDayDifference: Switch

    private val layoutChecks = mutableListOf<CheckBox>()

    private lateinit var microShiftEnabled: Switch
    private lateinit var microShiftMinutes: Spinner
    private lateinit var restEnabled: Switch
    private lateinit var restStartMinute: Spinner
    private lateinit var restMoveSeconds: Spinner
    private lateinit var fadeSeconds: Spinner
    private lateinit var dayBrightness: SeekBar
    private lateinit var nightBrightness: SeekBar
    private lateinit var nightStart: Spinner
    private lateinit var nightEnd: Spinner

    private lateinit var chargingControl: Switch
    private lateinit var wakeOnCharge: Switch
    private lateinit var immediateLock: Switch
    private lateinit var startAfterBoot: Switch
    private lateinit var adminStatus: TextView

    private val zoneOptions = listOf(
        ZoneOption("Bangkok", "Asia/Bangkok"),
        ZoneOption("Berlin", "Europe/Berlin"),
        ZoneOption("London", "Europe/London"),
        ZoneOption("New York", "America/New_York"),
        ZoneOption("Los Angeles", "America/Los_Angeles"),
        ZoneOption("Chicago", "America/Chicago"),
        ZoneOption("Toronto", "America/Toronto"),
        ZoneOption("São Paulo", "America/Sao_Paulo"),
        ZoneOption("UTC", "UTC"),
        ZoneOption("Dubai", "Asia/Dubai"),
        ZoneOption("Mumbai", "Asia/Kolkata"),
        ZoneOption("Singapore", "Asia/Singapore"),
        ZoneOption("Hong Kong", "Asia/Hong_Kong"),
        ZoneOption("Tokyo", "Asia/Tokyo"),
        ZoneOption("Seoul", "Asia/Seoul"),
        ZoneOption("Sydney", "Australia/Sydney"),
        ZoneOption("Auckland", "Pacific/Auckland"),
        ZoneOption("Honolulu", "Pacific/Honolulu")
    )

    private val colorOptions = listOf(
        ColorOption("Warm white", Color.rgb(232, 229, 220)),
        ColorOption("Soft gray", Color.rgb(190, 194, 199)),
        ColorOption("Amber", Color.rgb(255, 190, 90)),
        ColorOption("Muted cyan", Color.rgb(125, 210, 220)),
        ColorOption("Muted green", Color.rgb(145, 210, 155))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appSettings = AppSettings(this)
        backgroundRepository = BackgroundRepository(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::adminStatus.isInitialized) refreshAdminStatus()
    }

    @Deprecated("Legacy activity result API retained for Android 9 compatibility without external libraries")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            REQUEST_IMAGES -> importSelectedImages(data)
            REQUEST_EXPORT -> exportSettings(data.data)
            REQUEST_IMPORT -> importSettings(data.data)
            REQUEST_DEVICE_ADMIN -> refreshAdminStatus()
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(14, 16, 20))
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(48))
        }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)

        addTitle("Galaxy Desk Clock settings")
        addBody("Long-press the clock at any time to return here. All settings are stored locally on the phone.")

        buildPhotoSection()
        buildMainClockSection()
        buildWorldClockSection()
        buildLayoutSection()
        buildBurnInSection()
        buildChargingSection()
        buildBackupSection()

        addSpacer(20)
        addButton("Save settings") {
            saveAllSettings()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
        addButton("Cancel") { finish() }
    }

    private fun buildPhotoSection() {
        addSection("Background photographs")
        photoCount = addBody("")
        addButton("Add photographs") { openImagePicker() }
        addButton("Remove all photographs") {
            backgroundRepository.clear()
            refreshPhotoStrip()
        }
        photoStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val horizontal = HorizontalScrollView(this).apply {
            addView(photoStrip)
            isHorizontalScrollBarEnabled = false
        }
        content.addView(horizontal, matchWrap().apply { topMargin = dp(10) })
        refreshPhotoStrip()
    }

    private fun buildMainClockSection() {
        addSection("Main clock")
        use24Hour = addSwitch("Use 24-hour time", appSettings.use24Hour)
        showDate = addSwitch("Show date", appSettings.showDate)
        showWeekday = addSwitch("Show weekday", appSettings.showWeekday)
        showSeconds = addSwitch("Show seconds", appSettings.showSeconds)
        mainScale = addSeekBar("Main clock size", 65, 140, appSettings.mainClockScalePercent, "%")
        textColor = addSpinner("Clock colour", colorOptions.map { it.name }, colorOptions.indexOfFirst { it.color == appSettings.textColor }.coerceAtLeast(0))
        backgroundDim = addSeekBar("Background darkening", 0, 85, appSettings.backgroundDimPercent, "%")
    }

    private fun buildWorldClockSection() {
        addSection("World clocks")
        addBody("Enable two to four clocks. Time zones and daylight-saving changes come from Android's local time-zone database.")
        showWorldOffset = addSwitch("Show UTC offsets", appSettings.showWorldOffset)
        showWorldDayDifference = addSwitch("Show +1 / −1 day indicators", appSettings.showWorldDayDifference)

        val current = appSettings.worldClocks()
        repeat(4) { index ->
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setBackgroundColor(Color.rgb(25, 28, 34))
            }
            content.addView(panel, matchWrap().apply {
                topMargin = dp(10)
                bottomMargin = dp(4)
            })

            val title = TextView(this).apply {
                text = "World clock ${index + 1}"
                textSize = 17f
                setTextColor(Color.WHITE)
            }
            panel.addView(title)

            val enabled = createSwitch("Enabled", current[index].enabled)
            panel.addView(enabled)
            worldEnabled += enabled

            val knownZoneIndex = zoneOptions.indexOfFirst { it.id == current[index].zoneId }
            val zoneSpinner = createSpinner(
                zoneOptions.map { "${it.name}  ·  ${it.id}" },
                knownZoneIndex.coerceAtLeast(0)
            )
            panel.addView(labeledContainer("Common time zone", zoneSpinner))
            worldZones += zoneSpinner

            val customZone = EditText(this).apply {
                setText(if (knownZoneIndex < 0) current[index].zoneId else "")
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                hint = "Optional custom IANA ID, e.g. Europe/Paris"
                setSingleLine(true)
            }
            panel.addView(labeledContainer("Custom time-zone ID (optional)", customZone))
            worldCustomZones += customZone

            val label = EditText(this).apply {
                setText(current[index].label)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                hint = "Display label"
                setSingleLine(true)
            }
            panel.addView(labeledContainer("Label", label))
            worldLabels += label

            val type = createSpinner(listOf("Digital", "Analogue"), if (current[index].analog) 1 else 0)
            panel.addView(labeledContainer("Style", type))
            worldTypes += type
        }
    }

    private fun buildLayoutSection() {
        addSection("Hourly layouts")
        addBody("At each new hour, the app selects one enabled preset and avoids a fixed clock position.")
        val names = listOf(
            "Main clock left / world clocks right",
            "Main clock right / world clocks left",
            "Main clock centred / world clocks below",
            "Main clock low / world clocks above",
            "Main clock lower-left / world clocks above",
            "Main clock upper-right / world clocks below"
        )
        names.forEachIndexed { index, name ->
            val checked = (appSettings.enabledLayoutMask and (1 shl index)) != 0
            val box = CheckBox(this).apply {
                text = name
                isChecked = checked
                setTextColor(Color.LTGRAY)
                textSize = 15f
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.LTGRAY)
            }
            content.addView(box, matchWrap())
            layoutChecks += box
        }
    }

    private fun buildBurnInSection() {
        addSection("AMOLED protection")
        microShiftEnabled = addSwitch("Micro-shift all clock elements", appSettings.microShiftEnabled)
        microShiftMinutes = addSpinner(
            "Micro-shift interval",
            listOf("2 minutes", "3 minutes", "5 minutes"),
            listOf(2, 3, 5).indexOf(appSettings.microShiftMinutes).coerceAtLeast(1)
        )
        restEnabled = addSwitch("Hourly Display Rest", appSettings.restEnabled)
        restStartMinute = addSpinner(
            "Black-screen rest starts",
            (55..59).map { "At minute $it" },
            (appSettings.restStartMinute - 55).coerceIn(0, 4)
        )
        restMoveSeconds = addSpinner(
            "Small clock movement",
            listOf("Every 5 seconds", "Every 10 seconds", "Every 15 seconds", "Every 20 seconds", "Every 30 seconds"),
            listOf(5, 10, 15, 20, 30).indexOf(appSettings.restMoveSeconds).coerceAtLeast(2)
        )
        fadeSeconds = addSpinner(
            "New layout fade-in",
            (1..10).map { "$it seconds" },
            (appSettings.fadeSeconds - 1).coerceIn(0, 9)
        )
        dayBrightness = addSeekBar("Day brightness", 5, 100, appSettings.dayBrightnessPercent, "%")
        nightBrightness = addSeekBar("Night brightness", 1, 100, appSettings.nightBrightnessPercent, "%")
        nightStart = addSpinner("Night mode begins", (0..23).map { String.format("%02d:00", it) }, appSettings.nightStartHour)
        nightEnd = addSpinner("Night mode ends", (0..23).map { String.format("%02d:00", it) }, appSettings.nightEndHour)
    }

    private fun buildChargingSection() {
        addSection("Charging stand behavior")
        chargingControl = addSwitch("Control display from charging state", appSettings.chargingControlEnabled)
        wakeOnCharge = addSwitch("Wake and open clock when charging starts", appSettings.wakeOnCharge)
        immediateLock = addSwitch("Immediately lock screen when charging stops", appSettings.immediateLockOnDisconnect)
        startAfterBoot = addSwitch("Start after reboot when charging", appSettings.startAfterBoot)

        adminStatus = addBody("")
        addButton("Enable immediate screen-off permission") { requestDeviceAdmin() }
        addButton("Open battery optimization settings") {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
        refreshAdminStatus()
    }

    private fun buildBackupSection() {
        addSection("Backup and privacy")
        addBody("The app declares no internet permission and includes no ads, analytics, accounts, weather, or tracking.")
        addButton("Export settings") {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "galaxy-desk-clock-settings.json")
            }
            startActivityForResult(intent, REQUEST_EXPORT)
        }
        addButton("Import settings") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, REQUEST_IMPORT)
        }
    }

    private fun saveAllSettings() {
        appSettings.use24Hour = use24Hour.isChecked
        appSettings.showDate = showDate.isChecked
        appSettings.showWeekday = showWeekday.isChecked
        appSettings.showSeconds = showSeconds.isChecked
        appSettings.mainClockScalePercent = mainScale.progress
        appSettings.textColor = colorOptions[textColor.selectedItemPosition].color
        appSettings.backgroundDimPercent = backgroundDim.progress

        appSettings.showWorldOffset = showWorldOffset.isChecked
        appSettings.showWorldDayDifference = showWorldDayDifference.isChecked
        repeat(4) { index ->
            val commonZone = zoneOptions[worldZones[index].selectedItemPosition]
            val requestedCustom = worldCustomZones[index].text.toString().trim()
            val selectedZoneId = if (requestedCustom.isNotBlank()) {
                runCatching { ZoneId.of(requestedCustom).id }.getOrElse {
                    Toast.makeText(
                        this,
                        "Invalid time-zone ID for world clock ${index + 1}; using ${commonZone.name}",
                        Toast.LENGTH_LONG
                    ).show()
                    commonZone.id
                }
            } else {
                commonZone.id
            }
            appSettings.setWorldClock(
                index,
                WorldClockConfig(
                    enabled = worldEnabled[index].isChecked,
                    zoneId = selectedZoneId,
                    label = worldLabels[index].text.toString().trim().ifBlank { commonZone.name },
                    analog = worldTypes[index].selectedItemPosition == 1
                )
            )
        }

        var layoutMask = 0
        layoutChecks.forEachIndexed { index, checkBox ->
            if (checkBox.isChecked) layoutMask = layoutMask or (1 shl index)
        }
        appSettings.enabledLayoutMask = layoutMask

        appSettings.microShiftEnabled = microShiftEnabled.isChecked
        appSettings.microShiftMinutes = listOf(2, 3, 5)[microShiftMinutes.selectedItemPosition]
        appSettings.restEnabled = restEnabled.isChecked
        appSettings.restStartMinute = 55 + restStartMinute.selectedItemPosition
        appSettings.restMoveSeconds = listOf(5, 10, 15, 20, 30)[restMoveSeconds.selectedItemPosition]
        appSettings.fadeSeconds = 1 + fadeSeconds.selectedItemPosition
        appSettings.dayBrightnessPercent = dayBrightness.progress
        appSettings.nightBrightnessPercent = nightBrightness.progress
        appSettings.nightStartHour = nightStart.selectedItemPosition
        appSettings.nightEndHour = nightEnd.selectedItemPosition

        appSettings.chargingControlEnabled = chargingControl.isChecked
        appSettings.wakeOnCharge = wakeOnCharge.isChecked
        appSettings.immediateLockOnDisconnect = immediateLock.isChecked
        appSettings.startAfterBoot = startAfterBoot.isChecked
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMAGES)
    }

    private fun importSelectedImages(data: Intent) {
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) uris += clip.getItemAt(index).uri
        }
        data.data?.let { uris += it }
        if (uris.isEmpty()) return

        Toast.makeText(this, "Importing ${uris.size} photograph(s)…", Toast.LENGTH_SHORT).show()
        Thread {
            val imported = backgroundRepository.importUris(uris)
            runOnUiThread {
                refreshPhotoStrip()
                Toast.makeText(this, "$imported photograph(s) imported", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun refreshPhotoStrip() {
        if (!::photoStrip.isInitialized) return
        photoStrip.removeAllViews()
        val files = backgroundRepository.list()
        photoCount.text = if (files.isEmpty()) {
            "No photographs selected. The app will use a built-in dark gradient."
        } else {
            "${files.size} photograph(s) stored privately inside the app."
        }

        files.forEach { file ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(backgroundRepository.decodeFile(file, dp(160), dp(100)))
            }
            card.addView(image, LinearLayout.LayoutParams(dp(160), dp(100)))
            val remove = Button(this).apply {
                text = "Remove"
                textSize = 12f
                setOnClickListener {
                    backgroundRepository.remove(file)
                    refreshPhotoStrip()
                }
            }
            card.addView(remove, LinearLayout.LayoutParams(dp(160), ViewGroup.LayoutParams.WRAP_CONTENT))
            photoStrip.addView(card)
        }
    }

    private fun requestDeviceAdmin() {
        val component = ComponentName(this, DeskClockAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "This optional permission lets Galaxy Desk Clock lock the display immediately when charging stops."
            )
        }
        startActivityForResult(intent, REQUEST_DEVICE_ADMIN)
    }

    private fun refreshAdminStatus() {
        val manager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val active = manager.isAdminActive(ComponentName(this, DeskClockAdminReceiver::class.java))
        adminStatus.text = if (active) {
            "Immediate screen-off permission: enabled"
        } else {
            "Immediate screen-off permission: not enabled. Without it, the app shows black and waits for Android's normal screen timeout."
        }
        adminStatus.setTextColor(if (active) Color.rgb(150, 220, 160) else Color.rgb(230, 190, 120))
    }

    private fun exportSettings(uri: Uri?) {
        if (uri == null) return
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(appSettings.toJson().toString(2))
            } ?: error("Unable to open destination")
        }.onSuccess {
            Toast.makeText(this, "Settings exported", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Export failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importSettings(uri: Uri?) {
        if (uri == null) return
        runCatching {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Unable to read settings")
            appSettings.importJson(JSONObject(text))
        }.onSuccess {
            Toast.makeText(this, "Settings imported", Toast.LENGTH_SHORT).show()
            recreate()
        }.onFailure {
            Toast.makeText(this, "Import failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addTitle(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 27f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(6))
        }, matchWrap())
    }

    private fun addSection(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 21f
            setTextColor(Color.rgb(205, 214, 224))
            setPadding(0, dp(28), 0, dp(8))
        }, matchWrap())
    }

    private fun addBody(text: String): TextView {
        val view = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setLineSpacing(0f, 1.15f)
        }
        content.addView(view, matchWrap().apply { bottomMargin = dp(8) })
        return view
    }

    private fun addSwitch(label: String, checked: Boolean): Switch {
        val switch = createSwitch(label, checked)
        content.addView(switch, matchWrap())
        return switch
    }

    private fun createSwitch(label: String, checked: Boolean): Switch = Switch(this).apply {
        text = label
        isChecked = checked
        setTextColor(Color.LTGRAY)
        textSize = 15f
        setPadding(0, dp(4), 0, dp(4))
        buttonTintList = android.content.res.ColorStateList.valueOf(Color.LTGRAY)
    }

    private fun addSeekBar(label: String, min: Int, max: Int, current: Int, suffix: String): SeekBar {
        val valueLabel = TextView(this).apply {
            text = "$label: $current$suffix"
            setTextColor(Color.LTGRAY)
            textSize = 15f
        }
        content.addView(valueLabel, matchWrap().apply { topMargin = dp(8) })
        val seek = SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = current.coerceIn(min, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueLabel.text = "$label: $progress$suffix"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        content.addView(seek, matchWrap())
        return seek
    }

    private fun addSpinner(label: String, options: List<String>, selected: Int): Spinner {
        val spinner = createSpinner(options, selected)
        content.addView(labeledContainer(label, spinner), matchWrap())
        return spinner
    }

    private fun createSpinner(options: List<String>, selected: Int): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, options)
        setSelection(selected.coerceIn(0, options.lastIndex))
    }

    private fun labeledContainer(label: String, child: View): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(7), 0, dp(4))
            addView(TextView(this@SettingsActivity).apply {
                text = label
                textSize = 13f
                setTextColor(Color.GRAY)
            }, matchWrap())
            addView(child, matchWrap())
        }
    }

    private fun addButton(label: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setOnClickListener { action() }
        }
        content.addView(button, matchWrap().apply {
            topMargin = dp(6)
            bottomMargin = dp(2)
        })
        return button
    }

    private fun addSpacer(heightDp: Int) {
        content.addView(View(this), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class ZoneOption(val name: String, val id: String)
    private data class ColorOption(val name: String, val color: Int)

    companion object {
        private const val REQUEST_IMAGES = 1001
        private const val REQUEST_EXPORT = 1002
        private const val REQUEST_IMPORT = 1003
        private const val REQUEST_DEVICE_ADMIN = 1004
    }
}
