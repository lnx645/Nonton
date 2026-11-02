package id.nonton.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import id.nonton.app.MainActivity
import id.nonton.app.NontonApp
import id.nonton.app.R
import id.nonton.app.core.DownloaderImpl
import id.nonton.app.data.DownloadDao
import id.nonton.app.data.DownloadEntity
import id.nonton.app.data.DownloadKind
import id.nonton.app.data.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dao: DownloadDao
    private var loopJob: Job? = null
    private var activeCall: Call? = null
    private var activeId: Long? = null

    override fun onCreate() {
        super.onCreate()
        dao = (application as NontonApp).container.database.downloadDao()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                if (id > 0) {
                    scope.launch { dao.cancel(id) }
                    if (activeId == id) {
                        activeCall?.cancel()
                    }
                }
                if (loopJob?.isActive != true) {
                    stopSelf()
                }
            }
            else -> {
                if (loopJob?.isActive != true) {
                    startForegroundWith("Menyiapkan unduhan…", 0, true)
                    startLoop()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startLoop() {
        loopJob = scope.launch {
            dao.resetDownloading()
            while (isActive) {
                val next = dao.nextQueued() ?: break
                download(next)
            }
            withContext(Dispatchers.Main) {
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private suspend fun download(item: DownloadEntity) {
        activeId = item.id
        dao.update(item.id, DownloadStatus.DOWNLOADING, 0, 0, -1, null, null)
        startForegroundWith("Mengunduh ${item.title}", 0, true)

        var target: Target? = null
        try {
            val request = Request.Builder()
                .url(item.mediaUrl)
                .header("User-Agent", DownloaderImpl.USER_AGENT)
                .build()

            val call = DownloaderImpl.client.newCall(request)
            activeCall = call

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Server mengembalikan HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Respons kosong")
                val total = body.contentLength()
                val currentTarget = createTarget(item)
                target = currentTarget
                if (total > 0) {
                    dao.update(item.id, DownloadStatus.DOWNLOADING, 0, 0, total, null, null)
                }

                body.byteStream().use { input ->
                    currentTarget.output.use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytes = 0L
                        var lastUpdate = 0L
                        while (true) {
                            if (!currentCoroutineContext().isActive) {
                                throw CancellationException("Unduhan dibatalkan")
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytes += read
                            currentTarget.bytes = bytes

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 400) {
                                val progress = if (total > 0) {
                                    ((bytes * 100) / total).toInt().coerceIn(0, 99)
                                } else {
                                    0
                                }
                                dao.update(
                                    item.id,
                                    DownloadStatus.DOWNLOADING,
                                    progress,
                                    bytes,
                                    total,
                                    null,
                                    null,
                                )
                                notifyProgress(item, progress)
                                lastUpdate = now
                            }
                        }
                    }
                }
            }

            val finished = target ?: throw IOException("Target unduhan tidak tersedia")
            finalizeTarget(finished, item)
            dao.update(
                item.id,
                DownloadStatus.COMPLETED,
                100,
                finished.bytes,
                finished.bytes,
                finished.uri?.toString(),
                null,
            )
            notifyCompleted(item, finished.uri)
        } catch (e: CancellationException) {
            cleanupTarget(target, item)
            throw e
        } catch (e: Exception) {
            cleanupTarget(target, item)
            val currentStatus = dao.get(item.id)?.status
            if (currentStatus != DownloadStatus.CANCELLED) {
                dao.update(
                    item.id,
                    DownloadStatus.FAILED,
                    0,
                    0,
                    -1,
                    null,
                    e.message ?: "Unduhan gagal",
                )
                notifyFailed(item, e.message)
            }
        } finally {
            activeCall = null
            activeId = null
        }
    }

    private fun createTarget(item: DownloadEntity): Target {
        val isVideo = item.kind == DownloadKind.VIDEO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relativePath = if (isVideo) {
                "${Environment.DIRECTORY_MOVIES}/Nonton"
            } else {
                "${Environment.DIRECTORY_MUSIC}/Nonton"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(collection, values)
                ?: throw IOException("Gagal membuat file di MediaStore")
            val output = contentResolver.openOutputStream(uri)
                ?: throw IOException("Gagal membuka file tujuan")
            return Target(output = output, uri = uri, file = null)
        }

        val directory = File(
            Environment.getExternalStoragePublicDirectory(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
            ),
            "Nonton",
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Gagal membuat folder unduhan")
        }
        val file = uniqueFile(directory, item.fileName)
        return Target(FileOutputStream(file), uri = null, file = file)
    }

    private fun finalizeTarget(target: Target, item: DownloadEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && target.uri != null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            contentResolver.update(target.uri, values, null, null)
        } else if (target.file != null) {
            MediaScannerConnection.scanFile(
                this,
                arrayOf(target.file.absolutePath),
                arrayOf(item.mimeType),
                null,
            )
        }
    }

    private fun cleanupTarget(target: Target?, item: DownloadEntity) {
        if (target == null) return
        runCatching { target.output.close() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && target.uri != null) {
            runCatching { contentResolver.delete(target.uri, null, null) }
        }
        target.file?.let { runCatching { it.delete() } }
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var candidate = File(directory, fileName)
        var index = 1
        while (candidate.exists()) {
            val suffix = if (extension.isBlank()) "" else ".$extension"
            candidate = File(directory, "$base ($index)$suffix")
            index++
        }
        return candidate
    }

    private fun startForegroundWith(title: String, progress: Int, indeterminate: Boolean) {
        val notification = buildProgressNotification(title, progress, indeterminate)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }
    }

    private fun buildProgressNotification(
        title: String,
        progress: Int,
        indeterminate: Boolean,
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_download_notification)
        .setContentTitle(title)
        .setContentText(if (indeterminate) "Menyiapkan…" else "$progress%")
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setProgress(100, progress, indeterminate)
        .setContentIntent(mainActivityIntent())
        .build()

    private fun notifyProgress(item: DownloadEntity, progress: Int) {
        notifySafely(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download_notification)
                .setContentTitle(item.title)
                .setContentText("Mengunduh · $progress%")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, progress, false)
                .setContentIntent(mainActivityIntent())
                .build(),
        )
    }

    private fun notifyCompleted(item: DownloadEntity, uri: Uri?) {
        val contentIntent = if (uri != null) {
            PendingIntent.getActivity(
                this,
                item.id.toInt(),
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, item.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            mainActivityIntent()
        }

        notifySafely(
            COMPLETED_ID_BASE + item.id.toInt(),
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download_notification)
                .setContentTitle(item.title)
                .setContentText("Unduhan selesai")
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build(),
        )
    }

    private fun notifyFailed(item: DownloadEntity, message: String?) {
        notifySafely(
            COMPLETED_ID_BASE + item.id.toInt(),
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download_notification)
                .setContentTitle(item.title)
                .setContentText(message ?: "Unduhan gagal")
                .setAutoCancel(true)
                .setContentIntent(mainActivityIntent())
                .build(),
        )
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        runCatching {
            NotificationManagerCompat.from(this).notify(id, notification)
        }
    }

    private fun mainActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progres unduhan video dan audio"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        activeCall?.cancel()
        super.onDestroy()
    }

    private class Target(
        val output: OutputStream,
        val uri: Uri?,
        val file: File?,
    ) {
        var bytes: Long = 0L
    }

    companion object {
        const val ACTION_START = "id.nonton.app.action.START_DOWNLOADS"
        const val ACTION_CANCEL = "id.nonton.app.action.CANCEL_DOWNLOAD"
        const val EXTRA_ID = "extra_download_id"

        private const val CHANNEL_ID = "nonton_downloads"
        private const val NOTIFICATION_ID = 4101
        private const val COMPLETED_ID_BASE = 4200

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, id: Long) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_ID, id)
            runCatching { context.startService(intent) }
        }
    }
}
