package pl.szynolandia.mjakmiloscusb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

class DownloadService : Service() {

    data class QueueItem(
        val number: Int,
        val url: String
    )

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var downloadJob: Job? = null

    @Volatile
    private var stopRequested = false

    @Volatile
    private var currentProcessId: String? = null

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private val workDir: File by lazy {
        (getExternalFilesDir(null) ?: filesDir)
            .resolve("vod_work")
            .also { it.mkdirs() }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_STOP -> {
                stopCurrentDownload()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                if (downloadJob?.isActive != true) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(
                            title = "VOD USB",
                            text = "Przygotowanie kolejki...",
                            progress = 0,
                            indeterminate = true
                        )
                    )

                    startQueue()
                }
            }

            else -> {
                // If Android recreates the service after process death,
                // continue a persisted queue if one exists.
                if (
                    downloadJob?.isActive != true &&
                    loadPendingQueue().isNotEmpty()
                ) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(
                            title = "VOD USB",
                            text = "Wznawianie kolejki...",
                            progress = 0,
                            indeterminate = true
                        )
                    )

                    startQueue()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startQueue() {
        downloadJob = serviceScope.launch {
            stopRequested = false

            val queue = loadPendingQueue().toMutableList()

            if (queue.isEmpty()) {
                finishService("Brak odcinków w kolejce.")
                return@launch
            }

            initEngines()

            var completed = 0

            while (
                queue.isNotEmpty() &&
                !stopRequested
            ) {
                val item = queue.first()

                persistState(
                    queue = queue,
                    currentEpisode = item.number,
                    progress = 0,
                    status = "Pobieranie"
                )

                sendState(
                    episode = item.number,
                    progress = 0,
                    status = "Pobieranie",
                    completed = completed,
                    total = completed + queue.size
                )

                updateNotification(
                    "Odcinek ${item.number}",
                    "Pobieranie...",
                    0,
                    true
                )

                val success =
                    downloadOneEpisode(item)

                if (stopRequested) {
                    break
                }

                if (success) {
                    queue.removeAt(0)
                    completed++

                    persistQueue(queue)

                    sendState(
                        episode = item.number,
                        progress = 100,
                        status = "Gotowe",
                        completed = completed,
                        total = completed + queue.size
                    )
                } else {
                    // Keep failed item in persisted queue, but stop automatic loop.
                    persistState(
                        queue = queue,
                        currentEpisode = item.number,
                        progress = 0,
                        status = "Błąd"
                    )

                    updateNotification(
                        "Błąd odcinka ${item.number}",
                        "Otwórz aplikację, aby spróbować ponownie.",
                        0,
                        false
                    )

                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                    return@launch
                }
            }

            if (stopRequested) {
                persistState(
                    queue = queue,
                    currentEpisode = -1,
                    progress = 0,
                    status = "Zatrzymano"
                )

                updateNotification(
                    "VOD USB",
                    "Pobieranie zatrzymane.",
                    0,
                    false
                )
            } else {
                clearQueueState()

                updateNotification(
                    "VOD USB",
                    "Kolejka zakończona.",
                    100,
                    false
                )
            }

            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun initEngines() {
        YoutubeDL.getInstance().init(applicationContext)

        try {
            FFmpeg.getInstance().init(applicationContext)
        } catch (_: Exception) {
        }
    }

    private fun downloadOneEpisode(
        item: QueueItem
    ): Boolean {

        cleanWorkDir()

        val baseName =
            "odc. ${item.number}"

        val processId =
            "vod-bg-${item.number}-${System.currentTimeMillis()}"

        currentProcessId = processId

        val request =
            YoutubeDLRequest(item.url).apply {

                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--newline")

                addOption(
                    "-f",
                    if (isTvpUrl(item.url)) {
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

            YoutubeDL.getInstance().execute(
                request,
                processId
            ) { progress, etaSeconds, _ ->

                val safeProgress =
                    progress.toInt()
                        .coerceIn(0, 100)

                persistProgress(
                    item.number,
                    safeProgress,
                    "Pobieranie"
                )

                sendState(
                    episode = item.number,
                    progress = safeProgress,
                    status = if (etaSeconds >= 0) {
                        "Pobieranie • ETA ${etaSeconds}s"
                    } else {
                        "Pobieranie"
                    },
                    completed = -1,
                    total = -1
                )

                updateNotification(
                    "Odcinek ${item.number}",
                    if (etaSeconds >= 0) {
                        "$safeProgress% • ETA ${etaSeconds}s"
                    } else {
                        "$safeProgress%"
                    },
                    safeProgress,
                    false
                )

                kotlin.Unit
            }

            if (stopRequested) {
                cleanWorkDir()
                return false
            }

            val output =
                findFinishedOutput(baseName)
                    ?: return false

            sendState(
                episode = item.number,
                progress = 0,
                status = "Kopiowanie na pendrive",
                completed = -1,
                total = -1
            )

            updateNotification(
                "Odcinek ${item.number}",
                "Kopiowanie na pendrive...",
                0,
                true
            )

            val copied =
                copyFileToUsb(
                    source = output,
                    episodeNumber = item.number
                )

            cleanWorkDir()

            copied

        } catch (_: Exception) {
            cleanWorkDir()
            false

        } finally {
            currentProcessId = null
        }
    }

    private fun copyFileToUsb(
        source: File,
        episodeNumber: Int
    ): Boolean {

        val treeText =
            prefs.getString(KEY_TREE_URI, null)
                ?: return false

        val root =
            DocumentFile.fromTreeUri(
                this,
                Uri.parse(treeText)
            ) ?: return false

        if (
            !root.exists() ||
            !root.canWrite()
        ) {
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

            source.inputStream()
                .buffered()
                .use { input ->

                    contentResolver
                        .openOutputStream(
                            target.uri,
                            "w"
                        )
                        ?.buffered()
                        ?.use { output ->

                            val buffer =
                                ByteArray(
                                    1024 * 1024
                                )

                            while (true) {

                                if (stopRequested) {
                                    throw InterruptedException()
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

                                persistProgress(
                                    episodeNumber,
                                    progress,
                                    "Kopiowanie na USB"
                                )

                                sendState(
                                    episode = episodeNumber,
                                    progress = progress,
                                    status = "Kopiowanie na USB",
                                    completed = -1,
                                    total = -1
                                )

                                updateNotification(
                                    "Odcinek $episodeNumber",
                                    "Kopiowanie na USB • $progress%",
                                    progress,
                                    false
                                )
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

    private fun stopCurrentDownload() {
        stopRequested = true

        currentProcessId?.let { id ->
            try {
                YoutubeDL.getInstance()
                    .destroyProcessById(id)
            } catch (_: Exception) {
            }
        }

        sendState(
            episode = -1,
            progress = 0,
            status = "Zatrzymywanie...",
            completed = -1,
            total = -1
        )
    }

    private fun isTvpUrl(
        url: String
    ): Boolean {

        return try {
            val host =
                Uri.parse(url)
                    .host
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()

            host == "tvp.pl" ||
                host.endsWith(".tvp.pl")

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

    private fun loadPendingQueue(): List<QueueItem> {
        val raw =
            prefs.getString(
                KEY_QUEUE_JSON,
                "[]"
            ) ?: "[]"

        return try {
            val array = JSONArray(raw)

            buildList {
                for (i in 0 until array.length()) {
                    val obj =
                        array.getJSONObject(i)

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
                            QueueItem(
                                number,
                                url
                            )
                        )
                    }
                }
            }

        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistQueue(
        queue: List<QueueItem>
    ) {
        val array = JSONArray()

        queue.forEach { item ->
            array.put(
                JSONObject()
                    .put(
                        "number",
                        item.number
                    )
                    .put(
                        "url",
                        item.url
                    )
            )
        }

        prefs.edit()
            .putString(
                KEY_QUEUE_JSON,
                array.toString()
            )
            .apply()
    }

    private fun persistState(
        queue: List<QueueItem>,
        currentEpisode: Int,
        progress: Int,
        status: String
    ) {
        persistQueue(queue)

        prefs.edit()
            .putInt(
                KEY_CURRENT_EPISODE,
                currentEpisode
            )
            .putInt(
                KEY_CURRENT_PROGRESS,
                progress
            )
            .putString(
                KEY_CURRENT_STATUS,
                status
            )
            .apply()
    }

    private fun persistProgress(
        episode: Int,
        progress: Int,
        status: String
    ) {
        prefs.edit()
            .putInt(
                KEY_CURRENT_EPISODE,
                episode
            )
            .putInt(
                KEY_CURRENT_PROGRESS,
                progress
            )
            .putString(
                KEY_CURRENT_STATUS,
                status
            )
            .apply()
    }

    private fun clearQueueState() {
        prefs.edit()
            .putString(
                KEY_QUEUE_JSON,
                "[]"
            )
            .putInt(
                KEY_CURRENT_EPISODE,
                -1
            )
            .putInt(
                KEY_CURRENT_PROGRESS,
                0
            )
            .putString(
                KEY_CURRENT_STATUS,
                "Gotowe"
            )
            .apply()
    }

    private fun sendState(
        episode: Int,
        progress: Int,
        status: String,
        completed: Int,
        total: Int
    ) {
        val intent =
            Intent(
                ACTION_STATE
            ).apply {

                setPackage(
                    packageName
                )

                putExtra(
                    EXTRA_EPISODE,
                    episode
                )

                putExtra(
                    EXTRA_PROGRESS,
                    progress
                )

                putExtra(
                    EXTRA_STATUS,
                    status
                )

                putExtra(
                    EXTRA_COMPLETED,
                    completed
                )

                putExtra(
                    EXTRA_TOTAL,
                    total
                )
            }

        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Pobieranie VOD",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description =
                        "Postęp pobierania odcinków VOD"
                }

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(
                channel
            )
        }
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ) = NotificationCompat.Builder(
        this,
        CHANNEL_ID
    )
        .setSmallIcon(
            android.R.drawable.stat_sys_download
        )
        .setContentTitle(title)
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(
            downloadJob?.isActive == true ||
                indeterminate
        )
        .setProgress(
            100,
            progress.coerceIn(0, 100),
            indeterminate
        )
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                1,
                Intent(
                    this,
                    MainActivity::class.java
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            android.R.drawable.ic_media_pause,
            "Zatrzymaj",
            PendingIntent.getService(
                this,
                2,
                Intent(
                    this,
                    DownloadService::class.java
                ).apply {
                    action =
                        ACTION_STOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun updateNotification(
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ) {
        getSystemService(
            NotificationManager::class.java
        ).notify(
            NOTIFICATION_ID,
            buildNotification(
                title,
                text,
                progress,
                indeterminate
            )
        )
    }

    private fun finishService(
        message: String
    ) {
        updateNotification(
            "VOD USB",
            message,
            0,
            false
        )

        stopForeground(
            STOP_FOREGROUND_DETACH
        )

        stopSelf()
    }

    companion object {

        const val ACTION_START =
            "pl.szynolandia.mjakmiloscusb.action.START_DOWNLOADS"

        const val ACTION_STOP =
            "pl.szynolandia.mjakmiloscusb.action.STOP_DOWNLOADS"

        const val ACTION_STATE =
            "pl.szynolandia.mjakmiloscusb.action.DOWNLOAD_STATE"

        const val EXTRA_EPISODE =
            "episode"

        const val EXTRA_PROGRESS =
            "progress"

        const val EXTRA_STATUS =
            "status"

        const val EXTRA_COMPLETED =
            "completed"

        const val EXTRA_TOTAL =
            "total"

        const val PREFS_NAME =
            "vod_usb"

        const val KEY_TREE_URI =
            "tree_uri"

        const val KEY_QUEUE_JSON =
            "download_queue_json"

        const val KEY_CURRENT_EPISODE =
            "download_current_episode"

        const val KEY_CURRENT_PROGRESS =
            "download_current_progress"

        const val KEY_CURRENT_STATUS =
            "download_current_status"

        private const val CHANNEL_ID =
            "vod_downloads"

        private const val NOTIFICATION_ID =
            3001
    }
}
