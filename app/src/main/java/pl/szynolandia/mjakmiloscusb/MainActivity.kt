package pl.szynolandia.mjakmiloscusb

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    data class Episode(
        val number: Int,
        var url: String
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

    private val prefs by lazy {
        getSharedPreferences("vod_usb", MODE_PRIVATE)
    }

    private val episodes = mutableListOf<Episode>()
    private val selectedEpisodeNumbers = mutableSetOf<Int>()
    private val selectedDownloadedUris = mutableSetOf<String>()
    private val downloadedEpisodeNumbers = mutableSetOf<Int>()

    private var treeUri: Uri? = null

    @Volatile
    private var engineReady = false

    @Volatile
    private var isDownloading = false

    @Volatile
    private var stopRequested = false

    @Volatile
    private var currentProcessId: String? = null

    private val workDir: File by lazy {
        (getExternalFilesDir(null) ?: filesDir)
            .resolve("vod_work")
            .also { it.mkdirs() }
    }

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }

                prefs.edit()
                    .putString(KEY_TREE_URI, uri.toString())
                    .apply()

                treeUri = uri
                refreshUsb()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        titleText = findViewById(R.id.titleText)
        engineStatusText = findViewById(R.id.engineStatusText)
        usbStatusText = findViewById(R.id.usbStatusText)
        spaceText = findViewById(R.id.spaceText)
        downloadedSummaryText = findViewById(R.id.downloadedSummaryText)
        downloadSelectionText = findViewById(R.id.downloadSelectionText)
        episodesContainer = findViewById(R.id.episodesContainer)
        downloadedContainer = findViewById(R.id.downloadedContainer)
        deleteButton = findViewById(R.id.deleteButton)
        downloadButton = findViewById(R.id.downloadButton)
        stopButton = findViewById(R.id.stopButton)
        currentDownloadText = findViewById(R.id.currentDownloadText)
        currentProgressBar = findViewById(R.id.currentProgressBar)
        queueProgressText = findViewById(R.id.queueProgressText)

        findViewById<Button>(R.id.selectUsbButton).setOnClickListener {
            folderPicker.launch(treeUri)
        }

        findViewById<Button>(R.id.refreshButton).setOnClickListener {
            refreshUsb()
        }

        findViewById<Button>(R.id.editLinksButton).setOnClickListener {
            showLinksEditor()
        }

        downloadButton.setOnClickListener {
            startSelectedDownloads()
        }

        stopButton.setOnClickListener {
            stopDownloads()
        }

        deleteButton.setOnClickListener {
            confirmDeleteSelected()
        }

        treeUri = prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)

        loadEpisodes()
        refreshUsb()
        initDownloader()
    }

    override fun onResume() {
        super.onResume()
        if (!isDownloading) {
            refreshUsb()
        }
    }

    private fun initDownloader() {
        engineStatusText.text = "Silnik pobierania: inicjalizacja..."
        downloadButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                try {
                    FFmpeg.getInstance().init(applicationContext)
                } catch (_: Exception) {
                }

                withContext(Dispatchers.Main) {
                    engineStatusText.text = "Silnik pobierania: aktualizacja yt-dlp..."
                }

                try {
                    YoutubeDL.getInstance()
                        .updateYoutubeDL(applicationContext, UpdateChannel.STABLE)
                } catch (_: Exception) {
                    // Bundled yt-dlp can still be used if update fails.
                }

                val version = try {
                    YoutubeDL.getInstance().version(applicationContext) ?: "?"
                } catch (_: Exception) {
                    "?"
                }

                engineReady = true

                withContext(Dispatchers.Main) {
                    engineStatusText.text = "Silnik pobierania: gotowy • yt-dlp $version"
                    updateDownloadButtonState()
                }
            } catch (e: Exception) {
                engineReady = false

                withContext(Dispatchers.Main) {
                    engineStatusText.text =
                        "Silnik pobierania: błąd • ${e.message?.take(120)}"
                    updateDownloadButtonState()
                }
            }
        }
    }

    private fun loadEpisodes() {
        episodes.clear()

        val raw = assets.open("episodes.json")
            .bufferedReader()
            .use { it.readText() }

        val json = JSONObject(raw)
        titleText.text = json.optString("series", "VOD")

        val array = json.getJSONArray("episodes")

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val number = item.getInt("number")
            val assetUrl = item.optString("url", "")
            val savedUrl = prefs.getString("episode_url_$number", null)

            episodes += Episode(
                number = number,
                url = savedUrl ?: assetUrl
            )
        }

        rebuildEpisodesUi()
    }

    private fun rebuildEpisodesUi() {
        episodesContainer.removeAllViews()
        selectedEpisodeNumbers.clear()

        for (episode in episodes.sortedBy { it.number }) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 5, 0, 5)
            }

            val alreadyDownloaded =
                episode.number in downloadedEpisodeNumbers

            val checkBox = CheckBox(this).apply {
                text = "Odcinek ${episode.number}"
                textSize = 18f
                isChecked = false
                isEnabled = !alreadyDownloaded && episode.url.isNotBlank()

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedEpisodeNumbers.add(episode.number)
                    } else {
                        selectedEpisodeNumbers.remove(episode.number)
                    }
                    updateEpisodeSelectionSummary()
                }
            }

            val status = TextView(this).apply {
                textSize = 14f
                text = when {
                    alreadyDownloaded -> "Pobrany ✓"
                    episode.url.isBlank() -> "Brak linku"
                    else -> "Do pobrania"
                }
            }

            row.addView(checkBox)
            row.addView(status)
            episodesContainer.addView(row)
        }

        updateEpisodeSelectionSummary()
        updateDownloadButtonState()
    }

    private fun updateEpisodeSelectionSummary() {
        val count = selectedEpisodeNumbers.size
        val estimateBytes = count * ESTIMATED_EPISODE_BYTES

        downloadSelectionText.text =
            if (count == 0) {
                "Zaznaczono: 0 odcinków"
            } else {
                "Zaznaczono: $count • orientacyjnie ${formatBytes(estimateBytes)}"
            }

        updateDownloadButtonState()
    }

    private fun updateDownloadButtonState() {
        if (!::downloadButton.isInitialized) {
            return
        }

        downloadButton.isEnabled =
            engineReady &&
                !isDownloading &&
                treeUri != null &&
                selectedEpisodeNumbers.isNotEmpty()
    }

    private fun showLinksEditor() {
        val input = EditText(this).apply {
            minLines = 10
            gravity = Gravity.TOP
            setPadding(24, 18, 24, 18)

            setText(
                episodes.sortedBy { it.number }
                    .joinToString("\n") { episode ->
                        "${episode.number}|${episode.url}"
                    }
            )
        }

        AlertDialog.Builder(this)
            .setTitle("Linki odcinków")
            .setMessage(
                "Jedna linia = numer|link\n\n" +
                    "Przykład:\n1801|https://vod.tvp.pl/..."
            )
            .setView(input)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz") { _, _ ->
                saveLinksFromText(input.text.toString())
            }
            .show()
    }

    private fun saveLinksFromText(text: String) {
        val byNumber = mutableMapOf<Int, String>()

        for (line in text.lines()) {
            val trimmed = line.trim()

            if (trimmed.isBlank() || "|" !in trimmed) {
                continue
            }

            val number =
                trimmed.substringBefore("|")
                    .trim()
                    .toIntOrNull()
                    ?: continue

            val url =
                trimmed.substringAfter("|")
                    .trim()

            byNumber[number] = url
        }

        for (episode in episodes) {
            if (episode.number in byNumber) {
                episode.url = byNumber[episode.number].orEmpty()

                prefs.edit()
                    .putString(
                        "episode_url_${episode.number}",
                        episode.url
                    )
                    .apply()
            }
        }

        rebuildEpisodesUi()

        Toast.makeText(
            this,
            "Linki zapisane.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun refreshUsb() {
        selectedDownloadedUris.clear()
        downloadedEpisodeNumbers.clear()
        downloadedContainer.removeAllViews()

        val uri = treeUri

        if (uri == null) {
            usbStatusText.text = "Pendrive: nie wybrano"
            spaceText.text = "Wolne miejsce: —"
            downloadedSummaryText.text = "Na pendrive: 0 odcinków"
            deleteButton.isEnabled = false
            rebuildEpisodesUi()
            return
        }

        val root =
            DocumentFile.fromTreeUri(this, uri)

        if (
            root == null ||
            !root.exists() ||
            !root.canRead()
        ) {
            usbStatusText.text =
                "Pendrive: niedostępny — podłącz USB i kliknij Odśwież"
            spaceText.text = "Wolne miejsce: —"
            downloadedSummaryText.text = "Na pendrive: 0 odcinków"
            deleteButton.isEnabled = false
            rebuildEpisodesUi()
            return
        }

        usbStatusText.text =
            "Pendrive: gotowy • ${root.name ?: "USB"}"

        val space = getStorageSpace(uri)

        spaceText.text =
            if (space != null) {
                "Wolne miejsce: ${formatBytes(space.first)} z ${formatBytes(space.second)}"
            } else {
                "Wolne miejsce: Android nie udostępnił danych"
            }

        val videos =
            root.listFiles()
                .filter { file ->
                    file.isFile &&
                        file.name
                            ?.lowercase(Locale.ROOT)
                            ?.let {
                                it.endsWith(".mp4") ||
                                    it.endsWith(".mkv") ||
                                    it.endsWith(".webm") ||
                                    it.endsWith(".m4v")
                            } == true
                }
                .sortedWith(
                    compareBy<DocumentFile> {
                        extractEpisodeNumber(it.name ?: "")
                            ?: Int.MAX_VALUE
                    }.thenBy {
                        it.name ?: ""
                    }
                )

        for (file in videos) {
            extractEpisodeNumber(file.name ?: "")
                ?.let(downloadedEpisodeNumbers::add)
        }

        val totalBytes =
            videos.sumOf { it.length() }

        downloadedSummaryText.text =
            "Na pendrive: ${videos.size} odcinków • ${formatBytes(totalBytes)}"

        if (videos.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Brak filmów w wybranym folderze."
                textSize = 16f
                setPadding(4, 12, 4, 12)
            }

            downloadedContainer.addView(empty)
            deleteButton.isEnabled = false
        } else {
            for (file in videos) {
                addDownloadedFileRow(file)
            }

            deleteButton.isEnabled = !isDownloading
        }

        rebuildEpisodesUi()
    }

    private fun addDownloadedFileRow(file: DocumentFile) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 5, 0, 5)
        }

        val episode =
            extractEpisodeNumber(file.name ?: "")

        val displayName =
            if (episode != null) {
                "Odc. $episode • ${formatBytes(file.length())}"
            } else {
                "${file.name ?: "Film"} • ${formatBytes(file.length())}"
            }

        val checkBox = CheckBox(this).apply {
            text = displayName
            textSize = 17f

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selectedDownloadedUris.add(file.uri.toString())
                } else {
                    selectedDownloadedUris.remove(file.uri.toString())
                }
            }
        }

        val playButton = Button(this).apply {
            text = "▶"

            setOnClickListener {
                playFile(file)
            }
        }

        row.addView(checkBox)
        row.addView(playButton)

        downloadedContainer.addView(row)
    }

    private fun startSelectedDownloads() {
        if (isDownloading) {
            return
        }

        if (!engineReady) {
            Toast.makeText(
                this,
                "Silnik yt-dlp nie jest jeszcze gotowy.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val root = currentUsbRoot()

        if (root == null || !root.canWrite()) {
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
                .sortedBy { it.number }

        if (queue.isEmpty()) {
            Toast.makeText(
                this,
                "Nie ma odcinków do pobrania.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val phoneFree =
            StatFs(workDir.absolutePath).availableBytes

        if (phoneFree < MIN_PHONE_WORK_SPACE) {
            AlertDialog.Builder(this)
                .setTitle("Za mało wolnego miejsca w telefonie")
                .setMessage(
                    "Aplikacja potrzebuje około 2,5 GB wolnego miejsca na jeden " +
                        "tymczasowy odcinek. Po skopiowaniu na pendrive plik jest usuwany."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        isDownloading = true
        stopRequested = false

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        downloadButton.isEnabled = false
        stopButton.isEnabled = true
        deleteButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            var completed = 0

            for (episode in queue) {
                if (stopRequested) {
                    break
                }

                withContext(Dispatchers.Main) {
                    currentProgressBar.progress = 0
                    currentDownloadText.text =
                        "Odc. ${episode.number}: przygotowanie..."
                    queueProgressText.text =
                        "Kolejka: $completed / ${queue.size}"
                }

                val success =
                    downloadOneEpisode(episode)

                if (success) {
                    completed++
                }

                if (stopRequested) {
                    break
                }
            }

            withContext(Dispatchers.Main) {
                isDownloading = false
                stopButton.isEnabled = false

                window.clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )

                currentDownloadText.text =
                    if (stopRequested) {
                        "Pobieranie zatrzymane."
                    } else {
                        "Kolejka zakończona."
                    }

                queueProgressText.text =
                    "Kolejka: $completed / ${queue.size}"

                refreshUsb()
                updateDownloadButtonState()
            }
        }
    }

    private suspend fun downloadOneEpisode(
        episode: Episode
    ): Boolean {

        cleanWorkDir()

        val processId =
            "vod-episode-${episode.number}"

        currentProcessId = processId

        val baseName =
            "odc. ${episode.number}"

        val request =
            YoutubeDLRequest(episode.url).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--newline")

                addOption(
                    "-f",
                    if (isTvpUrl(episode.url)) {
                        "bestvideo+audio0-Polski/bestvideo+bestaudio/best"
                    } else {
                        "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                    }
                )

                addOption(
                    "--merge-output-format",
                    "mp4"
                )

                addOption(
                    "-o",
                    "${workDir.absolutePath}/$baseName.%(ext)s"
                )
            }

        return try {
            withContext(Dispatchers.Main) {
                currentDownloadText.text =
                    "Odc. ${episode.number}: pobieranie..."
            }

            YoutubeDL.getInstance().execute(
                request,
                processId
            ) { progress, etaSeconds, line ->

                runOnUiThread {
                    val safeProgress =
                        progress.toInt()
                            .coerceIn(0, 100)

                    currentProgressBar.progress =
                        safeProgress

                    currentDownloadText.text =
                        if (etaSeconds >= 0) {
                            "Odc. ${episode.number}: " +
                                "$safeProgress% • ETA ${etaSeconds}s"
                        } else {
                            "Odc. ${episode.number}: $safeProgress%"
                        }
                }

                kotlin.Unit
            }

            if (stopRequested) {
                cleanWorkDir()
                return false
            }

            val output =
                findFinishedOutput(baseName)
                    ?: throw IllegalStateException(
                        "yt-dlp zakończył pracę, ale nie znaleziono gotowego pliku."
                    )

            withContext(Dispatchers.Main) {
                currentProgressBar.progress = 0
                currentDownloadText.text =
                    "Odc. ${episode.number}: kopiowanie na pendrive..."
            }

            val copied =
                copyFileToUsb(
                    source = output,
                    episodeNumber = episode.number
                )

            cleanWorkDir()

            if (!copied) {
                throw IllegalStateException(
                    "Nie udało się zapisać pliku na pendrive."
                )
            }

            true

        } catch (e: Exception) {
            cleanWorkDir()

            if (!stopRequested) {
                withContext(Dispatchers.Main) {
                    currentDownloadText.text =
                        "Odc. ${episode.number}: BŁĄD"

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Nie udało się pobrać odc. ${episode.number}")
                        .setMessage(
                            e.message?.take(500)
                                ?: "Nieznany błąd yt-dlp."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            false
        } finally {
            currentProcessId = null
        }
    }

    private fun isTvpUrl(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase(Locale.ROOT).orEmpty()
            host == "tvp.pl" || host.endsWith(".tvp.pl")
        } catch (_: Exception) {
            false
        }
    }

    private fun findFinishedOutput(
        baseName: String
    ): File? {

        val files =
            workDir.listFiles()
                ?.filter {
                    it.isFile &&
                        !it.name.endsWith(".part") &&
                        !it.name.endsWith(".ytdl")
                }
                .orEmpty()

        return files
            .filter {
                it.nameWithoutExtension == baseName
            }
            .maxByOrNull {
                it.length()
            }
            ?: files.maxByOrNull {
                it.length()
            }
    }

    private suspend fun copyFileToUsb(
        source: File,
        episodeNumber: Int
    ): Boolean {

        val root =
            currentUsbRoot()
                ?: return false

        if (!root.exists() || !root.canWrite()) {
            return false
        }

        val extension =
            source.extension
                .ifBlank { "mp4" }
                .lowercase(Locale.ROOT)

        val finalName =
            "odc. $episodeNumber.$extension"

        root.findFile(finalName)
            ?.delete()

        val mime =
            when (extension) {
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "m4v" -> "video/x-m4v"
                else -> "video/mp4"
            }

        val target =
            root.createFile(
                mime,
                finalName
            ) ?: return false

        val total =
            source.length()
                .coerceAtLeast(1L)

        var copied = 0L

        return try {
            source.inputStream().buffered().use { input ->
                contentResolver
                    .openOutputStream(
                        target.uri,
                        "w"
                    )
                    ?.buffered()
                    ?.use { output ->

                        val buffer =
                            ByteArray(1024 * 1024)

                        while (true) {
                            if (stopRequested) {
                                throw InterruptedException(
                                    "Pobieranie zatrzymane."
                                )
                            }

                            val read =
                                input.read(buffer)

                            if (read <= 0) {
                                break
                            }

                            output.write(
                                buffer,
                                0,
                                read
                            )

                            copied += read

                            val progress =
                                (
                                    copied * 100L / total
                                )
                                    .toInt()
                                    .coerceIn(0, 100)

                            withContext(Dispatchers.Main) {
                                currentProgressBar.progress =
                                    progress

                                currentDownloadText.text =
                                    "Odc. $episodeNumber: " +
                                        "zapis na USB $progress%"
                            }
                        }

                        output.flush()
                    } ?: return false
            }

            true

        } catch (_: Exception) {
            target.delete()
            false
        }
    }

    private fun stopDownloads() {
        if (!isDownloading) {
            return
        }

        stopRequested = true

        currentProcessId?.let { processId ->
            try {
                YoutubeDL.getInstance()
                    .destroyProcessById(processId)
            } catch (_: Exception) {
            }
        }

        stopButton.isEnabled = false

        Toast.makeText(
            this,
            "Zatrzymywanie...",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun cleanWorkDir() {
        workDir.listFiles()
            ?.forEach { file ->
                try {
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                } catch (_: Exception) {
                }
            }
    }

    private fun currentUsbRoot(): DocumentFile? {
        val uri =
            treeUri ?: return null

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
                ?: contentResolver.getType(file.uri)
                ?: "video/*"

        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    file.uri,
                    mime
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Nie znaleziono aplikacji do odtwarzania filmu.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmDeleteSelected() {
        if (isDownloading) {
            Toast.makeText(
                this,
                "Najpierw zakończ pobieranie.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (selectedDownloadedUris.isEmpty()) {
            Toast.makeText(
                this,
                "Zaznacz pliki do usunięcia.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Usunąć odcinki?")
            .setMessage(
                "Wybrane pliki (${selectedDownloadedUris.size}) " +
                    "zostaną usunięte z pendrive."
            )
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Usuń") { _, _ ->
                deleteSelectedFiles()
            }
            .show()
    }

    private fun deleteSelectedFiles() {
        var deleted = 0
        var errors = 0

        val targets =
            selectedDownloadedUris.toList()

        for (uriText in targets) {
            try {
                val file =
                    DocumentFile.fromSingleUri(
                        this,
                        Uri.parse(uriText)
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
            "Usunięto: $deleted • błędy: $errors",
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
                .find(name)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

        if (preferred != null) {
            return preferred
        }

        return Regex(
            """\b(1\d{3}|2\d{3})\b"""
        )
            .find(name)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun getStorageSpace(
        tree: Uri
    ): Pair<Long, Long>? {

        return try {
            val documentId =
                DocumentsContract
                    .getTreeDocumentId(tree)

            val volumeId =
                documentId.substringBefore(":")

            val storageManager =
                getSystemService(
                    android.os.storage.StorageManager::class.java
                )

            val volume =
                storageManager
                    .storageVolumes
                    .firstOrNull { storageVolume ->

                        when {
                            volumeId.equals(
                                "primary",
                                ignoreCase = true
                            ) ->
                                storageVolume.isPrimary

                            storageVolume.uuid != null ->
                                storageVolume.uuid.equals(
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

        if (bytes < 1024) {
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

        var unitIndex = 0

        while (
            value >= 1024.0 &&
            unitIndex < units.lastIndex
        ) {
            value /= 1024.0
            unitIndex++
        }

        return String.format(
            Locale("pl", "PL"),
            "%.2f %s",
            value,
            units[unitIndex]
        )
    }

    companion object {
        private const val KEY_TREE_URI =
            "tree_uri"

        private const val ESTIMATED_EPISODE_BYTES =
            1_600_000_000L

        private const val MIN_PHONE_WORK_SPACE =
            2_500_000_000L
    }
}
