package pl.szynolandia.mjakmiloscusb

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    data class Episode(
        val number: Int,
        var url: String,
        var title: String = ""
    )

    data class SeriesOption(
        val key: String,
        val title: String,
        val url: String,
        val filePrefix: String
    )

    private lateinit var titleText: TextView
    private lateinit var engineStatusText: TextView
    private lateinit var usbStatusText: TextView
    private lateinit var spaceText: TextView
    private lateinit var downloadedSummaryText: TextView
    private lateinit var downloadSelectionText: TextView
    private lateinit var episodesContainer: LinearLayout
    private lateinit var downloadedContainer: LinearLayout
    private lateinit var deleteButton: Button
    private lateinit var downloadButton: Button
    private lateinit var stopButton: Button
    private lateinit var currentDownloadText: TextView
    private lateinit var currentProgressBar: ProgressBar
    private lateinit var queueProgressText: TextView
    private lateinit var catalogStatusText: TextView
    private lateinit var seriesUrlEdit: EditText
    private lateinit var refreshCatalogButton: Button
    private lateinit var startEpisodeEdit: EditText
    private lateinit var advancedContainer: LinearLayout
    private lateinit var seriesSpinner: Spinner
    private var suppressSeriesSelection = false
    private var visibleEpisodeLimit: Int = 10

    private val prefs by lazy {
        getSharedPreferences(
            DownloadService.PREFS_NAME,
            MODE_PRIVATE
        )
    }

    private val episodes =
        mutableListOf<Episode>()

    private val selectedEpisodeNumbers =
        mutableSetOf<Int>()

    private val selectedDownloadedUris =
        mutableSetOf<String>()

    private val downloadedEpisodeNumbers =
        mutableSetOf<Int>()

    private var treeUri: Uri? = null

    @Volatile
    private var engineReady = false

    private val folderPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->

            if (uri != null) {

                try {
                    contentResolver
                        .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                } catch (_: SecurityException) {
                }

                prefs.edit()
                    .putString(
                        DownloadService.KEY_TREE_URI,
                        uri.toString()
                    )
                    .apply()

                treeUri = uri

                refreshUsb()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    private val downloadStateReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (
                    intent?.action !=
                    DownloadService.ACTION_STATE
                ) {
                    return
                }

                val episode =
                    intent.getIntExtra(
                        DownloadService.EXTRA_EPISODE,
                        -1
                    )

                val progress =
                    intent.getIntExtra(
                        DownloadService.EXTRA_PROGRESS,
                        0
                    )

                val status =
                    intent.getStringExtra(
                        DownloadService.EXTRA_STATUS
                    ) ?: ""

                val completed =
                    intent.getIntExtra(
                        DownloadService.EXTRA_COMPLETED,
                        -1
                    )

                val total =
                    intent.getIntExtra(
                        DownloadService.EXTRA_TOTAL,
                        -1
                    )

                currentProgressBar.progress =
                    progress.coerceIn(
                        0,
                        100
                    )

                currentDownloadText.text =
                    when {
                        episode > 0 ->
                            "Odc. $episode: $status"

                        status.isNotBlank() ->
                            status

                        else ->
                            "Brak aktywnego pobierania"
                    }

                if (
                    completed >= 0 &&
                    total >= 0
                ) {
                    queueProgressText.text =
                        "Kolejka: $completed / $total"
                }

                if (
                    status == "Gotowe" ||
                    status == "Błąd" ||
                    status == "Zatrzymano"
                ) {
                    refreshUsb()
                }

                updateButtonsFromPersistedState()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_main
        )

        bindViews()
        bindButtons()
        setupSeriesSelector()

        treeUri =
            prefs.getString(
                DownloadService.KEY_TREE_URI,
                null
            )
                ?.let(
                    Uri::parse
                )

        requestNotificationPermissionIfNeeded()

        loadCatalogCacheOrAssets()
        seriesUrlEdit.setText(
            currentSeries().url
        )
        refreshUsb()
        initDownloader()
        restoreDownloadState()
        refreshCatalogFromTvp(false)
    }

    override fun onStart() {
        super.onStart()

        ContextCompat.registerReceiver(
            this,
            downloadStateReceiver,
            IntentFilter(
                DownloadService.ACTION_STATE
            ),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        try {
            unregisterReceiver(
                downloadStateReceiver
            )
        } catch (_: Exception) {
        }

        super.onStop()
    }

    override fun onResume() {
        super.onResume()

        refreshUsb()
        restoreDownloadState()
    }

    private fun bindViews() {
        titleText =
            findViewById(
                R.id.titleText
            )

        engineStatusText =
            findViewById(
                R.id.engineStatusText
            )

        usbStatusText =
            findViewById(
                R.id.usbStatusText
            )

        spaceText =
            findViewById(
                R.id.spaceText
            )

        downloadedSummaryText =
            findViewById(
                R.id.downloadedSummaryText
            )

        downloadSelectionText =
            findViewById(
                R.id.downloadSelectionText
            )

        episodesContainer =
            findViewById(
                R.id.episodesContainer
            )

        downloadedContainer =
            findViewById(
                R.id.downloadedContainer
            )

        deleteButton =
            findViewById(
                R.id.deleteButton
            )

        downloadButton =
            findViewById(
                R.id.downloadButton
            )

        stopButton =
            findViewById(
                R.id.stopButton
            )

        currentDownloadText =
            findViewById(
                R.id.currentDownloadText
            )

        currentProgressBar =
            findViewById(
                R.id.currentProgressBar
            )

        queueProgressText =
            findViewById(
                R.id.queueProgressText
            )

        catalogStatusText = findViewById(R.id.catalogStatusText)
        seriesUrlEdit = findViewById(R.id.seriesUrlEdit)
        refreshCatalogButton = findViewById(R.id.refreshCatalogButton)
        startEpisodeEdit = findViewById(R.id.startEpisodeEdit)
        advancedContainer = findViewById(R.id.advancedContainer)
        seriesSpinner = findViewById(R.id.seriesSpinner)
    }

    private fun bindButtons() {

        findViewById<Button>(
            R.id.selectUsbButton
        ).setOnClickListener {
            folderPicker.launch(
                treeUri
            )
        }

        findViewById<Button>(
            R.id.refreshButton
        ).setOnClickListener {
            refreshUsb()
        }

        findViewById<Button>(R.id.saveSeriesButton).setOnClickListener {
            saveSeriesUrlAndRefresh()
        }

        refreshCatalogButton.setOnClickListener {
            refreshCatalogFromTvp(true)
        }

        findViewById<Button>(
            R.id.editLinksButton
        ).setOnClickListener {
            showLinksEditor()
        }

        findViewById<Button>(R.id.showEpisodesButton).setOnClickListener {
            val number = startEpisodeEdit.text.toString().trim().toIntOrNull()
            if (number == null) {
                Toast.makeText(this, "Wpisz numer odcinka.", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putInt(selectedStartKey(), number).apply()
                visibleEpisodeLimit = 10
                rebuildEpisodesUi()
            }
        }

        findViewById<Button>(R.id.showMoreEpisodesButton).setOnClickListener {
            visibleEpisodeLimit += 10
            rebuildEpisodesUi()
        }

        findViewById<Button>(R.id.advancedButton).setOnClickListener {
            advancedContainer.visibility =
                if (advancedContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        downloadButton
            .setOnClickListener {
                startSelectedDownloads()
            }

        stopButton
            .setOnClickListener {
                stopDownloads()
            }

        deleteButton
            .setOnClickListener {
                confirmDeleteSelected()
            }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    private fun setupSeriesSelector() {
        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                SERIES_OPTIONS.map { it.title }
            ).apply {
                setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
                )
            }

        seriesSpinner.adapter =
            adapter

        val savedKey =
            prefs.getString(
                KEY_SELECTED_SERIES,
                SERIES_OPTIONS.first().key
            ) ?: SERIES_OPTIONS.first().key

        val selectedIndex =
            SERIES_OPTIONS.indexOfFirst {
                it.key == savedKey
            }.let {
                if (it >= 0) it else 0
            }

        suppressSeriesSelection =
            true

        seriesSpinner.setSelection(
            selectedIndex,
            false
        )

        suppressSeriesSelection =
            false

        seriesSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (
                        suppressSeriesSelection
                    ) {
                        return
                    }

                    val selected =
                        SERIES_OPTIONS[
                            position.coerceIn(
                                0,
                                SERIES_OPTIONS.lastIndex
                            )
                        ]

                    val previous =
                        prefs.getString(
                            KEY_SELECTED_SERIES,
                            SERIES_OPTIONS.first().key
                        )

                    if (
                        previous == selected.key
                    ) {
                        return
                    }

                    prefs.edit()
                        .putString(
                            KEY_SELECTED_SERIES,
                            selected.key
                        )
                        .remove(
                            selectedStartKey(
                                selected.key
                            )
                        )
                        .apply()

                    visibleEpisodeLimit =
                        10

                    episodes.clear()
                    selectedEpisodeNumbers.clear()

                    seriesUrlEdit.setText(
                        selected.url
                    )

                    loadCatalogCacheOrAssets()
                    refreshUsb()
                    refreshCatalogFromTvp(
                        false
                    )
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit
            }
    }

    private fun currentSeries(): SeriesOption {
        val key =
            prefs.getString(
                KEY_SELECTED_SERIES,
                SERIES_OPTIONS.first().key
            ) ?: SERIES_OPTIONS.first().key

        return SERIES_OPTIONS.firstOrNull {
            it.key == key
        } ?: SERIES_OPTIONS.first()
    }

    private fun catalogKey(
        seriesKey: String = currentSeries().key
    ): String =
        "${KEY_CATALOG_JSON}_$seriesKey"

    private fun selectedStartKey(
        seriesKey: String = currentSeries().key
    ): String =
        "${KEY_START_EPISODE}_$seriesKey"

    private fun fileBelongsToCurrentSeries(
        fileName: String
    ): Boolean {
        val normalized =
            fileName.lowercase(
                Locale.ROOT
            )

        return when (
            currentSeries().key
        ) {
            "mjak" ->
                !normalized.startsWith(
                    "klan - "
                ) &&
                    !normalized.startsWith(
                        "na dobre i na zle - "
                    )

            "klan" ->
                normalized.startsWith(
                    "klan - "
                )

            "nadobre" ->
                normalized.startsWith(
                    "na dobre i na zle - "
                )

            else ->
                false
        }
    }

    private fun initDownloader() {
        engineStatusText.text =
            "Silnik pobierania: inicjalizacja..."

        lifecycleScope.launch(
            Dispatchers.IO
        ) {
            try {
                YoutubeDL.getInstance()
                    .init(
                        applicationContext
                    )

                try {
                    FFmpeg.getInstance()
                        .init(
                            applicationContext
                        )
                } catch (_: Exception) {
                }

                withContext(
                    Dispatchers.Main
                ) {
                    engineStatusText.text =
                        "Silnik pobierania: aktualizacja yt-dlp..."
                }

                try {
                    YoutubeDL.getInstance()
                        .updateYoutubeDL(
                            applicationContext,
                            UpdateChannel.STABLE
                        )
                } catch (_: Exception) {
                }

                val version =
                    try {
                        YoutubeDL.getInstance()
                            .version(
                                applicationContext
                            ) ?: "?"
                    } catch (_: Exception) {
                        "?"
                    }

                engineReady = true

                withContext(
                    Dispatchers.Main
                ) {
                    engineStatusText.text =
                        "Silnik pobierania: gotowy • yt-dlp $version"

                    updateButtonsFromPersistedState()
                }

            } catch (e: Exception) {

                engineReady = false

                withContext(
                    Dispatchers.Main
                ) {
                    engineStatusText.text =
                        "Silnik pobierania: błąd • " +
                            (
                                e.message
                                    ?.take(120)
                                    ?: "nieznany błąd"
                                )

                    updateButtonsFromPersistedState()
                }
            }
        }
    }

    private fun loadCatalogCacheOrAssets() {
        episodes.clear()
        val cached = prefs.getString(catalogKey(), null)
        if (!cached.isNullOrBlank()) {
            try {
                val root = JSONObject(cached)
                titleText.text = root.optString("series", "VOD")
                val array = root.optJSONArray("episodes") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val number = item.optInt("number", -1)
                    if (number > 0) {
                        episodes += Episode(
                            number = number,
                            url = item.optString("url", ""),
                            title = item.optString("title", "")
                        )
                    }
                }
                if (episodes.isNotEmpty()) {
                    rebuildEpisodesUi()
                    catalogStatusText.text = "Katalog TVP: ${episodes.size} odc. • dane lokalne"
                    return
                }
            } catch (_: Exception) {}
        }
        val raw = assets.open("episodes.json").bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        titleText.text = currentSeries().title
        val array = json.getJSONArray("episodes")
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val number = item.getInt("number")
            val savedUrl = prefs.getString("episode_url_$number", null)
            episodes += Episode(number, savedUrl ?: item.optString("url", ""))
        }
        rebuildEpisodesUi()
    }

    private fun saveSeriesUrlAndRefresh() {
        val url = seriesUrlEdit.text.toString().trim()
        if (parseSeriesId(url) == null) {
            Toast.makeText(this, "Podaj główny adres serialu TVP.", Toast.LENGTH_LONG).show()
            return
        }
        prefs.edit().putString(KEY_SERIES_URL, url).apply()
        refreshCatalogFromTvp(true)
    }

    private fun refreshCatalogFromTvp(showToast: Boolean) {
        val seriesUrl = seriesUrlEdit.text.toString().trim().ifBlank { currentSeries().url }
        val seriesId = parseSeriesId(seriesUrl) ?: return
        catalogStatusText.text = "Katalog TVP: aktualizowanie..."
        refreshCatalogButton.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val metadata = getJsonObject("$TVP_API_BASE/vods/serials/$seriesId?lang=pl&platform=BROWSER")
                val seasons = getJsonArray("$TVP_API_BASE/vods/serials/$seriesId/seasons?lang=pl&platform=BROWSER")
                val found = mutableMapOf<Int, Episode>()
                for (i in 0 until seasons.length()) {
                    val seasonId = seasons.getJSONObject(i).optString("id", "")
                    if (seasonId.isBlank()) continue
                    val arr = getJsonArray("$TVP_API_BASE/vods/serials/$seriesId/seasons/$seasonId/episodes?lang=pl&platform=BROWSER")
                    for (j in 0 until arr.length()) {
                        val item = arr.getJSONObject(j)
                        val number = item.optInt("number", -1)
                        val webUrl = item.optString("webUrl", "")
                        if (number <= 0 || webUrl.isBlank()) continue
                        found[number] = Episode(number, webUrl, item.optString("title"))
                    }
                }
                val sorted = found.values.sortedByDescending { it.number }
                if (sorted.isEmpty()) error("TVP nie zwróciło odcinków")
                val seriesTitle = metadata.optString("title", "VOD")
                saveCatalog(seriesTitle, sorted)
                withContext(Dispatchers.Main) {
                    episodes.clear(); episodes.addAll(sorted); titleText.text = seriesTitle
                    prefs.edit().putString("${KEY_SERIES_URL}_${currentSeries().key}", seriesUrl).apply()
                    refreshUsb()
                    catalogStatusText.text = "Katalog TVP: ${sorted.size} odc. • zaktualizowano"
                    refreshCatalogButton.isEnabled = true
                    if (showToast) Toast.makeText(this@MainActivity, "Katalog zaktualizowany: ${sorted.size} odcinków.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    catalogStatusText.text = "Katalog TVP: błąd • ${e.message?.take(100) ?: "nieznany"}"
                    refreshCatalogButton.isEnabled = true
                    if (showToast) Toast.makeText(this@MainActivity, "Nie udało się odświeżyć katalogu.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseSeriesId(url: String): String? = Regex("""-odcinki,(\d+)""").find(url)?.groupValues?.getOrNull(1)
    private fun getJsonObject(url: String) = JSONObject(httpGet(url))
    private fun getJsonArray(url: String) = JSONArray(httpGet(url))

    private fun httpGet(urlText: String): String {
        val c = URL(urlText).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"; c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) VOD-USB/0.4.0")
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code")
            return body
        } finally { c.disconnect() }
    }

    private fun findFirstImageUrl(node: Any?): String {
        when (node) {
            is JSONObject -> {
                val direct = node.optString("url", "")
                if (direct.startsWith("http")) return direct
                val keys = node.keys()
                while (keys.hasNext()) {
                    val result = findFirstImageUrl(node.opt(keys.next()))
                    if (result.isNotBlank()) return result
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                val result = findFirstImageUrl(node.opt(i)); if (result.isNotBlank()) return result
            }
        }
        return ""
    }

    private fun saveCatalog(seriesTitle: String, list: List<Episode>) {
        val root = JSONObject().put("series", seriesTitle)
        val array = JSONArray()
        list.forEach { e -> array.put(JSONObject().put("number", e.number).put("url", e.url).put("title", e.title)) }
        root.put("episodes", array)
        prefs.edit().putString(catalogKey(), root.toString()).apply()
    }

    private fun rebuildEpisodesUi() {
        episodesContainer.removeAllViews()
        selectedEpisodeNumbers.clear()

        val root = currentUsbRoot()
        val usbReady =
            root != null &&
                root.exists() &&
                root.canRead()

        if (!usbReady) {
            findViewById<TextView>(R.id.episodesHintText).text =
                "Najpierw podłącz i wybierz pendrive."

            findViewById<Button>(R.id.showMoreEpisodesButton).visibility =
                View.GONE

            updateEpisodeSelectionSummary()
            return
        }

        if (episodes.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Pobieranie listy odcinków..."
                textSize = 16f
                setPadding(4, 12, 4, 12)
            }

            episodesContainer.addView(empty)

            findViewById<Button>(R.id.showMoreEpisodesButton).visibility =
                View.GONE

            updateEpisodeSelectionSummary()
            return
        }

        val manualStart =
            prefs.getInt(
                selectedStartKey(),
                -1
            )

        val automaticStart =
            if (downloadedEpisodeNumbers.isNotEmpty()) {
                (downloadedEpisodeNumbers.maxOrNull() ?: 0) + 1
            } else {
                episodes.minOfOrNull {
                    it.number
                } ?: 1
            }

        val requestedStart =
            if (manualStart > 0) {
                manualStart
            } else {
                automaticStart
            }

        findViewById<TextView>(R.id.episodesHintText).text =
            if (manualStart > 0) {
                "Pokazuję odcinki od $requestedStart (ustawione ręcznie)."
            } else {
                "Następny odcinek do pobrania: $requestedStart"
            }

        val allAvailable =
            episodes
                .filter {
                    it.number >= requestedStart
                }
                .sortedBy {
                    it.number
                }

        val visibleEpisodes =
            allAvailable.take(
                visibleEpisodeLimit
            )

        if (visibleEpisodes.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Brak kolejnych odcinków do pokazania."
                textSize = 16f
                setPadding(4, 12, 4, 12)
            }

            episodesContainer.addView(empty)
        } else {
            for (episode in visibleEpisodes) {
                val alreadyDownloaded =
                    episode.number in downloadedEpisodeNumbers

                val check =
                    CheckBox(this).apply {
                        text =
                            if (alreadyDownloaded) {
                                "Odcinek ${episode.number}   •   na pendrive ✓"
                            } else {
                                "Odcinek ${episode.number}"
                            }

                        textSize = 18f
                        minHeight = dp(52)

                        isEnabled =
                            !alreadyDownloaded &&
                                episode.url.isNotBlank()

                        setOnCheckedChangeListener {
                                _,
                                checked ->

                            if (checked) {
                                selectedEpisodeNumbers.add(
                                    episode.number
                                )
                            } else {
                                selectedEpisodeNumbers.remove(
                                    episode.number
                                )
                            }

                            updateEpisodeSelectionSummary()
                        }
                    }

                episodesContainer.addView(
                    check
                )
            }
        }

        findViewById<Button>(R.id.showMoreEpisodesButton).visibility =
            if (
                allAvailable.size >
                visibleEpisodes.size
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        updateEpisodeSelectionSummary()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateEpisodeSelectionSummary() {

        val count =
            selectedEpisodeNumbers
                .size

        val estimateBytes =
            count *
                ESTIMATED_EPISODE_BYTES

        downloadSelectionText.text =
            if (count == 0) {
                "Zaznacz odcinki poniżej."
            } else {
                "Wybrano: $count odc. • około " +
                    formatBytes(
                        estimateBytes
                    )
            }

        updateButtonsFromPersistedState()
    }

    private fun showLinksEditor() {

        val input =
            EditText(this).apply {

                minLines =
                    10

                gravity =
                    Gravity.TOP

                setPadding(
                    24,
                    18,
                    24,
                    18
                )

                setText(
                    episodes
                        .sortedBy {
                            it.number
                        }
                        .joinToString(
                            "\n"
                        ) { episode ->
                            "${episode.number}|${episode.url}"
                        }
                )
            }

        AlertDialog.Builder(this)
            .setTitle(
                "Linki odcinków"
            )
            .setMessage(
                "Jedna linia = numer|link\n\n" +
                    "Przykład:\n" +
                    "1801|https://vod.tvp.pl/..."
            )
            .setView(
                input
            )
            .setNegativeButton(
                "Anuluj",
                null
            )
            .setPositiveButton(
                "Zapisz"
            ) { _, _ ->
                saveLinksFromText(
                    input.text
                        .toString()
                )
            }
            .show()
    }

    private fun saveLinksFromText(
        text: String
    ) {
        val byNumber =
            mutableMapOf<Int, String>()

        for (
            line
            in text.lines()
        ) {
            val trimmed =
                line.trim()

            if (
                trimmed.isBlank() ||
                "|" !in trimmed
            ) {
                continue
            }

            val number =
                trimmed
                    .substringBefore(
                        "|"
                    )
                    .trim()
                    .toIntOrNull()
                    ?: continue

            val url =
                trimmed
                    .substringAfter(
                        "|"
                    )
                    .trim()

            byNumber[
                number
            ] = url
        }

        for (
            episode
            in episodes
        ) {
            if (
                episode.number in byNumber
            ) {
                episode.url =
                    byNumber[
                        episode.number
                    ].orEmpty()

                prefs.edit()
                    .putString(
                        "episode_url_${episode.number}",
                        episode.url
                    )
                    .apply()
            }
        }

        saveCatalog(titleText.text.toString(), episodes)
        rebuildEpisodesUi()

        Toast.makeText(
            this,
            "Linki zapisane.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun refreshUsb() {

        selectedDownloadedUris
            .clear()

        downloadedEpisodeNumbers
            .clear()

        downloadedContainer
            .removeAllViews()

        val uri =
            treeUri

        if (
            uri == null
        ) {
            usbStatusText.text =
                "Pendrive nie jest jeszcze wybrany"

            spaceText.visibility =
                View.GONE

            downloadedSummaryText.text =
                "Na pendrive: 0 odcinków"

            deleteButton.isEnabled =
                false

            rebuildEpisodesUi()
            return
        }

        val root =
            DocumentFile.fromTreeUri(
                this,
                uri
            )

        if (
            root == null ||
            !root.exists() ||
            !root.canRead()
        ) {
            usbStatusText.text =
                "Pendrive odłączony — podłącz go i naciśnij Odśwież"

            spaceText.visibility =
                View.GONE

            downloadedSummaryText.text =
                "Na pendrive: 0 odcinków"

            deleteButton.isEnabled =
                false

            rebuildEpisodesUi()
            return
        }

        usbStatusText.text =
            "Pendrive podłączony ✓  ${root.name ?: "USB"}"

        val space =
            getStorageSpace(
                uri
            )

        if (
            space != null
        ) {
            spaceText.visibility =
                View.VISIBLE

            spaceText.text =
                "Wolne miejsce: " +
                    "${formatBytes(space.first)} z " +
                    formatBytes(
                        space.second
                    )
        } else {
            spaceText.visibility =
                View.GONE
        }

        val videos =
            root.listFiles()
                .filter { file ->

                    file.isFile &&
                        fileBelongsToCurrentSeries(file.name ?: "") &&
                        file.name
                            ?.lowercase(
                                Locale.ROOT
                            )
                            ?.let {
                                it.endsWith(
                                    ".mp4"
                                ) ||
                                    it.endsWith(
                                        ".mkv"
                                    ) ||
                                    it.endsWith(
                                        ".webm"
                                    ) ||
                                    it.endsWith(
                                        ".m4v"
                                    )
                            } == true
                }
                .sortedWith(
                    compareBy<DocumentFile> {
                        extractEpisodeNumber(
                            it.name ?: ""
                        ) ?: Int.MAX_VALUE
                    }.thenBy {
                        it.name ?: ""
                    }
                )

        for (
            file
            in videos
        ) {
            extractEpisodeNumber(
                file.name ?: ""
            )
                ?.let(
                    downloadedEpisodeNumbers::add
                )
        }

        if (
            downloadedEpisodeNumbers.isNotEmpty()
        ) {
            prefs.edit()
                .remove(
                    selectedStartKey()
                )
                .apply()

            visibleEpisodeLimit =
                10
        }

        val totalBytes =
            videos.sumOf {
                it.length()
            }

        downloadedSummaryText.text =
            "Na pendrive: ${videos.size} odcinków • " +
                formatBytes(
                    totalBytes
                )

        if (
            videos.isEmpty()
        ) {
            val empty =
                TextView(this).apply {
                    text =
                        "Pendrive jest pusty — nie ma jeszcze pobranych odcinków."

                    textSize =
                        16f

                    setPadding(
                        4,
                        12,
                        4,
                        12
                    )
                }

            downloadedContainer
                .addView(
                    empty
                )

            deleteButton.isEnabled =
                false

        } else {

            for (
                file
                in videos
            ) {
                addDownloadedFileRow(
                    file
                )
            }

            deleteButton.isEnabled =
                !isQueueActive()
        }

        rebuildEpisodesUi()
    }

    private fun addDownloadedFileRow(
        file: DocumentFile
    ) {
        val row =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    0,
                    5,
                    0,
                    5
                )
            }

        val episode =
            extractEpisodeNumber(
                file.name ?: ""
            )

        val displayName =
            if (
                episode != null
            ) {
                "Odc. $episode • " +
                    formatBytes(
                        file.length()
                    )
            } else {
                "${file.name ?: "Film"} • " +
                    formatBytes(
                        file.length()
                    )
            }

        val checkBox =
            CheckBox(this).apply {

                text =
                    displayName

                textSize =
                    17f

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )

                setOnCheckedChangeListener {
                        _,
                        checked ->

                    if (checked) {
                        selectedDownloadedUris
                            .add(
                                file.uri
                                    .toString()
                            )
                    } else {
                        selectedDownloadedUris
                            .remove(
                                file.uri
                                    .toString()
                            )
                    }

                    deleteButton.isEnabled =
                        selectedDownloadedUris
                            .isNotEmpty() &&
                            !isQueueActive()
                }
            }

        val playButton =
            Button(this).apply {

                text =
                    "▶"

                setOnClickListener {
                    playFile(
                        file
                    )
                }
            }

        row.addView(
            checkBox
        )

        row.addView(
            playButton
        )

        downloadedContainer
            .addView(
                row
            )
    }

    private fun startSelectedDownloads() {

        if (
            !engineReady
        ) {
            Toast.makeText(
                this,
                "Silnik yt-dlp nie jest jeszcze gotowy.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val root =
            currentUsbRoot()

        if (
            root == null ||
            !root.canWrite()
        ) {
            Toast.makeText(
                this,
                "Wybierz dostępny folder na pendrivie.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val queue =
            episodes
                .filter {
                    it.number in selectedEpisodeNumbers &&
                        it.url.isNotBlank() &&
                        it.number !in downloadedEpisodeNumbers
                }
                .sortedBy {
                    it.number
                }

        if (
            queue.isEmpty()
        ) {
            Toast.makeText(
                this,
                "Nie ma odcinków do pobrania.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val phoneFree =
            StatFs(
                (
                    getExternalFilesDir(
                        null
                    )
                        ?: filesDir
                    )
                    .absolutePath
            )
                .availableBytes

        if (
            phoneFree <
            MIN_PHONE_WORK_SPACE
        ) {
            AlertDialog.Builder(this)
                .setTitle(
                    "Za mało wolnego miejsca w telefonie"
                )
                .setMessage(
                    "Aplikacja potrzebuje około 2,5 GB wolnego miejsca " +
                        "na jeden tymczasowy odcinek."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

            return
        }

        persistQueue(
            queue
        )

        val intent =
            Intent(
                this,
                DownloadService::class.java
            ).apply {
                action =
                    DownloadService.ACTION_START
            }

        ContextCompat.startForegroundService(
            this,
            intent
        )

        selectedEpisodeNumbers
            .clear()

        restoreDownloadState()
        rebuildEpisodesUi()
    }

    private fun stopDownloads() {
        val intent =
            Intent(
                this,
                DownloadService::class.java
            ).apply {
                action =
                    DownloadService.ACTION_STOP
            }

        startService(
            intent
        )

        currentDownloadText.text =
            "Zatrzymywanie..."
    }

    private fun persistQueue(
        queue: List<Episode>
    ) {
        val array =
            JSONArray()

        queue.forEach {
                episode ->

            array.put(
                JSONObject()
                    .put(
                        "number",
                        episode.number
                    )
                    .put(
                        "url",
                        episode.url
                    )
                    .put(
                        "seriesKey",
                        currentSeries().key
                    )
                    .put(
                        "filePrefix",
                        currentSeries().filePrefix
                    )
            )
        }

        prefs.edit()
            .putString(
                DownloadService.KEY_QUEUE_JSON,
                array.toString()
            )
            .putString(
                DownloadService.KEY_CURRENT_STATUS,
                "Oczekuje"
            )
            .putInt(
                DownloadService.KEY_CURRENT_PROGRESS,
                0
            )
            .putInt(
                DownloadService.KEY_CURRENT_EPISODE,
                -1
            )
            .apply()
    }

    private fun restoreDownloadState() {
        val queue =
            loadPersistedQueue()

        val currentEpisode =
            prefs.getInt(
                DownloadService.KEY_CURRENT_EPISODE,
                -1
            )

        val progress =
            prefs.getInt(
                DownloadService.KEY_CURRENT_PROGRESS,
                0
            )

        val status =
            prefs.getString(
                DownloadService.KEY_CURRENT_STATUS,
                ""
            ).orEmpty()

        currentProgressBar.progress =
            progress.coerceIn(
                0,
                100
            )

        currentDownloadText.text =
            when {
                currentEpisode > 0 &&
                    status.isNotBlank() ->
                    "Odc. $currentEpisode: $status"

                queue.isNotEmpty() ->
                    "Kolejka oczekuje: ${queue.size} odc."

                status == "Gotowe" ->
                    "Gotowe ✓ Odcinki są na pendrive. Możesz go odłączyć i podłączyć do telewizora."

                else ->
                    "Brak aktywnego pobierania"
            }

        queueProgressText.text =
            if (
                queue.isNotEmpty()
            ) {
                "W kolejce: ${queue.size}"
            } else {
                "Kolejka: 0 / 0"
            }

        updateButtonsFromPersistedState()
        updateDownloadProgressVisibility()
    }

    private fun loadPersistedQueue():
        List<Pair<Int, String>> {

        val raw =
            prefs.getString(
                DownloadService.KEY_QUEUE_JSON,
                "[]"
            ) ?: "[]"

        return try {
            val array =
                JSONArray(
                    raw
                )

            buildList {
                for (
                    i
                    in 0
                    until array.length()
                ) {
                    val obj =
                        array.getJSONObject(
                            i
                        )

                    val number =
                        obj.optInt(
                            "number",
                            -1
                        )

                    val url =
                        obj.optString(
                            "url",
                            ""
                        )

                    if (
                        number > 0 &&
                        url.isNotBlank()
                    ) {
                        add(
                            number
                                to url
                        )
                    }
                }
            }

        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isQueueActive():
        Boolean {

        val queue =
            loadPersistedQueue()

        val status =
            prefs.getString(
                DownloadService.KEY_CURRENT_STATUS,
                ""
            ).orEmpty()

        return queue.isNotEmpty() &&
            status !in setOf(
                "Błąd",
                "Zatrzymano",
                "Gotowe"
            )
    }

    private fun updateButtonsFromPersistedState() {

        val queue =
            loadPersistedQueue()

        val active =
            isQueueActive()

        downloadButton.isEnabled =
            engineReady &&
                !active &&
                treeUri != null &&
                selectedEpisodeNumbers
                    .isNotEmpty()

        stopButton.isEnabled =
            active

        deleteButton.isEnabled =
            !active &&
                downloadedEpisodeNumbers
                    .isNotEmpty()

        updateDownloadProgressVisibility()
    }

    private fun updateDownloadProgressVisibility() {
        val active =
            isQueueActive()

        val visibility =
            if (active) {
                View.VISIBLE
            } else {
                View.GONE
            }

        stopButton.visibility =
            visibility

        currentDownloadText.visibility =
            visibility

        currentProgressBar.visibility =
            visibility

        queueProgressText.visibility =
            visibility

        findViewById<TextView>(
            R.id.downloadProgressTitle
        ).visibility =
            visibility
    }

    private fun currentUsbRoot():
        DocumentFile? {

        val uri =
            treeUri
                ?: return null

        return try {
            DocumentFile.fromTreeUri(
                this,
                uri
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun playFile(
        file: DocumentFile
    ) {
        val mime =
            file.type
                ?: contentResolver
                    .getType(
                        file.uri
                    )
                ?: "video/*"

        val intent =
            Intent(
                Intent.ACTION_VIEW
            ).apply {

                setDataAndType(
                    file.uri,
                    mime
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        try {
            startActivity(
                intent
            )
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Nie znaleziono aplikacji do odtwarzania filmu.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmDeleteSelected() {

        if (
            isQueueActive()
        ) {
            Toast.makeText(
                this,
                "Najpierw zakończ pobieranie.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (
            selectedDownloadedUris
                .isEmpty()
        ) {
            Toast.makeText(
                this,
                "Zaznacz pliki do usunięcia.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        AlertDialog.Builder(this)
            .setTitle(
                "Usunąć obejrzane odcinki?"
            )
            .setMessage(
                "Zaznaczone odcinki (${selectedDownloadedUris.size}) zostaną trwale usunięte z pendrive."
            )
            .setNegativeButton(
                "Anuluj",
                null
            )
            .setPositiveButton(
                "Usuń"
            ) { _, _ ->
                deleteSelectedFiles()
            }
            .show()
    }

    private fun deleteSelectedFiles() {

        var deleted =
            0

        var errors =
            0

        val targets =
            selectedDownloadedUris
                .toList()

        for (
            uriText
            in targets
        ) {
            try {
                val file =
                    DocumentFile
                        .fromSingleUri(
                            this,
                            Uri.parse(
                                uriText
                            )
                        )

                if (
                    file != null &&
                    file.exists() &&
                    file.delete()
                ) {
                    deleted++
                } else {
                    errors++
                }

            } catch (_: Exception) {
                errors++
            }
        }

        Toast.makeText(
            this,
            if (errors == 0) "Usunięto $deleted odc." else "Usunięto: $deleted • błędy: $errors",
            Toast.LENGTH_LONG
        ).show()

        refreshUsb()
    }

    private fun extractEpisodeNumber(
        name: String
    ): Int? {

        val preferred =
            Regex(
                """(?i)(?:odc(?:inek)?[\s._-]*)(\d{3,5})"""
            )
                .find(
                    name
                )
                ?.groupValues
                ?.getOrNull(
                    1
                )
                ?.toIntOrNull()

        if (
            preferred != null
        ) {
            return preferred
        }

        return Regex(
            """\b(1\d{3}|2\d{3})\b"""
        )
            .find(
                name
            )
            ?.groupValues
            ?.getOrNull(
                1
            )
            ?.toIntOrNull()
    }

    private fun getStorageSpace(
        tree: Uri
    ):
        Pair<Long, Long>? {

        return try {
            val documentId =
                DocumentsContract
                    .getTreeDocumentId(
                        tree
                    )

            val volumeId =
                documentId
                    .substringBefore(
                        ":"
                    )

            val storageManager =
                getSystemService(
                    android.os.storage.StorageManager::class.java
                )

            val volume =
                storageManager
                    .storageVolumes
                    .firstOrNull {
                            storageVolume ->

                        when {
                            volumeId.equals(
                                "primary",
                                ignoreCase = true
                            ) ->
                                storageVolume.isPrimary

                            storageVolume.uuid != null ->
                                storageVolume.uuid
                                    .equals(
                                        volumeId,
                                        ignoreCase = true
                                    )

                            else ->
                                false
                        }
                    }
                    ?: return null

            val directory =
                volume.directory
                    ?: return null

            val stat =
                StatFs(
                    directory.absolutePath
                )

            Pair(
                stat.availableBytes,
                stat.totalBytes
            )

        } catch (_: Exception) {
            null
        }
    }

    private fun formatBytes(
        bytes: Long
    ): String {

        if (
            bytes < 1024
        ) {
            return "$bytes B"
        }

        val units =
            arrayOf(
                "KB",
                "MB",
                "GB",
                "TB"
            )

        var value =
            bytes.toDouble() / 1024.0

        var unitIndex =
            0

        while (
            value >= 1024.0 &&
            unitIndex <
            units.lastIndex
        ) {
            value /=
                1024.0

            unitIndex++
        }

        return String.format(
            Locale(
                "pl",
                "PL"
            ),
            "%.2f %s",
            value,
            units[
                unitIndex
            ]
        )
    }

    companion object {

        private const val KEY_SERIES_URL = "series_url"
        private const val KEY_SELECTED_SERIES = "selected_series"
        private const val KEY_CATALOG_JSON = "catalog_json"
        private const val KEY_START_EPISODE = "start_episode"
        private val SERIES_OPTIONS =
            listOf(
                SeriesOption(
                    key = "mjak",
                    title = "M jak miłość",
                    url = "https://vod.tvp.pl/seriale,18/m-jak-milosc-odcinki,274703",
                    filePrefix = ""
                ),
                SeriesOption(
                    key = "klan",
                    title = "Klan",
                    url = "https://vod.tvp.pl/seriale,18/klan-odcinki,273586",
                    filePrefix = "Klan - "
                ),
                SeriesOption(
                    key = "nadobre",
                    title = "Na dobre i na złe",
                    url = "https://vod.tvp.pl/seriale,18/na-dobre-i-na-zle-odcinki,273721",
                    filePrefix = "Na dobre i na zle - "
                )
            )
        private const val TVP_API_BASE = "https://vod.tvp.pl/api/products"

        private const val ESTIMATED_EPISODE_BYTES =
            1_600_000_000L

        private const val MIN_PHONE_WORK_SPACE =
            2_500_000_000L
    }
}
