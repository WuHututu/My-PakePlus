package com.app.pakeplus

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class AndroidMusicCache(private val context: Context) {
    private val downloadTasks = ConcurrentHashMap<String, Any>()

    private val qualityPriority = mapOf(
        "master" to 100,
        "dolby" to 95,
        "spatial" to 90,
        "surround" to 85,
        "hi-res" to 80,
        "hires" to 80,
        "sq" to 70,
        "hq" to 60,
        "mq" to 50,
        "lq" to 40,
        "standard" to 40
    )

    fun defaultCachePath(): String = cacheRoot().absolutePath

    fun hasCache(id: String, quality: String?, expectedMd5: String?): String? {
        ensureDirs()
        val candidates = if (!quality.isNullOrBlank()) {
            val prefix = cachePrefix(id, quality)
            musicDir().listFiles()
                ?.filter {
                    it.isFile &&
                        it.length() > 0L &&
                        !it.name.endsWith(".tmp") &&
                        (it.name.startsWith("$prefix.") || it.name == "$prefix.sc")
                }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } else {
            pickCandidates(id)
        }

        for (file in candidates) {
            if (!validateCachedMusicFile(file)) {
                deleteBadCache(file, "cache_validation_failed")
                continue
            }

            if (expectedMd5.isNullOrBlank()) {
                file.setLastModified(System.currentTimeMillis())
                return playablePath(file)
            }

            try {
                val actual = md5(file)
                if (actual.equals(expectedMd5, ignoreCase = true)) {
                    file.setLastModified(System.currentTimeMillis())
                    return playablePath(file)
                }
                file.delete()
            } catch (e: Exception) {
                Log.w("AndroidMusicCache", "MD5 check failed: ${file.absolutePath}", e)
                if (!quality.isNullOrBlank()) return null
            }
        }
        return null
    }

    fun cacheMusic(id: String, sourceUrl: String, quality: String?, userAgent: String?): String {
        ensureDirs()
        hasCache(id, quality, null)?.let { return it }

        val target = File(musicDir(), "${cachePrefix(id, quality)}.${inferExtension(sourceUrl)}")
        Log.i(
            "AndroidMusicCache",
            "cacheMusic start id=$id quality=$quality sourceUrl=$sourceUrl target=${target.absolutePath} " +
                    "userAgent=${userAgent?.take(180)}"
        )
        val key = target.absolutePath
        val lock = downloadTasks.getOrPut(key) { Any() }
        synchronized(lock) {
            try {
                hasCache(id, quality, null)?.let { return it }
                cleanTmpFiles()
                checkAndCleanCache()

                val tmp = File(target.parentFile, "${target.name}.tmp")
                tmp.delete()
                if (sourceUrl.startsWith("file://", ignoreCase = true)) {
                    copyFileUrlTo(sourceUrl, tmp)
                } else {
                    downloadToFile(sourceUrl, tmp, userAgent)
                }
                if (!tmp.isFile || tmp.length() <= 0L) {
                    Log.e(
                        "AndroidMusicCache",
                        "empty cache file sourceUrl=$sourceUrl target=${tmp.absolutePath} exists=${tmp.exists()} bytes=${tmp.length()}"
                    )
                    tmp.delete()
                    throw IllegalStateException("Downloaded cache file is empty")
                }
                if (!validateCachedMusicFile(tmp)) {
                    val bytes = tmp.length()
                    tmp.delete()
                    throw IllegalStateException("Downloaded cache file is not a playable audio payload, bytes=$bytes")
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    FileInputStream(tmp).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                    tmp.delete()
                }
                Log.i(
                    "AndroidMusicCache",
                    "cacheMusic finished id=$id quality=$quality file=${target.absolutePath} bytes=${target.length()}"
                )
                return playablePath(target)
            } finally {
                downloadTasks.remove(key)
            }
        }
    }

    fun resolvePlayableUri(uri: Uri): File? {
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        val path = uri.path ?: return null
        val root = musicDir().canonicalFile
        val target = if (path.startsWith("/android-cache/")) {
            val name = uri.lastPathSegment ?: return null
            File(root, name).canonicalFile
        } else {
            File(path).canonicalFile
        }
        if (!target.path.startsWith(root.path) || !target.isFile) return null
        if (!validateCachedMusicFile(target)) {
            deleteBadCache(target, "resolve_playable_invalid")
            return null
        }
        target.setLastModified(System.currentTimeMillis())
        return target
    }

    fun invalidatePlayablePath(pathOrUri: String?, reason: String?): Boolean {
        ensureDirs()
        val target = resolveMusicFileForInvalidation(pathOrUri) ?: return false
        val bytes = target.length()
        val deleted = target.delete()
        Log.w(
            "AndroidMusicCache",
            "cache invalidated reason=${reason.orEmpty()} path=${target.absolutePath} bytes=$bytes deleted=$deleted"
        )
        return deleted
    }

    fun getSize(): Long {
        ensureDirs()
        return dirSize(cacheRoot())
    }

    fun clearAll() {
        cacheRoot().deleteRecursively()
        ensureDirs()
    }

    fun clear(type: String?) {
        when (type) {
            "music" -> musicDir().deleteRecursively()
            "local-data" -> File(cacheRoot(), "local-data").deleteRecursively()
            "lyrics" -> File(cacheRoot(), "lyrics").deleteRecursively()
            "list-data" -> File(cacheRoot(), "list-data").deleteRecursively()
            else -> clearAll()
        }
        ensureDirs()
    }

    fun remove(type: String?, key: String?) {
        if (key.isNullOrBlank()) return
        val dir = cacheDir(type) ?: return
        val target = safeCacheFile(dir, key) ?: return
        if (target.isFile) target.delete()
    }

    fun list(type: String?): JSONArray {
        ensureDirs()
        val dir = cacheDir(type) ?: return JSONArray()
        val result = JSONArray()
        dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.forEach { file ->
            result.put(
                JSONObject()
                    .put("key", file.name)
                    .put("size", file.length())
                    .put("mtime", file.lastModified())
                    .put("atime", file.lastModified())
            )
        }
        return result
    }

    fun get(type: String?, key: String?): ByteArray? {
        if (key.isNullOrBlank()) return null
        ensureDirs()
        val dir = cacheDir(type) ?: return null
        val target = safeCacheFile(dir, key) ?: return null
        if (!target.isFile) return null
        target.setLastModified(System.currentTimeMillis())
        return target.readBytes()
    }

    fun put(type: String?, key: String?, bytes: ByteArray) {
        if (key.isNullOrBlank()) return
        ensureDirs()
        val dir = cacheDir(type) ?: return
        val target = safeCacheFile(dir, key) ?: return
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    private fun cacheRoot(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "SPlayerCache")
    }

    private fun musicDir(): File = File(cacheRoot(), "music")

    private fun ensureDirs() {
        musicDir().mkdirs()
        File(cacheRoot(), "local-data").mkdirs()
        File(cacheRoot(), "lyrics").mkdirs()
        File(cacheRoot(), "list-data").mkdirs()
    }

    private fun cachePrefix(id: String, quality: String?): String {
        return "${safePart(id)}_${normalizeQuality(quality)}"
    }

    private fun playablePath(file: File): String {
        return file.absolutePath
    }

    private fun safePart(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun normalizeQuality(value: String?): String {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: "standard"
        val lower = raw.lowercase(Locale.ROOT)
        val canonical = when (lower) {
            "master", "jymaster", "jm" -> "master"
            "dolby", "db" -> "dolby"
            "spatial", "sky", "sk" -> "spatial"
            "surround", "jyeffect", "je" -> "surround"
            "hi-res", "hires", "hr" -> "hi-res"
            "sq", "lossless" -> "sq"
            "hq", "exhigh", "h" -> "hq"
            "mq", "higher", "m" -> "mq"
            "lq", "standard", "l" -> "standard"
            else -> lower
        }
        return safePart(canonical)
    }

    private fun pickCandidates(id: String): List<File> {
        val prefix = "${safePart(id)}_"
        return musicDir().listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.name.startsWith(prefix) && !it.name.endsWith(".tmp") }
            ?.sortedWith(
                compareByDescending<File> { qualityWeightFromFileName(prefix, it.name) }
                    .thenByDescending { it.lastModified() }
            )
            ?: emptyList()
    }

    private fun qualityWeightFromFileName(prefix: String, name: String): Int {
        val stem = name.substringBeforeLast('.', name).removePrefix(prefix)
        val quality = stem.lowercase(Locale.ROOT)
        return qualityPriority[quality] ?: 0
    }

    private fun cacheDir(type: String?): File? {
        return when (type) {
            "music" -> musicDir()
            "local-data" -> File(cacheRoot(), "local-data")
            "lyrics" -> File(cacheRoot(), "lyrics")
            "list-data" -> File(cacheRoot(), "list-data")
            else -> null
        }
    }

    private fun safeCacheFile(dir: File, key: String): File? {
        val root = dir.canonicalFile
        val target = File(root, key).canonicalFile
        return if (target.path.startsWith(root.path)) target else null
    }

    private fun resolveMusicFileForInvalidation(pathOrUri: String?): File? {
        val raw = pathOrUri?.trim().orEmpty()
        if (raw.isBlank()) return null
        val root = musicDir().canonicalFile
        val target = runCatching {
            val parsed = Uri.parse(raw)
            when {
                parsed.scheme.equals("file", ignoreCase = true) -> {
                    val path = parsed.path ?: return null
                    File(path).canonicalFile
                }
                raw.startsWith("/android-cache/", ignoreCase = true) -> {
                    val name = raw.substringAfterLast('/').substringBefore('?').substringBefore('#')
                    File(root, name).canonicalFile
                }
                else -> File(raw).canonicalFile
            }
        }.getOrNull() ?: return null
        return if (target.path.startsWith(root.path) && target.isFile) target else null
    }

    private fun validateCachedMusicFile(file: File): Boolean {
        val reason = invalidCachedMusicReason(file)
        if (reason != null) {
            Log.w(
                "AndroidMusicCache",
                "invalid cache file reason=$reason path=${file.absolutePath} bytes=${file.length()}"
            )
            return false
        }
        return true
    }

    private fun invalidCachedMusicReason(file: File): String? {
        if (!file.isFile) return "not_file"
        if (file.name.endsWith(".tmp", ignoreCase = true)) return "tmp_file"
        val length = file.length()
        if (length <= 0L) return "empty"
        if (length < MIN_VALID_AUDIO_CACHE_BYTES) return "too_small:$length"
        val sampleSize = minOf(AUDIO_SNIFF_BYTES.toLong(), length).toInt()
        val head = ByteArray(sampleSize)
        val read = try {
            FileInputStream(file).use { it.read(head) }
        } catch (e: Exception) {
            Log.w("AndroidMusicCache", "read cache header failed: ${file.absolutePath}", e)
            return "unreadable_header"
        }
        if (read <= 0) return "empty_header"
        if (looksLikeTextErrorPayload(head, read)) return "text_error_payload"
        return null
    }

    private fun looksLikeTextErrorPayload(buffer: ByteArray, read: Int): Boolean {
        val sample = String(buffer, 0, read, Charsets.ISO_8859_1)
        val lower = sample
            .trimStart('\uFEFF', '\u0000', ' ', '\n', '\r', '\t')
            .take(512)
            .lowercase(Locale.ROOT)
        if (lower.startsWith("<!doctype") || lower.startsWith("<html") || lower.startsWith("<?xml") || lower.startsWith("<body")) {
            return true
        }
        if ((lower.startsWith("{") || lower.startsWith("[")) &&
            listOf("\"code\"", "\"error\"", "\"message\"", "\"status\"", "forbidden", "not found").any { lower.contains(it) }
        ) {
            return true
        }
        val printable = buffer.take(read).count {
            val b = it.toInt() and 0xff
            b == 9 || b == 10 || b == 13 || b in 32..126
        }
        val looksText = read >= 64 && printable.toDouble() / read.toDouble() > 0.92
        if (looksText && listOf(
                "error",
                "forbidden",
                "not found",
                "access denied",
                "bad gateway",
                "service unavailable",
                "unauthorized",
                "too many requests"
            ).any { lower.contains(it) }
        ) {
            return true
        }
        return false
    }

    private fun deleteBadCache(file: File, reason: String) {
        val bytes = file.length()
        val deleted = runCatching { file.delete() }.getOrDefault(false)
        Log.w("AndroidMusicCache", "bad cache removed reason=$reason path=${file.absolutePath} bytes=$bytes deleted=$deleted")
    }

    private fun isDefinitelyInvalidAudioContentType(contentType: String?): Boolean {
        val type = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        if (type.isBlank()) return false
        if (type.startsWith("audio/")) return false
        if (type == "application/octet-stream" || type == "binary/octet-stream" || type == "video/mp4") return false
        return type.startsWith("text/") || type in setOf(
            "application/json",
            "application/xml",
            "application/xhtml+xml",
            "application/javascript",
            "application/problem+json"
        )
    }

    private fun readSmallResponseBody(connection: HttpURLConnection): String? {
        return runCatching {
            val stream = if (connection.responseCode >= 400) connection.errorStream else connection.inputStream
            stream?.bufferedReader()?.use { it.readText().take(512) }
        }.getOrNull()
    }

    private fun inferExtension(sourceUrl: String): String {
        val path = runCatching { Uri.parse(sourceUrl).path.orEmpty().lowercase(Locale.ROOT) }
            .getOrDefault(sourceUrl.lowercase(Locale.ROOT))
        val ext = listOf("flac", "m4a", "aac", "ogg", "opus", "wav", "mp3")
            .firstOrNull { path.endsWith(".$it") || path.contains(".$it/") }
        return ext ?: "mp3"
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadToFile(sourceUrl: String, target: File, userAgent: String?) {
        var currentUrl = sourceUrl
        var redirectCount = 0
        while (redirectCount <= 5) {
            val effectiveUserAgent = userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_UA
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 45000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", effectiveUserAgent)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "close")
            }
            Log.i(
                "AndroidMusicCache",
                "download request sourceUrl=$sourceUrl currentUrl=$currentUrl redirect=$redirectCount " +
                        "target=${target.absolutePath} userAgent=${effectiveUserAgent.take(180)}"
            )
            try {
                val code = connection.responseCode
                Log.i(
                    "AndroidMusicCache",
                    "download response url=$currentUrl code=$code message=${connection.responseMessage} " +
                            "type=${connection.contentType} encoding=${connection.contentEncoding} " +
                            "length=${connection.contentLengthLong} acceptRanges=${connection.getHeaderField("Accept-Ranges")} " +
                            "contentRange=${connection.getHeaderField("Content-Range")} location=${connection.getHeaderField("Location")}"
                )
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect without Location")
                    val resolvedUrl = URL(URL(currentUrl), location).toString()
                    Log.i(
                        "AndroidMusicCache",
                        "download redirect from=$currentUrl to=$location resolved=$resolvedUrl code=$code"
                    )
                    currentUrl = resolvedUrl
                    redirectCount += 1
                    continue
                }
                if (code !in 200..299) {
                    val errorBody = runCatching {
                        connection.errorStream?.bufferedReader()?.use { it.readText().take(512) }
                    }.getOrNull()
                    Log.e(
                        "AndroidMusicCache",
                        "download failed url=$currentUrl code=$code message=${connection.responseMessage} errorBody=$errorBody"
                    )
                    throw IllegalStateException("HTTP $code")
                }
                if (isDefinitelyInvalidAudioContentType(connection.contentType)) {
                    val bodyPreview = readSmallResponseBody(connection)
                    Log.e(
                        "AndroidMusicCache",
                        "download rejected invalid content-type url=$currentUrl type=${connection.contentType} body=$bodyPreview"
                    )
                    throw IllegalStateException("Invalid audio content-type: ${connection.contentType}")
                }
                val copiedBytes = connection.inputStream.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                val expectedLength = connection.contentLengthLong
                if (expectedLength > 0L && target.length() < expectedLength) {
                    Log.e(
                        "AndroidMusicCache",
                        "download truncated url=$currentUrl expected=$expectedLength actual=${target.length()} file=${target.absolutePath}"
                    )
                    target.delete()
                    throw IllegalStateException("Downloaded cache file is truncated")
                }
                if (!validateCachedMusicFile(target)) {
                    target.delete()
                    throw IllegalStateException("Downloaded cache file failed validation")
                }
                Log.i(
                    "AndroidMusicCache",
                    "download finished url=$currentUrl copiedBytes=$copiedBytes fileBytes=${target.length()} file=${target.absolutePath}"
                )
                return
            } catch (e: Exception) {
                Log.e(
                    "AndroidMusicCache",
                    "download exception sourceUrl=$sourceUrl currentUrl=$currentUrl redirect=$redirectCount target=${target.absolutePath}",
                    e
                )
                throw e
            } finally {
                connection.disconnect()
            }
        }
        Log.e("AndroidMusicCache", "download failed sourceUrl=$sourceUrl reason=too_many_redirects lastUrl=$currentUrl")
        throw IllegalStateException("Too many redirects")
    }

    private fun copyFileUrlTo(sourceUrl: String, target: File) {
        val parsed = Uri.parse(sourceUrl)
        val file = resolvePlayableUri(parsed) ?: parsed.path?.let { File(it) }
            ?: throw IllegalArgumentException("Invalid file url")
        FileInputStream(file).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }

    private fun cleanTmpFiles() {
        musicDir().listFiles()?.forEach {
            if (it.isFile && it.name.endsWith(".tmp")) it.delete()
        }
    }

    private fun checkAndCleanCache() {
        val limitBytes = 10L * 1024L * 1024L * 1024L
        var currentSize = getSize()
        if (currentSize <= limitBytes) return
        val files = musicDir().listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?: return
        for (file in files) {
            if (currentSize <= limitBytes) break
            val size = file.length()
            if (file.delete()) currentSize -= size
        }
    }

    companion object {
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Mobile Safari/537.36"
        private const val MIN_VALID_AUDIO_CACHE_BYTES = 32L * 1024L
        private const val AUDIO_SNIFF_BYTES = 4096
    }
}

