package com.app.pakeplus

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Environment
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.Gravity
import android.view.KeyEvent
import android.webkit.PermissionRequest
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.GeolocationPermissions
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.Toast

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
// import android.view.Menu
// import android.view.WindowInsets
// import com.google.android.material.snackbar.Snackbar
// import com.google.android.material.navigation.NavigationView
// import androidx.navigation.findNavController
// import androidx.navigation.ui.AppBarConfiguration
// import androidx.navigation.ui.navigateUp
// import androidx.navigation.ui.setupActionBarWithNavController
// import androidx.navigation.ui.setupWithNavController
// import androidx.drawerlayout.widget.DrawerLayout
// import com.app.pakeplus.databinding.ActivityMainBinding
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.URISyntaxException
import java.net.URLDecoder
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

//    private lateinit var appBarConfiguration: AppBarConfiguration
//    private lateinit var binding: ActivityMainBinding

    private lateinit var webView: WebView
    private lateinit var gestureDetector: GestureDetectorCompat
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private var directoryPickerCallbackId: String? = null
    private lateinit var directoryPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var androidMusicCache: AndroidMusicCache
    private var webViewUserAgent: String = ""
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var pendingPermissionRequest: PermissionRequest? = null

    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private var pendingGeolocationOrigin: String? = null
    private var pendingGeolocationCallback: GeolocationPermissions.Callback? = null

    // 全屏视频相关
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation: Int = 0

    /** 是否从配置启用了全屏（隐藏状态栏+导航栏） */
    private var isFullScreenMode: Boolean = false

    /** 当前主文档是否已出现加载错误；仅成功时隐藏启动遮罩 */
    private var mainFrameLoadError: Boolean = false

    /** app.json 中 launch 非空时才显示启动图遮罩 */
    private var showLaunchSplash: Boolean = false

    /** app.json 中 screenOn 为 true */
    private var keepScreenOnFromConfig: Boolean = false

    /** 仅当 app.json 中 callPhone 为 true 才允许跳转拨号器 */
    private var allowCallPhoneFromConfig: Boolean = false
    private var allowDownloadFromConfig: Boolean = false
    private var allowCameraFromConfig: Boolean = false
    private var allowMicrophoneFromConfig: Boolean = false
    private var allowPositionFromConfig: Boolean = false
    private var allowUploadFromConfig: Boolean = false
    private var allowGalleryFromConfig: Boolean = false
    private var allowExternalBrowserFromConfig: Boolean = false
    private var allowWechatFromConfig: Boolean = false
    private var allowDouyinFromConfig: Boolean = false
    private var allowVideoFullFromConfig: Boolean = true
    private var allowBackgroundPlayFromConfig: Boolean = false
    private var allowInjectJsFromConfig: Boolean = true
    private var mediaSession: MediaSession? = null
    private var mediaTitleFromWeb: String = "SPlayer"
    private var mediaArtistFromWeb: String = ""
    private var mediaAlbumFromWeb: String = ""
    private var mediaArtworkUrlFromWeb: String = ""
    private var mediaArtworkBitmap: Bitmap? = null
    private var mediaDurationMs: Long = 0L
    private var mediaPositionMs: Long = 0L
    private var mediaPlaybackRateFromWeb: Float = 1f
    private var mediaIsPlayingFromWeb: Boolean = false
    private var mediaStateUpdatedAtMs: Long = 0L
    private var lastArtworkFetchUrl: String = ""
    private var lastNotificationIsPlaying: Boolean? = null
    private var lastNotificationAtMs: Long = 0L
    private var lastPolledMediaState: String = ""
    private var playbackServiceRunning: Boolean = false
    private var hasAudioFocus: Boolean = false
    private var audioFocusRequest: Any? = null
    private var mediaPausedByAudioFocus: Boolean = false
    private var mediaSuppressedByAudioFocus: Boolean = false
    private var externalAudioInactiveChecks: Int = 0
    private var audioFocusResumeCheckRunnable: Runnable? = null
    private var safeAreaTopPx: Int = 0
    private var safeAreaBottomPx: Int = 0
    private var safeAreaLeftPx: Int = 0
    private var safeAreaRightPx: Int = 0
    private val mediaPollHandler = Handler(Looper.getMainLooper())
    private var mediaPollRunnable: Runnable? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                runOnUiThread {
                    if (mediaPausedByAudioFocus) {
                        restoreMediaAfterAudioFocusInterruption()
                    }
                }
            }

            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                runOnUiThread {
                    val shouldResumeLater = mediaIsPlayingFromWeb || mediaPausedByAudioFocus
                    val shouldRetreat = shouldResumeLater || mediaSession?.isActive == true || playbackServiceRunning || lastNotificationIsPlaying != null
                    if (mediaIsPlayingFromWeb) {
                        controlWebMedia("pause")
                    }
                    if (shouldRetreat) {
                        suppressMediaSessionForExternalAudio(shouldResumeLater)
                    }
                }
            }
        }
    }

    companion object {
        private const val MEDIA_NOTIFICATION_ID = 1024
        private const val MEDIA_CHANNEL_ID = "splayer_media_playback"
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidMusicCache = AndroidMusicCache(this)

        // 初始化文件选择器
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val resultCode = result.resultCode
            val data = result.data

            if (fileUploadCallback == null) return@registerForActivityResult

            var results: Array<Uri>? = null

            if (resultCode == RESULT_OK && data != null) {
                val dataString = data.dataString
                val clipData = data.clipData

                if (clipData != null) {
                    // 多文件选择
                    results = Array(clipData.itemCount) { i ->
                        clipData.getItemAt(i).uri
                    }
                } else if (dataString != null) {
                    // 单文件选择
                    results = arrayOf(Uri.parse(dataString))
                }
            }

            results?.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                    // Some providers only grant temporary read access.
                }
            }
            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        }

        directoryPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callbackId = directoryPickerCallbackId
            directoryPickerCallbackId = null
            val data = result.data
            val uri = if (result.resultCode == RESULT_OK) data?.data else null
            if (uri != null && data != null) {
                val takeFlags = data.flags and (
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                if (takeFlags != 0) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (_: Exception) {
                        // Some file managers only grant temporary access.
                    }
                }
            }
            notifyDirectoryPickerResult(callbackId, uri?.toString())
        }
        // 初始化运行时权限请求（摄像头 / 麦克风）
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val request = pendingPermissionRequest
            if (request == null) {
                return@registerForActivityResult
            }

            // 所有相关权限都通过才允许 WebView 使用
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                request.grant(request.resources)
            } else {
                request.deny()
            }
            pendingPermissionRequest = null
        }

        // 网页 HTML5 定位（navigator.geolocation）
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val origin = pendingGeolocationOrigin
            val geoCallback = pendingGeolocationCallback
            pendingGeolocationOrigin = null
            pendingGeolocationCallback = null
            if (origin != null && geoCallback != null) {
                val fine = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
                val coarse = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                geoCallback.invoke(origin, fine || coarse, false)
            }
        }

        // parseJsonWithNative
        val config = parseJsonWithNative(this, "app.json")
        val fullScreen = config?.get("fullScreen") as? Boolean ?: false
        val gesture = config?.get("gesture") as? Boolean ?: false
        val debug = config?.get("debug") as? Boolean ?: false
        val userAgent = config?.get("userAgent") as? String ?: ""
        val webUrl = config?.get("webUrl") as? String ?: "https://pakeplus.com/"
        val clearCache = config?.get("clearCache") as? Boolean ?: false
        val setZoom = config?.get("setZoom") as? Boolean ?: false
        allowCallPhoneFromConfig = config?.get("callPhone") as? Boolean ?: false
        allowDownloadFromConfig = config?.get("download") as? Boolean ?: false
        allowCameraFromConfig = config?.get("camera") as? Boolean ?: false
        allowMicrophoneFromConfig = config?.get("microphone") as? Boolean ?: false
        allowPositionFromConfig = config?.get("position") as? Boolean ?: false
        allowUploadFromConfig = config?.get("upload") as? Boolean ?: false
        allowGalleryFromConfig = config?.get("gallery") as? Boolean ?: false
        allowExternalBrowserFromConfig = config?.get("browser") as? Boolean ?: false
        allowWechatFromConfig = config?.get("wechat") as? Boolean ?: false
        allowDouyinFromConfig = config?.get("douyin") as? Boolean ?: false
        allowVideoFullFromConfig = config?.get("videoFull") as? Boolean ?: true
        allowBackgroundPlayFromConfig = config?.get("backgroundPlay") as? Boolean ?: false
        allowInjectJsFromConfig = config?.get("injectJs") as? Boolean ?: true
        mediaTitleFromWeb = config?.get("name") as? String ?: "SPlayer"
        if (allowBackgroundPlayFromConfig) {
            setupMediaSession()
            requestNotificationPermissionIfNeeded()
        }
        val javaScriptEnabledFromConfig = config?.get("javaScriptEnabled") as? Boolean ?: true
        val domStorageEnabledFromConfig = config?.get("domStorageEnabled") as? Boolean ?: true
        val allowFileAccessFromConfig = config?.get("allowFileAccess") as? Boolean ?: false
        val loadWithOverviewModeFromConfig = config?.get("loadWithOverviewMode") as? Boolean ?: true
        val launchCfg = config?.get("launch") as? String
        showLaunchSplash = !launchCfg.isNullOrBlank()
        keepScreenOnFromConfig = config?.get("screenOn") as? Boolean ?: false
        if (keepScreenOnFromConfig) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        // enable debug by chrome://inspect
        WebView.setWebContentsDebuggingEnabled(debug)
        // config fullscreen
        isFullScreenMode = fullScreen
        if (fullScreen) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            )
            window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            }
            // 低于 P 时在这里用旧 API 隐藏导航栏；P 及以上在 setContentView 后由 hideSystemUI() 统一处理
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        )
            }
        }
        // Keep the app inside opaque black system bars in normal mode.
        applyBlackSystemBars()
        setContentView(R.layout.single_main)
        if (!showLaunchSplash) {
            findViewById<View>(R.id.splash_overlay).visibility = View.GONE
        }
        // Normal mode lets Android reserve status/nav-bar space; no Web safe-area compensation is needed.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ConstraintLayout))
        { view, insets ->
            val systemBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            safeAreaLeftPx = systemBar.left
            safeAreaTopPx = systemBar.top
            safeAreaRightPx = systemBar.right
            safeAreaBottomPx = systemBar.bottom
            view.setPadding(0, 0, 0, 0)
            insets
        }
        // 全屏模式下隐藏状态栏和底部导航栏（Android 9+ 必须在这里调用，window 已就绪）
        if (isFullScreenMode) {
            window.decorView.post { hideSystemUI() }
        }
        webView = findViewById<WebView>(R.id.webview)
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        injectAndroidWebBridge(webView)
        webView.settings.apply {
            javaScriptEnabled = javaScriptEnabledFromConfig
            domStorageEnabled = domStorageEnabledFromConfig
            setGeolocationEnabled(allowPositionFromConfig)
            allowFileAccess = true
            useWideViewPort = true
            allowFileAccessFromFileURLs = allowFileAccessFromConfig
            allowContentAccess = true
            allowUniversalAccessFromFileURLs = allowFileAccessFromConfig
            loadWithOverviewMode = loadWithOverviewModeFromConfig
            mediaPlaybackRequiresUserGesture = !allowBackgroundPlayFromConfig
            // setSupportMultipleWindows(true)
        }
        webView
        // set user agent
        val baseUserAgent = if (userAgent.isNotEmpty()) userAgent else webView.settings.userAgentString
        webView.settings.userAgentString = if (baseUserAgent.contains("SPlayerAndroid", ignoreCase = true)) {
            baseUserAgent
        } else {
            "$baseUserAgent SPlayerAndroid"
        }
        webViewUserAgent = webView.settings.userAgentString

        webView.settings.loadWithOverviewMode = loadWithOverviewModeFromConfig
        webView.settings.setSupportZoom(setZoom)

        // clear cache
        if (clearCache) {
            webView.clearCache(true)
        }

        // 为 blob: 链接下载注入 JS 接口
        webView.addJavascriptInterface(JsInterface(this), "JsBridge")

        // inject js
        webView.webViewClient = MyWebViewClient(debug)

        // get web load progress
        webView.webChromeClient = MyChromeClient(this)

        // 网页内下载：点击下载链接时由 DownloadManager 保存到系统下载目录
        // blob:/data: 不能交给 DownloadManager，否则会抛异常甚至闪退（Canvas 导出常见）
        if (allowDownloadFromConfig) {
            webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                if (tryHandleSpecialSchemeDownload(url, userAgent, contentDisposition, mimetype)) {
                    return@setDownloadListener
                }
                startDownload(url, userAgent, contentDisposition, mimetype)
            }
        }

        // Setup gesture detector
        gestureDetector =
            GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false

                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y

                    // Only handle horizontal swipes
                    if (abs(diffX) > abs(diffY)) {
                        if (abs(diffX) > 100 && abs(velocityX) > 100) {
                            if (diffX > 0) {
                                // Swipe right - go back
                                if (webView.canGoBack()) {
                                    webView.goBack()
                                    return true
                                }
                            } else {
                                // Swipe left - go forward
                                if (webView.canGoForward()) {
                                    webView.goForward()
                                    return true
                                }
                            }
                        }
                    }
                    return false
                }
            })

        // Set touch listener for WebView
        webView.setOnTouchListener { _, event ->
            if (gesture) {
                gestureDetector.onTouchEvent(event)
            }
            false
        }

        // load webUrl or file:///android_asset/index.html
        webView.loadUrl(webUrl)

//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(R.layout.single_main)

//        setSupportActionBar(binding.appBarMain.toolbar)

//        binding.appBarMain.fab.setOnClickListener { view ->
//            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                .setAction("Action", null)
//                .setAnchorView(R.id.fab).show()
//        }

//        val drawerLayout: DrawerLayout = binding.drawerLayout
//        val navView: NavigationView = binding.navView
//        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
//        appBarConfiguration = AppBarConfiguration(
//            setOf(
//                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
//            ), drawerLayout
//        )
//        setupActionBarWithNavController(navController, appBarConfiguration)
//        navView.setupWithNavController(navController)
    }


    override fun onPause() {
        super.onPause()
        if (!allowBackgroundPlayFromConfig) {
            webView.onPause()
        }
        // 如果正在全屏播放视频，暂停播放
        if (customView != null) {
            webView.pauseTimers()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!allowBackgroundPlayFromConfig) {
            webView.onResume()
        }
        // 恢复 WebView 的定时器
        webView.resumeTimers()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 全屏模式下窗口重新获得焦点时再次隐藏导航栏（用户从边缘滑出后会自动再隐藏）
        if (hasFocus && isFullScreenMode && customView == null) {
            hideSystemUI()
        }
    }

    override fun onDestroy() {
        // 清理全屏视图
        if (customView != null) {
            hideCustomView()
        }
        stopMediaStatePolling()
        clearExternalAudioSuppression()
        stopPlaybackForegroundService()
        abandonMediaAudioFocus()
        cancelMediaNotification()
        mediaSession?.release()
        mediaSession = null
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 如果正在全屏播放视频，先退出全屏
        if (customView != null) {
            hideCustomView()
            return
        }

        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // 显示全屏视频
    private fun setupMediaSession() {
        if (mediaSession != null) return
        createMediaNotificationChannel()
        mediaSession = MediaSession(this, "SPlayer").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    clearExternalAudioSuppression()
                    controlWebMedia("play")
                    updateMediaPlayback(true, true)
                }

                override fun onPause() {
                    clearExternalAudioSuppression()
                    controlWebMedia("pause")
                    updateMediaPlayback(false, true)
                }

                override fun onSkipToNext() {
                    clearExternalAudioSuppression()
                    controlWebMedia("next")
                    updateMediaPlayback(true, true)
                }

                override fun onSkipToPrevious() {
                    clearExternalAudioSuppression()
                    controlWebMedia("prev")
                    updateMediaPlayback(true, true)
                }

                override fun onStop() {
                    clearExternalAudioSuppression()
                    controlWebMedia("pause")
                    mediaSession?.isActive = false
                    abandonMediaAudioFocus()
                    stopPlaybackForegroundService()
                    cancelMediaNotification()
                    lastNotificationIsPlaying = null
                }

                override fun onSeekTo(pos: Long) {
                    val safePosition = pos.coerceAtLeast(0L)
                    mediaPositionMs = if (mediaDurationMs > 0L) {
                        safePosition.coerceAtMost(mediaDurationMs)
                    } else {
                        safePosition
                    }
                    mediaStateUpdatedAtMs = SystemClock.elapsedRealtime()
                    seekWebMedia(mediaPositionMs)
                    updateMediaPlayback(mediaIsPlayingFromWeb, true)
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (event?.action != KeyEvent.ACTION_UP) return true
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> onPlay()
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> onPause()
                        KeyEvent.KEYCODE_MEDIA_NEXT -> onSkipToNext()
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> onSkipToPrevious()
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            clearExternalAudioSuppression()
                            controlWebMedia("toggle")
                            updateMediaPlayback(!mediaIsPlayingFromWeb, true)
                        }
                        else -> return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                    return true
                }
            })
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setPlaybackToLocal(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            isActive = false
        }
        updateMediaPlayback(false, true)
    }
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun controlWebMedia(action: String) {
        if (!::webView.isInitialized) return
        val script = """
            (function(){
              var action = "$action";
              function unique(list) {
                return list.filter(function (item, index, arr) { return item && arr.indexOf(item) === index; });
              }
              function allMedia() {
                var tracked = [];
                try {
                  var registry = window.__SPLAYER_ANDROID_MEDIA_ELEMENTS__ || [];
                  if (typeof registry.forEach === 'function') registry.forEach(function (el) { tracked.push(el); });
                  else if (Array.isArray(registry)) tracked = registry.slice();
                } catch (e) {}
                return unique(tracked.concat(Array.prototype.slice.call(document.querySelectorAll('audio,video'))));
              }
              function hasSource(el) {
                try { return !!(el && (el.currentSrc || el.src || el.getAttribute && el.getAttribute('src'))); } catch (e) { return false; }
              }
              function pauseElement(el) {
                try { if (el && el.pause) el.pause(); } catch (e) {}
              }
              function pauseAll() {
                allMedia().forEach(pauseElement);
              }
              function firstUsableMedia() {
                var list = allMedia().filter(function (el) { return el && (hasSource(el) || !isNaN(el.duration)); });
                return list.filter(function (el) { return el && !el.paused && !el.ended; })[0] || list[0] || null;
              }
              var player = window.__SPLAYER_PLAYER_CONTROLLER__ || null;
              var hadActiveMedia = allMedia().some(function (el) { return el && !el.paused && !el.ended; });
              try {
                if ((action === "next" || action === "prev") && player && typeof player.nextOrPrev === "function") {
                  pauseAll();
                  player.nextOrPrev(action === "next" ? "next" : "prev", true);
                  return;
                }
                if (player) {
                  if (action === "play" && typeof player.play === "function") { player.play(); return; }
                  if (action === "pause" && typeof player.pause === "function") { player.pause(); pauseAll(); return; }
                  if (action === "toggle" && typeof player.playOrPause === "function") {
                    player.playOrPause();
                    if (hadActiveMedia) pauseAll();
                    return;
                  }
                }
              } catch (e) {}
              var media = firstUsableMedia();
              if (!media) return;
              if (action === "play") media.play && media.play();
              if (action === "pause") pauseAll();
              if (action === "toggle") {
                if (media.paused) media.play && media.play();
                else pauseAll();
              }
            })();
        """.trimIndent()
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun clearExternalAudioSuppression() {
        mediaPausedByAudioFocus = false
        mediaSuppressedByAudioFocus = false
        externalAudioInactiveChecks = 0
        audioFocusResumeCheckRunnable?.let { mediaPollHandler.removeCallbacks(it) }
        audioFocusResumeCheckRunnable = null
    }

    private fun suppressMediaSessionForExternalAudio(resumeWhenFocusReturns: Boolean) {
        val pausedPosition = currentMediaPositionMs()
        mediaPausedByAudioFocus = resumeWhenFocusReturns
        mediaSuppressedByAudioFocus = true
        mediaIsPlayingFromWeb = false
        mediaPositionMs = pausedPosition
        mediaStateUpdatedAtMs = SystemClock.elapsedRealtime()

        val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SEEK_TO
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(PlaybackState.STATE_PAUSED, pausedPosition, 0f)
                .build()
        )
        mediaSession?.isActive = false
        stopPlaybackForegroundService()
        cancelMediaNotification()
        lastNotificationIsPlaying = null
        lastNotificationAtMs = SystemClock.elapsedRealtime()

        if (resumeWhenFocusReturns) {
            scheduleExternalAudioResumeCheck()
        } else {
            audioFocusResumeCheckRunnable?.let { mediaPollHandler.removeCallbacks(it) }
            audioFocusResumeCheckRunnable = null
            externalAudioInactiveChecks = 0
        }
    }

    private fun scheduleExternalAudioResumeCheck(delayMs: Long = 1500L) {
        if (!mediaPausedByAudioFocus || audioFocusResumeCheckRunnable != null) return
        val runnable = object : Runnable {
            override fun run() {
                audioFocusResumeCheckRunnable = null
                if (!mediaPausedByAudioFocus || !mediaSuppressedByAudioFocus) return
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (audioManager.isMusicActive) {
                    externalAudioInactiveChecks = 0
                    scheduleExternalAudioResumeCheck()
                    return
                }
                externalAudioInactiveChecks++
                if (externalAudioInactiveChecks >= 2) {
                    restoreMediaAfterAudioFocusInterruption()
                } else {
                    scheduleExternalAudioResumeCheck()
                }
            }
        }
        audioFocusResumeCheckRunnable = runnable
        mediaPollHandler.postDelayed(runnable, delayMs)
    }

    private fun restoreMediaAfterAudioFocusInterruption() {
        if (!mediaPausedByAudioFocus) return
        clearExternalAudioSuppression()
        controlWebMedia("play")
        updateMediaPlayback(true, true)
    }

    private fun seekWebMedia(positionMs: Long) {
        if (!::webView.isInitialized) return
        val seconds = positionMs / 1000.0
        val script = """
            (function(){
              var targetSeconds = $seconds;
              var targetMs = Math.round(targetSeconds * 1000);
              var player = window.__SPLAYER_PLAYER_CONTROLLER__ || null;
              if (player && typeof player.setSeek === "function") { player.setSeek(targetMs); return; }
              var media = Array.prototype.slice.call(document.querySelectorAll('audio,video'))
                .filter(function(el){ return el && !isNaN(el.duration); })[0];
              if (media) media.currentTime = targetSeconds;
            })();
        """.trimIndent()
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun updateMediaPlayback(isPlaying: Boolean, forceNotify: Boolean = false) {
        val session = mediaSession ?: return
        if (mediaSuppressedByAudioFocus) {
            if (isPlaying) controlWebMedia("pause")
            return
        }
        mediaIsPlayingFromWeb = isPlaying
        val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SEEK_TO
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val position = currentMediaPositionMs()
        val playbackSpeed = if (isPlaying) mediaPlaybackRateFromWeb else 0f
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, position, playbackSpeed)
                .build()
        )
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, mediaTitleFromWeb)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, mediaArtistFromWeb)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, mediaAlbumFromWeb)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, mediaTitleFromWeb)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, mediaArtistFromWeb)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, mediaAlbumFromWeb)
        if (mediaDurationMs > 0L) {
            metadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, mediaDurationMs)
        }
        if (mediaArtworkUrlFromWeb.isNotBlank()) {
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_ART_URI, mediaArtworkUrlFromWeb)
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, mediaArtworkUrlFromWeb)
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, mediaArtworkUrlFromWeb)
        }
        mediaArtworkBitmap?.let {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, it)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, it)
        }
        session.setMetadata(metadataBuilder.build())

        val now = SystemClock.elapsedRealtime()
        if (isPlaying) {
            if (!requestMediaAudioFocus()) {
                controlWebMedia("pause")
                mediaIsPlayingFromWeb = false
                session.isActive = false
                stopPlaybackForegroundService()
                cancelMediaNotification()
                lastNotificationIsPlaying = null
                return
            }
            session.isActive = true
            if (forceNotify || lastNotificationIsPlaying != true || now - lastNotificationAtMs > 5000L) {
                showMediaNotification(true)
                startPlaybackForegroundService()
            } else if (!playbackServiceRunning) {
                startPlaybackForegroundService()
            }
        } else {
            session.isActive = true
            if (forceNotify || lastNotificationIsPlaying != false || now - lastNotificationAtMs > 5000L) {
                showMediaNotification(false)
                startPlaybackForegroundService()
            } else if (!playbackServiceRunning) {
                startPlaybackForegroundService()
            }
        }
    }

    private fun currentMediaPositionMs(): Long {
        val base = mediaPositionMs.coerceAtLeast(0L)
        val advanced = if (mediaIsPlayingFromWeb && mediaStateUpdatedAtMs > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - mediaStateUpdatedAtMs
            base + (elapsed * mediaPlaybackRateFromWeb).toLong()
        } else {
            base
        }
        return if (mediaDurationMs > 0L) {
            advanced.coerceIn(0L, mediaDurationMs)
        } else {
            advanced.coerceAtLeast(0L)
        }
    }

    private fun secondsToMs(value: Double): Long {
        if (value.isNaN() || value.isInfinite() || value <= 0.0) return 0L
        return (value * 1000.0).toLong().coerceAtLeast(0L)
    }

    private fun decodeJsResult(result: String?): String? {
        val trimmed = result?.trim().orEmpty()
        if (trimmed.isBlank() || trimmed == "null" || trimmed == "undefined") return null
        if (!trimmed.startsWith("\"")) return trimmed
        return try {
            JSONObject("{\"value\":$trimmed}").optString("value", null)
        } catch (e: Exception) {
            null
        }
    }

    private fun updateArtworkFromUrl(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl == lastArtworkFetchUrl) return
        if (cleanUrl.startsWith("blob:", ignoreCase = true) ||
            cleanUrl.startsWith("data:", ignoreCase = true)
        ) {
            return
        }
        lastArtworkFetchUrl = cleanUrl
        val userAgent = if (::webView.isInitialized) webView.settings.userAgentString else null
        thread(name = "splayer-artwork-loader") {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sampleSize = 1
                while ((bounds.outWidth / sampleSize) > 512 || (bounds.outHeight / sampleSize) > 512) {
                    sampleSize *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (bitmap != null) {
                    runOnUiThread {
                        if (mediaArtworkUrlFromWeb == cleanUrl) {
                            mediaArtworkBitmap = bitmap
                            updateMediaPlayback(mediaIsPlayingFromWeb, true)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MediaSession", "Unable to load artwork: $cleanUrl", e)
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun updateMediaStateFromWeb(
        isPlaying: Boolean,
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        durationSeconds: Double,
        positionSeconds: Double,
        playbackRate: Double
    ) {
        val cleanTitle = title?.trim().orEmpty().ifBlank { "SPlayer" }
        val cleanArtist = artist?.trim().orEmpty()
        val cleanAlbum = album?.trim().orEmpty()
        val cleanArtworkUrl = artworkUrl?.trim().orEmpty()
        val newDurationMs = secondsToMs(durationSeconds)
        val newPositionMs = secondsToMs(positionSeconds)
        val newPlaybackRate = if (playbackRate.isNaN() || playbackRate.isInfinite() || playbackRate <= 0.0) {
            1f
        } else {
            playbackRate.toFloat()
        }

        var metadataChanged = false
        if (mediaTitleFromWeb != cleanTitle) {
            mediaTitleFromWeb = cleanTitle
            metadataChanged = true
        }
        if (mediaArtistFromWeb != cleanArtist) {
            mediaArtistFromWeb = cleanArtist
            metadataChanged = true
        }
        if (mediaAlbumFromWeb != cleanAlbum) {
            mediaAlbumFromWeb = cleanAlbum
            metadataChanged = true
        }
        if (mediaArtworkUrlFromWeb != cleanArtworkUrl) {
            mediaArtworkUrlFromWeb = cleanArtworkUrl
            mediaArtworkBitmap = null
            metadataChanged = true
            if (cleanArtworkUrl.isNotBlank()) updateArtworkFromUrl(cleanArtworkUrl)
        }

        mediaDurationMs = newDurationMs
        mediaPositionMs = if (newDurationMs > 0L) newPositionMs.coerceAtMost(newDurationMs) else newPositionMs
        mediaPlaybackRateFromWeb = newPlaybackRate
        mediaStateUpdatedAtMs = SystemClock.elapsedRealtime()
        if (mediaSuppressedByAudioFocus) {
            mediaIsPlayingFromWeb = false
            if (isPlaying) controlWebMedia("pause")
            return
        }
        updateMediaPlayback(isPlaying, metadataChanged || lastNotificationIsPlaying != isPlaying)
    }

    private fun startMediaStatePolling(view: WebView?) {
        if (view == null || mediaPollRunnable != null) return
        val poller = object : Runnable {
            override fun run() {
                if (!allowBackgroundPlayFromConfig || !::webView.isInitialized) return
                val script = """
                    (function () {
                      try {
                        function finite(value) { var n = Number(value); return isFinite(n) ? n : 0; }
                        function positive(value) { var n = finite(value); return n > 0 ? n : 0; }
                        function msToSeconds(value) { var n = positive(value); return n > 0 ? n / 1000 : 0; }
                        function cleanText(value) { return String(value || '').replace(/\s+/g, ' ').trim(); }
                        function absoluteUrl(url) {
                          if (!url) return '';
                          try { return new URL(String(url), location.href).href; } catch (e) { return String(url || ''); }
                        }
                        function parseStore(key) {
                          try {
                            var raw = localStorage.getItem(key);
                            if (!raw) return {};
                            var data = JSON.parse(raw);
                            return data && typeof data === 'object' ? data : {};
                          } catch (e) { return {}; }
                        }
                        function firstText(selectors) {
                          for (var i = 0; i < selectors.length; i++) {
                            var el = document.querySelector(selectors[i]);
                            var text = cleanText(el && (el.innerText || el.textContent));
                            if (text) return text;
                          }
                          return '';
                        }
                        function firstImage(selectors) {
                          for (var i = 0; i < selectors.length; i++) {
                            var el = document.querySelector(selectors[i]);
                            var src = el && (el.currentSrc || el.src || el.getAttribute('src'));
                            if (src) return absoluteUrl(src);
                          }
                          return '';
                        }
                        function chooseArtwork(artwork) {
                          if (!artwork || !artwork.length) return '';
                          var best = null;
                          Array.prototype.slice.call(artwork).forEach(function (item) {
                            if (!item || !item.src) return;
                            var score = 0;
                            var sizes = String(item.sizes || '');
                            var match = sizes.match(/(\d+)x(\d+)/);
                            if (match) score = Number(match[1]) * Number(match[2]);
                            if (!best || score >= best.score) best = { score: score, src: item.src };
                          });
                          return best ? absoluteUrl(best.src) : '';
                        }
                        function artistOf(song) {
                          if (!song) return '';
                          if (song.type === 'radio' && song.dj && song.dj.creator) return cleanText(song.dj.creator);
                          var artists = song.artists;
                          if (Array.isArray(artists)) {
                            return artists.map(function (item) {
                              return cleanText(item && typeof item === 'object' ? (item.name || item.nickname) : item);
                            }).filter(Boolean).join('/');
                          }
                          return cleanText(artists);
                        }
                        function albumOf(song) {
                          if (!song) return '';
                          if (song.type === 'radio' && song.dj && song.dj.name) return cleanText(song.dj.name);
                          var album = song.album;
                          return cleanText(album && typeof album === 'object' ? album.name : album);
                        }
                        function coverOf(song) {
                          if (!song) return '';
                          var sizes = song.coverSize || {};
                          return absoluteUrl(sizes.xl || sizes.l || sizes.cover || sizes.m || sizes.s || song.cover || '');
                        }
                        function methodNumber(target, name) {
                          try { return target && typeof target[name] === 'function' ? positive(target[name]()) : 0; } catch (e) { return 0; }
                        }
                        function propertyNumber(target, name) {
                          try { return target ? positive(target[name]) : 0; } catch (e) { return 0; }
                        }
                        function propertyBoolean(target, name, fallback) {
                          try { return target && name in target ? !!target[name] : fallback; } catch (e) { return fallback; }
                        }
                        var session = navigator.mediaSession || null;
                        var metadata = session && session.metadata ? session.metadata : null;
                        var manager = window.__SPLAYER_AUDIO_MANAGER__ || null;
                        var controller = window.__SPLAYER_PLAYER_CONTROLLER__ || null;
                        var statusStore = parseStore('status-store');
                        var musicStore = parseStore('music-store');
                        var song = musicStore.playSong || {};
                        var hasSong = !!(song && (song.id || (cleanText(song.name) && cleanText(song.name) !== '未播放歌曲')));
                        var trackedMedia = [];
                        try {
                          var registry = window.__SPLAYER_ANDROID_MEDIA_ELEMENTS__ || [];
                          if (typeof registry.forEach === 'function') registry.forEach(function (el) { trackedMedia.push(el); });
                          else if (Array.isArray(registry)) trackedMedia = registry.slice();
                        } catch (e) {}
                        var mediaList = trackedMedia.concat(Array.prototype.slice.call(document.querySelectorAll('audio,video')))
                          .filter(function (el, index, arr) { return el && arr.indexOf(el) === index; });
                        var media = mediaList.filter(function (el) { return el && !el.paused && !el.ended; })[0] ||
                          mediaList.filter(function (el) { return el && positive(el.duration) > 0; })[0] || null;
                        var state = session && session.playbackState ? String(session.playbackState) : '';
                        var mediaDuration = media ? positive(media.duration) : 0;
                        var mediaPosition = media ? positive(media.currentTime) : 0;
                        var managerDuration = propertyNumber(manager, 'duration');
                        var managerPosition = propertyNumber(manager, 'currentTime');
                        var controllerDuration = methodNumber(controller, 'getDuration') / 1000;
                        var controllerPosition = methodNumber(controller, 'getSeek') / 1000;
                        var storeDuration = msToSeconds(statusStore.duration);
                        var storePosition = msToSeconds(statusStore.currentTime);
                        var songDuration = msToSeconds(song.duration);
                        var duration = mediaDuration || managerDuration || controllerDuration || storeDuration || songDuration || 0;
                        var position = mediaPosition || managerPosition || controllerPosition || storePosition || 0;
                        if (duration > 0 && position > duration) position = duration;
                        var paused = propertyBoolean(manager, 'paused', true);
                        var isPlaying = media ? (!media.paused && !media.ended) : manager ? !paused : state === 'playing';
                        var title = cleanText(metadata && metadata.title) || (hasSong ? cleanText(song.name) : '') ||
                          firstText(['.player-data .name .name-text', '.player-data .name', '.name .name-text']) ||
                          cleanText(document.title) || 'SPlayer';
                        var artist = cleanText(metadata && metadata.artist) || artistOf(song) ||
                          firstText(['.player-data .artists .ar-list', '.player-data .artists', '.artists .ar-list']);
                        var album = cleanText(metadata && metadata.album) || albumOf(song) ||
                          firstText(['.player-data .album .name-text', '.player-data .dj .name-text', '.player-data .album']);
                        var artwork = chooseArtwork(metadata && metadata.artwork) || coverOf(song) ||
                          firstImage(['.player-cover img', '.cover img', 'img[src*="music.126.net"]', 'img[src*="/images/song"]']);
                        var rate = media && positive(media.playbackRate) ? positive(media.playbackRate) : positive(statusStore.playRate) || 1;
                        return {
                          isPlaying: isPlaying,
                          title: title,
                          artist: artist,
                          album: album,
                          artwork: artwork,
                          duration: duration,
                          position: position,
                          playbackRate: rate
                        };
                      } catch (e) {
                        return { isPlaying: false, title: document.title || 'SPlayer', artist: '', album: '', artwork: '', duration: 0, position: 0, playbackRate: 1 };
                      }
                    })();
                """.trimIndent()
                webView.evaluateJavascript(script) { result ->
                    val jsonText = decodeJsResult(result)
                    if (!jsonText.isNullOrBlank() && jsonText != lastPolledMediaState) {
                        lastPolledMediaState = jsonText
                        try {
                            val payload = JSONObject(jsonText)
                            updateMediaStateFromWeb(
                                isPlaying = payload.optBoolean("isPlaying", false),
                                title = payload.optString("title", ""),
                                artist = payload.optString("artist", ""),
                                album = payload.optString("album", ""),
                                artworkUrl = payload.optString("artwork", ""),
                                durationSeconds = payload.optDouble("duration", 0.0),
                                positionSeconds = payload.optDouble("position", 0.0),
                                playbackRate = payload.optDouble("playbackRate", 1.0)
                            )
                        } catch (e: Exception) {
                            Log.w("MediaSession", "Invalid polled media state", e)
                        }
                    }
                    mediaPollHandler.postDelayed(this, 1000L)
                }
            }
        }
        mediaPollRunnable = poller
        mediaPollHandler.postDelayed(poller, 2500L)
    }

    private fun stopMediaStatePolling() {
        mediaPollRunnable?.let { mediaPollHandler.removeCallbacks(it) }
        mediaPollRunnable = null
    }
    private fun createMediaNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            MEDIA_CHANNEL_ID,
            "SPlayer playback",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun showMediaNotification(isPlaying: Boolean) {
        val session = mediaSession ?: return
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun mediaButtonIntent(requestCode: Int, keyCode: Int): PendingIntent {
            return PendingIntent.getBroadcast(
                this,
                requestCode,
                Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(
                    Intent.EXTRA_KEY_EVENT,
                    KeyEvent(KeyEvent.ACTION_UP, keyCode)
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val previousIntent = mediaButtonIntent(2, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        val toggleIntent = mediaButtonIntent(
            1,
            if (isPlaying) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
        )
        val nextIntent = mediaButtonIntent(3, KeyEvent.KEYCODE_MEDIA_NEXT)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, MEDIA_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notificationBuilder = builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(mediaTitleFromWeb)
            .setContentText(mediaArtistFromWeb.ifBlank {
                mediaAlbumFromWeb.ifBlank { if (isPlaying) "Playing" else "Paused" }
            })
            .setContentIntent(openIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                previousIntent
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                toggleIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                nextIntent
            )
        mediaArtworkBitmap?.let { notificationBuilder.setLargeIcon(it) }
        val notification = notificationBuilder.build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(MEDIA_NOTIFICATION_ID, notification)
        lastNotificationIsPlaying = isPlaying
        lastNotificationAtMs = SystemClock.elapsedRealtime()
    }
    private fun cancelMediaNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(MEDIA_NOTIFICATION_ID)
    }

    private fun requestMediaAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = (audioFocusRequest as? AudioFocusRequest) ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setWillPauseWhenDucked(true)
                .build()
                .also { audioFocusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonMediaAudioFocus() {
        if (!hasAudioFocus) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (audioFocusRequest as? AudioFocusRequest)?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    private fun startPlaybackForegroundService() {
        val session = mediaSession ?: return
        val intent = Intent(this, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_START
            putExtra(MediaPlaybackService.EXTRA_TITLE, mediaTitleFromWeb)
            putExtra(MediaPlaybackService.EXTRA_ARTIST, mediaArtistFromWeb)
            putExtra(MediaPlaybackService.EXTRA_SESSION_TOKEN, session.sessionToken)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
            playbackServiceRunning = true
        } catch (e: Exception) {
            Log.w("MediaSession", "Unable to start foreground playback service", e)
        }
    }

    private fun stopPlaybackForegroundService() {
        try {
            stopService(Intent(this, MediaPlaybackService::class.java))
        } catch (_: Exception) {
            // The service may already be gone.
        }
        playbackServiceRunning = false
    }

    private fun applyBlackSystemBars() {
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (!isFullScreenMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }

    private fun openAndroidDirectoryPicker(callbackId: String?) {
        runOnUiThread {
            directoryPickerCallbackId?.let { notifyDirectoryPickerResult(it, null) }
            directoryPickerCallbackId = callbackId
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            }
            try {
                directoryPickerLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("DirectoryPicker", "No directory picker available", e)
                notifyDirectoryPickerResult(callbackId, null)
            } catch (e: Exception) {
                Log.e("DirectoryPicker", "Unable to open directory picker", e)
                notifyDirectoryPickerResult(callbackId, null)
            }
        }
    }

    private fun notifyDirectoryPickerResult(callbackId: String?, uri: String?) {
        if (!::webView.isInitialized || callbackId.isNullOrBlank()) return
        val idJson = JSONObject.quote(callbackId)
        val uriJson = uri?.let { JSONObject.quote(it) } ?: "null"
        val script = "window.__PakePlusAndroidDirectoryPicked && window.__PakePlusAndroidDirectoryPicked($idJson, $uriJson);"
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun androidBridgeScript(): String {
        val defaultCachePathJson = JSONObject.quote(androidMusicCache.defaultCachePath())
        return """
            (function () {
              try {
                var oldSafeStyle = document.getElementById('__pakeplus_android_safe_area_style__');
                if (oldSafeStyle && oldSafeStyle.parentNode) oldSafeStyle.parentNode.removeChild(oldSafeStyle);
                document.documentElement.style.removeProperty('--android-safe-area-top');
                document.documentElement.style.removeProperty('--android-safe-area-bottom');
                document.documentElement.style.removeProperty('--android-safe-area-left');
                document.documentElement.style.removeProperty('--android-safe-area-right');

                var androidDefaultCachePath = $defaultCachePathJson;
                var storeKey = '__pakeplus_android_store__';
                function readStore() {
                  try { return JSON.parse(localStorage.getItem(storeKey) || '{}') || {}; }
                  catch (e) { return {}; }
                }
                function writeStore(data) {
                  try { localStorage.setItem(storeKey, JSON.stringify(data || {})); } catch (e) {}
                }
                function ensureStoreDefaults() {
                  var data = readStore();
                  if (!Object.prototype.hasOwnProperty.call(data, 'cachePath') || !data.cachePath) {
                    data.cachePath = androidDefaultCachePath;
                    writeStore(data);
                  }
                  return data;
                }
                ensureStoreDefaults();

                function androidBridgeLog(tag, message) {
                  try {
                    if (window.JsBridge && typeof window.JsBridge.androidLog === 'function') {
                      window.JsBridge.androidLog(String(tag || 'AndroidJs'), String(message || ''));
                    }
                  } catch (e) {}
                }

                (function installAndroidAudioProxy() {
                  if (window.__splayerAndroidAudioProxyPatched) return;
                  window.__splayerAndroidAudioProxyPatched = true;
                  var proxyPrefix = 'https://splayer.android.local/__audio_proxy__?u=';
                  var tracked = window.__SPLAYER_ANDROID_MEDIA_ELEMENTS__;
                  if (!tracked || typeof tracked.push !== 'function') tracked = [];
                  window.__SPLAYER_ANDROID_MEDIA_ELEMENTS__ = tracked;

                  function rememberMedia(el) {
                    try {
                      if (!el || tracked.indexOf(el) >= 0) return el;
                      tracked.push(el);
                      if (tracked.length > 20) tracked.splice(0, tracked.length - 20);
                    } catch (e) {}
                    return el;
                  }
                  function isAudioElement(el) {
                    try {
                      return !!(el && ((window.HTMLAudioElement && el instanceof HTMLAudioElement) || String(el.tagName || '').toLowerCase() === 'audio'));
                    } catch (e) { return false; }
                  }
                  function normalizeAudioUrl(value) {
                    if (value == null) return value;
                    var raw = String(value);
                    if (!raw || /^(blob|data|file|content|about|javascript):/i.test(raw)) return raw;
                    var abs = '';
                    try { abs = new URL(raw, location.href).href; } catch (e) { return raw; }
                    if (!/^https?:/i.test(abs)) return raw;
                    try {
                      var parsed = new URL(abs);
                      if (parsed.hostname === 'splayer.android.local' && parsed.pathname === '/__audio_proxy__') return abs;
                    } catch (e2) {}
                    var proxied = proxyPrefix + encodeURIComponent(abs);
                    androidBridgeLog('SPlayerAudioProxy', 'rewrite ' + abs.slice(0, 220));
                    return proxied;
                  }

                  var mediaProto = window.HTMLMediaElement && HTMLMediaElement.prototype;
                  if (mediaProto) {
                    var srcDesc = Object.getOwnPropertyDescriptor(mediaProto, 'src');
                    if (srcDesc && srcDesc.get && srcDesc.set && !srcDesc.__splayerAndroidWrapped) {
                      Object.defineProperty(mediaProto, 'src', {
                        configurable: true,
                        enumerable: srcDesc.enumerable,
                        get: function () { return srcDesc.get.call(this); },
                        set: function (value) {
                          rememberMedia(this);
                          return srcDesc.set.call(this, isAudioElement(this) ? normalizeAudioUrl(value) : value);
                        }
                      });
                    }
                    ['load', 'play', 'pause'].forEach(function (name) {
                      var original = mediaProto[name];
                      if (typeof original === 'function' && !original.__splayerAndroidWrapped) {
                        var wrapped = function () {
                          rememberMedia(this);
                          return original.apply(this, arguments);
                        };
                        wrapped.__splayerAndroidWrapped = true;
                        mediaProto[name] = wrapped;
                      }
                    });
                  }

                  var oldSetAttribute = window.Element && Element.prototype && Element.prototype.setAttribute;
                  if (typeof oldSetAttribute === 'function' && !oldSetAttribute.__splayerAndroidWrapped) {
                    var wrappedSetAttribute = function (name, value) {
                      if (isAudioElement(this) && String(name || '').toLowerCase() === 'src') {
                        rememberMedia(this);
                        value = normalizeAudioUrl(value);
                      }
                      return oldSetAttribute.call(this, name, value);
                    };
                    wrappedSetAttribute.__splayerAndroidWrapped = true;
                    Element.prototype.setAttribute = wrappedSetAttribute;
                  }

                  var NativeAudio = window.Audio;
                  if (typeof NativeAudio === 'function' && !NativeAudio.__splayerAndroidWrapped) {
                    var WrappedAudio = function (src) {
                      var el = arguments.length > 0 ? new NativeAudio(normalizeAudioUrl(src)) : new NativeAudio();
                      rememberMedia(el);
                      return el;
                    };
                    WrappedAudio.prototype = NativeAudio.prototype;
                    try { Object.setPrototypeOf(WrappedAudio, NativeAudio); } catch (e) {}
                    WrappedAudio.__splayerAndroidWrapped = true;
                    window.Audio = WrappedAudio;
                  }

                  document.addEventListener('play', function (event) { if (isAudioElement(event.target)) rememberMedia(event.target); }, true);
                  document.addEventListener('loadedmetadata', function (event) { if (isAudioElement(event.target)) rememberMedia(event.target); }, true);
                  androidBridgeLog('SPlayerAudioProxy', 'audio proxy script installed');
                })();

                window.api = window.api || {};
                window.api.store = window.api.store || {
                  get: function (key) {
                    var data = ensureStoreDefaults();
                    return Promise.resolve(Object.prototype.hasOwnProperty.call(data, key) ? data[key] : null);
                  },
                  set: function (key, value) {
                    var data = readStore();
                    data[key] = value;
                    writeStore(data);
                    return Promise.resolve(true);
                  },
                  has: function (key) {
                    var data = ensureStoreDefaults();
                    return Promise.resolve(Object.prototype.hasOwnProperty.call(data, key));
                  },
                  delete: function (key) {
                    var data = readStore();
                    delete data[key];
                    writeStore(data);
                    return Promise.resolve(true);
                  },
                  reset: function () {
                    writeStore({ cachePath: androidDefaultCachePath });
                    return Promise.resolve(true);
                  }
                };

                window.__PakePlusAndroidDirectoryCallbacks = window.__PakePlusAndroidDirectoryCallbacks || {};
                window.__PakePlusAndroidDirectoryPicked = function (id, uri) {
                  var callback = window.__PakePlusAndroidDirectoryCallbacks[id];
                  if (!callback) return;
                  delete window.__PakePlusAndroidDirectoryCallbacks[id];
                  callback.resolve(uri || null);
                };

                window.__PakePlusAndroidIpcCallbacks = window.__PakePlusAndroidIpcCallbacks || {};
                window.__PakePlusAndroidIpcResolved = function (id, payload) {
                  var callback = window.__PakePlusAndroidIpcCallbacks[id];
                  if (!callback) return;
                  delete window.__PakePlusAndroidIpcCallbacks[id];
                  callback.resolve(payload === undefined ? null : payload);
                };

                function androidInvoke(channel, args) {
                  return new Promise(function (resolve) {
                    var id = 'android_ipc_' + Date.now() + '_' + Math.random().toString(36).slice(2);
                    window.__PakePlusAndroidIpcCallbacks[id] = { resolve: resolve };
                    try {
                      if (window.JsBridge && typeof window.JsBridge.androidIpcInvoke === 'function') {
                        window.JsBridge.androidIpcInvoke(id, channel, JSON.stringify(args || []));
                      } else {
                        delete window.__PakePlusAndroidIpcCallbacks[id];
                        resolve(null);
                      }
                    } catch (e) {
                      delete window.__PakePlusAndroidIpcCallbacks[id];
                      resolve(null);
                    }
                  });
                }

                function installAndroidCacheErrorInvalidation() {
                  if (window.__splayerAndroidCacheErrorInvalidationPatched) return;
                  window.__splayerAndroidCacheErrorInvalidationPatched = true;
                  var invalidated = {};
                  function isAndroidCachedMediaUrl(value) {
                    var raw = String(value || '');
                    if (!raw) return false;
                    if (raw.indexOf('/android-cache/') >= 0) return true;
                    if (!/^file:/i.test(raw)) return false;
                    try {
                      var decoded = decodeURIComponent(raw).replace(/\\/g, '/');
                      return decoded.indexOf('SPlayerCache') >= 0 && decoded.indexOf('/music/') >= 0;
                    } catch (e) {
                      return raw.indexOf('SPlayerCache') >= 0;
                    }
                  }
                  function isMediaElement(el) {
                    try {
                      return !!(el && window.HTMLMediaElement && el instanceof HTMLMediaElement);
                    } catch (e) {
                      return false;
                    }
                  }
                  document.addEventListener('error', function (event) {
                    try {
                      var el = event && event.target;
                      if (!isMediaElement(el)) return;
                      var url = el.currentSrc || el.src || '';
                      if (!isAndroidCachedMediaUrl(url)) return;
                      if (invalidated[url]) return;
                      invalidated[url] = Date.now();
                      var code = el.error && el.error.code ? el.error.code : 0;
                      androidBridgeLog('AndroidMusicCache', 'invalidate cached media after element error code=' + code + ' url=' + String(url).slice(0, 220));
                      androidInvoke('music-cache-invalidate', [url, 'media_element_error_' + code]);
                    } catch (e) {}
                  }, true);
                }
                installAndroidCacheErrorInvalidation();

                function chooseDirectory() {
                  return new Promise(function (resolve) {
                    var id = 'android_dir_' + Date.now() + '_' + Math.random().toString(36).slice(2);
                    window.__PakePlusAndroidDirectoryCallbacks[id] = { resolve: resolve };
                    try {
                      if (window.JsBridge && typeof window.JsBridge.chooseDirectory === 'function') {
                        window.JsBridge.chooseDirectory(id);
                      } else {
                        delete window.__PakePlusAndroidDirectoryCallbacks[id];
                        resolve(null);
                      }
                    } catch (e) {
                      delete window.__PakePlusAndroidDirectoryCallbacks[id];
                      resolve(null);
                    }
                  });
                }

                function bytesToBase64(bytes) {
                  var binary = '';
                  var chunk = 0x8000;
                  for (var i = 0; i < bytes.length; i += chunk) {
                    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
                  }
                  return btoa(binary);
                }
                function dataToBase64(data) {
                  if (typeof data === 'string') return bytesToBase64(new TextEncoder().encode(data));
                  if (data instanceof ArrayBuffer) return bytesToBase64(new Uint8Array(data));
                  if (ArrayBuffer.isView(data)) return bytesToBase64(new Uint8Array(data.buffer, data.byteOffset, data.byteLength));
                  if (data && data.type === 'Buffer' && Array.isArray(data.data)) return bytesToBase64(new Uint8Array(data.data));
                  return bytesToBase64(new TextEncoder().encode(String(data == null ? '' : data)));
                }
                function base64ToBytes(base64) {
                  var binary = atob(base64 || '');
                  var bytes = new Uint8Array(binary.length);
                  for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
                  return bytes;
                }

                window.electron = window.electron || {};
                window.electron.ipcRenderer = window.electron.ipcRenderer || {};
                var ipc = window.electron.ipcRenderer;
                var previousInvoke = ipc.invoke;
                var previousSend = ipc.send;
                if (!ipc.__androidCacheBridgePatched) {
                  ipc.invoke = function (channel) {
                    var args = Array.prototype.slice.call(arguments, 1);
                    if (channel === 'choose-path') return chooseDirectory();
                    if (channel === 'store-get') return window.api.store.get(args[0]);
                    if (channel === 'store-set') return window.api.store.set(args[0], args[1]);
                    if (channel === 'store-has') return window.api.store.has(args[0]);
                    if (channel === 'store-delete') return window.api.store.delete(args[0]);
                    if (channel === 'store-reset') return window.api.store.reset(args[0]);
                    if (channel === 'taskbar:get-option' || channel === 'desktop-lyric:get-option') return Promise.resolve({});
                    if (channel === 'win-state') return Promise.resolve(false);
                    if (channel === 'cache-put') return androidInvoke(channel, [args[0], args[1], dataToBase64(args[2])]);
                    if (channel === 'cache-get') {
                      return androidInvoke(channel, args).then(function (res) {
                        if (res && res.success && typeof res.data === 'string') res.data = base64ToBytes(res.data);
                        return res;
                      });
                    }
                    var androidChannels = {
                      'music-cache-check': true,
                      'music-cache-download': true,
                      'music-cache-invalidate': true,
                      'cache-list': true,
                      'cache-remove': true,
                      'cache-clear': true,
                      'cache-clear-all': true,
                      'cache-size': true,
                      'file-exists': true,
                      'get-default-dir': true
                    };
                    if (androidChannels[channel]) return androidInvoke(channel, args);
                    if (typeof previousInvoke === 'function') return previousInvoke.apply(this, [channel].concat(args));
                    return Promise.resolve(null);
                  };
                  ipc.send = function () {
                    if (typeof previousSend === 'function') return previousSend.apply(this, arguments);
                  };
                  ipc.sendSync = ipc.sendSync || function (channel) {
                    if (channel === 'win-state') return false;
                    return null;
                  };
                  ipc.on = ipc.on || function () { return ipc; };
                  ipc.once = ipc.once || function () { return ipc; };
                  ipc.removeListener = ipc.removeListener || function () { return ipc; };
                  ipc.removeAllListeners = ipc.removeAllListeners || function () { return ipc; };
                  ipc.__androidCacheBridgePatched = true;
                }
              } catch (e) {}
            })();
        """.trimIndent()
    }

    private fun injectBridgeIntoHtml(html: String): String {
        val scriptTag = "<script>${androidBridgeScript()}</script>"
        val headStart = html.indexOf("<head", ignoreCase = true)
        if (headStart >= 0) {
            val headEnd = html.indexOf('>', headStart)
            if (headEnd >= 0) {
                return html.substring(0, headEnd + 1) + scriptTag + html.substring(headEnd + 1)
            }
        }
        val htmlStart = html.indexOf("<html", ignoreCase = true)
        if (htmlStart >= 0) {
            val htmlEnd = html.indexOf('>', htmlStart)
            if (htmlEnd >= 0) {
                return html.substring(0, htmlEnd + 1) + "<head>" + scriptTag + "</head>" + html.substring(htmlEnd + 1)
            }
        }
        return scriptTag + html
    }

    private fun tryBuildBridgeHtmlResponse(request: WebResourceRequest?): WebResourceResponse? {
        val req = request ?: return null
        if (!req.isForMainFrame || !req.method.equals("GET", ignoreCase = true)) return null
        val uri = req.url ?: return null
        val scheme = uri.scheme ?: return null
        if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return null
        val path = uri.path.orEmpty()
        val looksLikeDocument = path.isBlank() || path.endsWith("/") || path.endsWith(".html", ignoreCase = true) || path.endsWith(".htm", ignoreCase = true)
        if (!looksLikeDocument) return null
        val accept = req.requestHeaders.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value
        if (accept != null && !accept.contains("text/html", ignoreCase = true)) return null

        return try {
            val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                requestMethod = "GET"
                req.requestHeaders.forEach { (key, value) ->
                    if (!key.equals("Accept-Encoding", ignoreCase = true) && !key.equals("Range", ignoreCase = true)) {
                        setRequestProperty(key, value)
                    }
                }
                if (getRequestProperty("User-Agent").isNullOrBlank()) {
                    setRequestProperty("User-Agent", webViewUserAgent)
                }
            }
            val code = connection.responseCode.takeIf { it in 100..599 } ?: 200
            val contentType = connection.contentType.orEmpty()
            if (!contentType.contains("text/html", ignoreCase = true)) {
                connection.disconnect()
                return null
            }
            val charsetName = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
                .find(contentType)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.trim('\"')
                ?: "UTF-8"
            val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            val html = bytes.toString(charset)
            val injectedBytes = injectBridgeIntoHtml(html).toByteArray(charset)
            val reason = connection.responseMessage ?: if (code < 400) "OK" else "Error"
            val headers = mutableMapOf<String, String>()
            connection.headerFields.forEach { (name, values) ->
                if (name != null && values != null && values.isNotEmpty() &&
                    !name.equals("Content-Encoding", ignoreCase = true) &&
                    !name.equals("Content-Length", ignoreCase = true) &&
                    !name.equals("Content-Security-Policy", ignoreCase = true)
                ) {
                    headers[name] = values.joinToString(",")
                }
            }
            connection.disconnect()
            WebResourceResponse(
                "text/html",
                charset.name(),
                code,
                reason,
                headers,
                ByteArrayInputStream(injectedBytes)
            )
        } catch (e: Exception) {
            Log.w("AndroidBridge", "HTML bridge injection skipped", e)
            null
        }
    }

    private fun tryBuildAndroidCacheResponse(request: WebResourceRequest?): WebResourceResponse? {
        val req = request ?: return null
        val file = androidMusicCache.resolvePlayableUri(req.url) ?: return null
        val mime = when (file.extension.lowercase()) {
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }
        val fileLength = file.length()
        val rangeHeader = req.requestHeaders.entries.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
        var start = 0L
        var end = fileLength - 1L
        var status = 200
        var reason = "OK"
        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
            val parts = rangeHeader.removePrefix("bytes=").split("-", limit = 2)
            start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            end = parts.getOrNull(1)?.toLongOrNull() ?: end
            if (start < 0L) start = 0L
            if (end >= fileLength) end = fileLength - 1L
            if (end < start) end = fileLength - 1L
            status = 206
            reason = "Partial Content"
        }
        val contentLength = (end - start + 1L).coerceAtLeast(0L)
        val headers = mutableMapOf(
            "Accept-Ranges" to "bytes",
            "Content-Length" to contentLength.toString()
        )
        if (status == 206) {
            headers["Content-Range"] = "bytes $start-$end/$fileLength"
        }
        return WebResourceResponse(mime, null, status, reason, headers, limitedFileInputStream(file, start, contentLength))
    }

    private fun tryBuildAudioProxyResponse(request: WebResourceRequest?): WebResourceResponse? {
        val req = request ?: return null
        val uri = req.url ?: return null
        val targetUrl = audioProxyTargetUrl(req, uri) ?: return null

        val originalUrl = targetUrl
        var currentUrl = targetUrl
        Log.i("SPlayerAudioProxy", "request target=$targetUrl virtual=${isAndroidAudioProxyUri(uri)} headers=${req.requestHeaders}")
        var redirectCount = 0
        while (redirectCount <= 5) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 45000
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", audioProxyUserAgent())
                    setRequestProperty("Accept", req.requestHeaders.entries.firstOrNull {
                        it.key.equals("Accept", ignoreCase = true)
                    }?.value?.takeIf { it.isNotBlank() } ?: "*/*")
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("Connection", "close")
                    req.requestHeaders.forEach { (key, value) ->
                        if (value.isBlank()) return@forEach
                        if (key.equals("User-Agent", ignoreCase = true) ||
                            key.equals("Accept", ignoreCase = true) ||
                            key.equals("Accept-Encoding", ignoreCase = true) ||
                            key.equals("Connection", ignoreCase = true) ||
                            key.equals("Host", ignoreCase = true) ||
                            key.equals("Origin", ignoreCase = true) ||
                            key.equals("Referer", ignoreCase = true) ||
                            key.equals("Cookie", ignoreCase = true)
                        ) {
                            return@forEach
                        }
                        setRequestProperty(key, value)
                    }
                }
                val code = connection.responseCode.takeIf { it in 100..599 } ?: 200
                val message = connection.responseMessage ?: defaultHttpReason(code)
                val range = req.requestHeaders.entries.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
                Log.i(
                    "SPlayerAudioProxy",
                    "response original=$originalUrl current=$currentUrl code=$code message=$message " +
                            "range=$range type=${connection.contentType} length=${connection.contentLengthLong} " +
                            "acceptRanges=${connection.getHeaderField("Accept-Ranges")} " +
                            "contentRange=${connection.getHeaderField("Content-Range")} " +
                            "location=${connection.getHeaderField("Location")}"
                )
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrBlank()) {
                        Log.w("SPlayerAudioProxy", "redirect without location original=$originalUrl current=$currentUrl code=$code")
                        return null
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirectCount += 1
                    continue
                }

                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                val body = stream ?: ByteArrayInputStream(ByteArray(0))
                val headers = audioProxyResponseHeaders(connection)
                val mime = connection.contentType
                    ?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: inferAudioMimeFromUrl(currentUrl)
                val response = WebResourceResponse(
                    mime,
                    null,
                    code,
                    message.ifBlank { defaultHttpReason(code) },
                    headers,
                    disconnectingInputStream(body, connection)
                )
                connection = null
                return response
            } catch (e: Exception) {
                Log.e("SPlayerAudioProxy", "proxy failed original=$originalUrl current=$currentUrl redirect=$redirectCount", e)
                connection?.disconnect()
                return null
            }
        }
        Log.e("SPlayerAudioProxy", "proxy failed original=$originalUrl reason=too_many_redirects lastUrl=$currentUrl")
        return null
    }

    private fun audioProxyTargetUrl(req: WebResourceRequest, uri: Uri): String? {
        if (!req.method.equals("GET", ignoreCase = true)) return null
        if (isAndroidAudioProxyUri(uri)) {
            val target = uri.getQueryParameter("u")?.trim().orEmpty()
            if (target.startsWith("http://", ignoreCase = true) ||
                target.startsWith("https://", ignoreCase = true)
            ) {
                return target
            }
            Log.w("SPlayerAudioProxy", "virtual proxy request missing target url=${uri}")
            return null
        }
        return if (isLikelyRemoteAudioRequest(req, uri)) uri.toString() else null
    }

    private fun isAndroidAudioProxyUri(uri: Uri): Boolean {
        return uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("splayer.android.local", ignoreCase = true) &&
                uri.path.equals("/__audio_proxy__", ignoreCase = true)
    }
    private fun isLikelyRemoteAudioRequest(req: WebResourceRequest, uri: Uri): Boolean {
        if (req.isForMainFrame || !req.method.equals("GET", ignoreCase = true)) return false
        val scheme = uri.scheme ?: return false
        if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return false
        val path = uri.path.orEmpty().lowercase()
        val audioExtensions = listOf(".mp3", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wav")
        if (audioExtensions.any { path.endsWith(it) || path.contains("$it/") }) return true
        val accept = req.requestHeaders.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value.orEmpty()
        val range = req.requestHeaders.entries.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value.orEmpty()
        return accept.contains("audio/", ignoreCase = true) && range.startsWith("bytes=", ignoreCase = true)
    }

    private fun audioProxyUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    }

    private fun inferAudioMimeFromUrl(url: String): String {
        val path = runCatching { Uri.parse(url).path.orEmpty().lowercase() }
            .getOrDefault(url.lowercase())
        return when {
            path.endsWith(".flac") || path.contains(".flac/") -> "audio/flac"
            path.endsWith(".m4a") || path.contains(".m4a/") -> "audio/mp4"
            path.endsWith(".aac") || path.contains(".aac/") -> "audio/aac"
            path.endsWith(".ogg") || path.contains(".ogg/") -> "audio/ogg"
            path.endsWith(".opus") || path.contains(".opus/") -> "audio/ogg"
            path.endsWith(".wav") || path.contains(".wav/") -> "audio/wav"
            else -> "audio/mpeg"
        }
    }

    private fun audioProxyResponseHeaders(connection: HttpURLConnection): MutableMap<String, String> {
        val headers = mutableMapOf<String, String>()
        connection.headerFields.forEach { (name, values) ->
            if (name != null && values != null && values.isNotEmpty() &&
                !name.equals("Transfer-Encoding", ignoreCase = true) &&
                !name.equals("Content-Encoding", ignoreCase = true) &&
                !name.equals("Connection", ignoreCase = true)
            ) {
                headers[name] = values.joinToString(",")
            }
        }
        if (!headers.keys.any { it.equals("Accept-Ranges", ignoreCase = true) }) {
            headers["Accept-Ranges"] = "bytes"
        }
        headers["Access-Control-Allow-Origin"] = "*"
        headers["Access-Control-Allow-Headers"] = "Range, Origin, Accept, Content-Type"
        headers["Access-Control-Expose-Headers"] = "Content-Length, Content-Range, Accept-Ranges"
        return headers
    }

    private fun defaultHttpReason(code: Int): String {
        return when (code) {
            200 -> "OK"
            206 -> "Partial Content"
            301 -> "Moved Permanently"
            302 -> "Found"
            303 -> "See Other"
            307 -> "Temporary Redirect"
            308 -> "Permanent Redirect"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            416 -> "Range Not Satisfiable"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> if (code < 400) "OK" else "Error"
        }
    }

    private fun disconnectingInputStream(input: InputStream, connection: HttpURLConnection): InputStream {
        return object : InputStream() {
            override fun read(): Int = input.read()
            override fun read(buffer: ByteArray, offset: Int, len: Int): Int = input.read(buffer, offset, len)
            override fun close() {
                try {
                    input.close()
                } finally {
                    connection.disconnect()
                }
            }
        }
    }
    private fun limitedFileInputStream(file: File, start: Long, length: Long): InputStream {
        val input = FileInputStream(file)
        var skipped = 0L
        while (skipped < start) {
            val delta = input.skip(start - skipped)
            if (delta <= 0L) break
            skipped += delta
        }
        return object : InputStream() {
            private var remaining = length
            override fun read(): Int {
                if (remaining <= 0L) return -1
                val value = input.read()
                if (value >= 0) remaining--
                return value
            }
            override fun read(buffer: ByteArray, offset: Int, len: Int): Int {
                if (remaining <= 0L) return -1
                val toRead = minOf(len.toLong(), remaining).toInt()
                val read = input.read(buffer, offset, toRead)
                if (read > 0) remaining -= read.toLong()
                return read
            }
            override fun close() {
                input.close()
            }
        }
    }

    private fun injectAndroidWebBridge(view: WebView?) {
        val target = view ?: return
        val script = androidBridgeScript()
        target.post { target.evaluateJavascript(script, null) }
    }
    private fun handleAndroidIpcInvoke(callbackId: String?, channel: String?, argsJson: String?) {
        if (callbackId.isNullOrBlank()) return
        thread {
            val resultJson = try {
                val args = try {
                    JSONArray(argsJson ?: "[]")
                } catch (_: Exception) {
                    JSONArray()
                }
                handleAndroidIpc(channel, args)
            } catch (e: Exception) {
                Log.e("AndroidIpc", "IPC failed: $channel", e)
                JSONObject()
                    .put("success", false)
                    .put("message", e.message ?: "Android IPC error")
                    .toString()
            }
            notifyAndroidIpcResult(callbackId, resultJson)
        }
    }

    private fun handleAndroidIpc(channel: String?, args: JSONArray): String {
        return when (channel) {
            "music-cache-check" -> {
                val id = args.optStringOrNull(0) ?: return "null"
                val quality = args.optStringOrNull(1)
                val md5 = args.optStringOrNull(2)
                val path = androidMusicCache.hasCache(id, quality, md5)
                path?.let { JSONObject.quote(it) } ?: "null"
            }
            "music-cache-download" -> {
                val id = args.optStringOrNull(0) ?: throw IllegalArgumentException("missing song id")
                val url = args.optStringOrNull(1) ?: throw IllegalArgumentException("missing source url")
                val quality = args.optStringOrNull(2)
                val path = androidMusicCache.cacheMusic(id, url, quality, webViewUserAgent)
                JSONObject().put("success", true).put("path", path).toString()
            }
            "music-cache-invalidate" -> {
                val target = args.optStringOrNull(0)
                val reason = args.optStringOrNull(1)
                cacheSuccess(androidMusicCache.invalidatePlayablePath(target, reason))
            }
            "cache-size" -> cacheSuccess(androidMusicCache.getSize())
            "cache-clear-all" -> {
                androidMusicCache.clearAll()
                cacheSuccess(null)
            }
            "cache-clear" -> {
                androidMusicCache.clear(args.optStringOrNull(0))
                cacheSuccess(null)
            }
            "cache-list" -> {
                JSONObject().put("success", true).put("data", androidMusicCache.list(args.optStringOrNull(0))).toString()
            }
            "cache-remove" -> {
                androidMusicCache.remove(args.optStringOrNull(0), args.optStringOrNull(1))
                cacheSuccess(null)
            }
            "cache-get" -> {
                val bytes = androidMusicCache.get(args.optStringOrNull(0), args.optStringOrNull(1))
                cacheSuccess(bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) })
            }
            "cache-put" -> {
                val type = args.optStringOrNull(0)
                val key = args.optStringOrNull(1)
                val payload = args.optStringOrNull(2).orEmpty()
                androidMusicCache.put(type, key, Base64.decode(payload, Base64.DEFAULT))
                cacheSuccess(null)
            }
            "file-exists" -> {
                val path = args.optStringOrNull(0)
                val file = path?.let {
                    if (it.startsWith("file://", ignoreCase = true)) Uri.parse(it).path else it
                }?.let { File(it) }
                ((file?.isFile == true) && file.length() > 0L).toString()
            }
            "get-default-dir" -> JSONObject.quote(androidMusicCache.defaultCachePath())
            else -> "null"
        }
    }

    private fun cacheSuccess(data: Any?): String {
        val json = JSONObject().put("success", true)
        if (data == null) json.put("data", JSONObject.NULL) else json.put("data", data)
        return json.toString()
    }

    private fun JSONArray.optStringOrNull(index: Int): String? {
        if (index < 0 || index >= length() || isNull(index)) return null
        val value = opt(index) ?: return null
        if (value == JSONObject.NULL) return null
        return value.toString().takeIf { it.isNotBlank() && it != "null" }
    }

    private fun notifyAndroidIpcResult(callbackId: String, resultJson: String) {
        if (!::webView.isInitialized) return
        val idJson = JSONObject.quote(callbackId)
        val script = "window.__PakePlusAndroidIpcResolved && window.__PakePlusAndroidIpcResolved($idJson, $resultJson);"
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun injectMediaSessionBridge(view: WebView?) {
        startMediaStatePolling(view)
    }
    private fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        // 如果已经有全屏视图，先隐藏它
        if (customView != null) {
            hideCustomView()
            return
        }

        customView = view
        customViewCallback = callback

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 保存当前屏幕方向
        originalOrientation = requestedOrientation

        // 获取根布局
        val decorView = window.decorView as ViewGroup
        val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)

        // 创建全屏容器
        val fullscreenContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        // 将全屏视图添加到容器
        fullscreenContainer.addView(view)

        // 将容器添加到根布局
        rootView.addView(fullscreenContainer)

        // 隐藏系统UI
        hideSystemUI()

        // 隐藏WebView
        webView.visibility = View.GONE
    }

    // 隐藏全屏视频
    private fun hideCustomView() {
        if (customView == null) return

        // 恢复系统UI
        showSystemUI()

        // 显示WebView
        webView.visibility = View.VISIBLE

        // 获取根布局
        val decorView = window.decorView as ViewGroup
        val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)

        // 移除全屏容器
        val fullscreenContainer = customView?.parent as? ViewGroup
        fullscreenContainer?.let {
            rootView.removeView(it)
        }

        // 调用回调
        customViewCallback?.onCustomViewHidden()

        // 清理
        customView = null
        customViewCallback = null

        // 恢复屏幕方向
        requestedOrientation = originalOrientation

        if (!keepScreenOnFromConfig) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 隐藏系统UI（全屏模式）
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                // 设置系统栏行为：通过滑动显示临时栏
                try {
                    @Suppress("NewApi")
                    it.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } catch (e: Exception) {
                    // 如果常量不可用，忽略此设置
                    Log.w("MainActivity", "BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE not available", e)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }
    }

    // 显示系统UI
    private fun showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
        }
        applyBlackSystemBars()
        if (::webView.isInitialized) {
            injectAndroidWebBridge(webView)
        }
    }

    fun parseJsonWithNative(context: Context, jsonFilePath: String): Map<String, Any>? {
        val jsonString = assets.open(jsonFilePath).bufferedReader().use { it.readText() }
        return try {
            val jsonObject = JSONObject(jsonString)
            // 提取字段
            val name = jsonObject.getString("name")
            val webUrl = jsonObject.getString("webUrl")
            val debug = jsonObject.getBoolean("debug")
            val userAgent = jsonObject.getString("userAgent")
            val fullScreen = jsonObject.getBoolean("fullScreen")
            val launch = jsonObject.getString("launch")
            val screenOn = jsonObject.optBoolean("screenOn", false)
            val gesture = jsonObject.optBoolean("gesture", false)
            val clearCache = jsonObject.optBoolean("clearCache", false)
            val setZoom = jsonObject.optBoolean("setZoom", false)
            val callPhone = jsonObject.optBoolean("callPhone", false)
            val download = jsonObject.optBoolean("download", false)
            val camera = jsonObject.optBoolean("camera", false)
            val microphone = jsonObject.optBoolean("microphone", false)
            val position = jsonObject.optBoolean("position", false)
            val upload = jsonObject.optBoolean("upload", false)
            val gallery = jsonObject.optBoolean("gallery", false)
            val browser = jsonObject.optBoolean("browser", false)
            val wechat = jsonObject.optBoolean("wechat", false)
            val douyin = jsonObject.optBoolean("douyin", false)
            val videoFull = jsonObject.optBoolean("videoFull", true)
            val backgroundPlay = jsonObject.optBoolean("backgroundPlay", false)
            val injectJs = jsonObject.optBoolean("injectJs", true)
            val javaScriptEnabled = jsonObject.optBoolean("javaScriptEnabled", true)
            val domStorageEnabled = jsonObject.optBoolean("domStorageEnabled", true)
            val allowFileAccess = jsonObject.optBoolean("allowFileAccess", false)
            val loadWithOverviewMode = jsonObject.optBoolean("loadWithOverviewMode", true)
            // 返回键值对
            mapOf(
                "name" to name,
                "webUrl" to webUrl,
                "debug" to debug,
                "userAgent" to userAgent,
                "fullScreen" to fullScreen,
                "launch" to launch,
                "screenOn" to screenOn,
                "gesture" to gesture,
                "clearCache" to clearCache,
                "setZoom" to setZoom,
                "callPhone" to callPhone,
                "download" to download,
                "camera" to camera,
                "microphone" to microphone,
                "position" to position,
                "upload" to upload,
                "gallery" to gallery,
                "browser" to browser,
                "wechat" to wechat,
                "douyin" to douyin,
                "videoFull" to videoFull,
                "backgroundPlay" to backgroundPlay,
                "injectJs" to injectJs,
                "javaScriptEnabled" to javaScriptEnabled,
                "domStorageEnabled" to domStorageEnabled,
                "allowFileAccess" to allowFileAccess,
                "loadWithOverviewMode" to loadWithOverviewMode
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * JS 调用的接口
     */
    inner class JsInterface(private val context: Context) {

        // 接收 base64 数据并保存为文件
        @JavascriptInterface
        fun downloadBase64File(base64Data: String, mimeType: String?, fileName: String?) {
            (context as? MainActivity)?.runOnUiThread {
                try {
                    if (!allowDownloadFromConfig) {
                        showTopToast(context, "Download is disabled", Toast.LENGTH_SHORT)
                        return@runOnUiThread
                    }
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    saveDecodedDownload(bytes, mimeType, fileName)
                } catch (e: Exception) {
                    Log.e("BlobDownload", "save error", e)
                    showTopToast(context, "保存失败: ${e.message}", Toast.LENGTH_LONG)
                }
            }
        }

        // 接收一个url，用默认浏览器打开
        @JavascriptInterface
        fun openUrl(url: String) {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        }

        // 判断是不是安卓客户端
        @JavascriptInterface
        fun isAndroid(): Boolean {
            return true
        }

        // is app
        @JavascriptInterface
        fun isApp(): Boolean {
            return true
        }

        @JavascriptInterface
        fun chooseDirectory(callbackId: String?) {
            (context as? MainActivity)?.openAndroidDirectoryPicker(callbackId)
        }

        @JavascriptInterface
        fun androidIpcInvoke(callbackId: String?, channel: String?, argsJson: String?) {
            (context as? MainActivity)?.handleAndroidIpcInvoke(callbackId, channel, argsJson)
        }

        @JavascriptInterface
        fun androidLog(tag: String?, message: String?) {
            val cleanTag = tag?.replace(Regex("[^A-Za-z0-9_.-]"), "")?.take(32)?.ifBlank { null } ?: "AndroidJs"
            Log.i(cleanTag, message?.take(1000) ?: "")
        }

        @JavascriptInterface
        fun updateMediaState(isPlaying: Boolean, title: String?, artist: String?) {
            (context as? MainActivity)?.runOnUiThread {
                if (!allowBackgroundPlayFromConfig) return@runOnUiThread
                updateMediaStateFromWeb(
                    isPlaying = isPlaying,
                    title = title,
                    artist = artist,
                    album = null,
                    artworkUrl = null,
                    durationSeconds = mediaDurationMs / 1000.0,
                    positionSeconds = currentMediaPositionMs() / 1000.0,
                    playbackRate = mediaPlaybackRateFromWeb.toDouble()
                )
            }
        }
    }
    /** 将解码后的文件写入公共 Download 目录（与 JsBridge / data: 下载共用） */
    private fun saveDecodedDownload(bytes: ByteArray, mimeType: String?, fileName: String?) {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val safeName = when {
            !fileName.isNullOrBlank() -> fileName
            !mimeType.isNullOrBlank() -> {
                val ext = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mimeType) ?: "bin"
                "download_${System.currentTimeMillis()}.$ext"
            }

            else -> "download_${System.currentTimeMillis()}.bin"
        }

        val outFile = File(downloadsDir, safeName)
        FileOutputStream(outFile).use { it.write(bytes) }

        showTopToast(this, "已保存到下载目录: ${outFile.name}", Toast.LENGTH_LONG)
        Log.d("BlobDownload", "File saved: ${outFile.absolutePath}")
    }

    /**
     * Canvas 等生成的 data:/blob: 链接不能走 DownloadManager。
     * @return true 表示已处理或已主动放弃（切勿再 enqueue）
     */
    private fun tryHandleSpecialSchemeDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?
    ): Boolean {
        when {
            url.startsWith("data:", ignoreCase = true) -> {
                if (!trySaveDataUrlToDownloads(url, contentDisposition, mimetype)) {
                    showTopToast(this, "无法保存此链接（data URL 解析失败）", Toast.LENGTH_SHORT)
                }
                return true
            }

            url.startsWith("blob:", ignoreCase = true) -> {
                saveBlobUrlViaJavaScript(url, contentDisposition, mimetype)
                return true
            }

            else -> return false
        }
    }

    private fun trySaveDataUrlToDownloads(
        dataUrl: String,
        contentDisposition: String?,
        mimetype: String?
    ): Boolean {
        return try {
            val comma = dataUrl.indexOf(',')
            if (comma < 0) return false
            val meta = dataUrl.substring(5, comma)
            val payload = dataUrl.substring(comma + 1)
            val isBase64 = meta.contains(";base64", ignoreCase = true)
            val mimeFromMeta = meta.substringBefore(';').trim().takeIf { it.isNotEmpty() }
            val effectiveMime = mimetype?.takeIf { it.isNotBlank() } ?: mimeFromMeta
            val bytes = if (isBase64) {
                Base64.decode(payload, Base64.DEFAULT)
            } else {
                URLDecoder.decode(payload, StandardCharsets.UTF_8.name())
                    .toByteArray(StandardCharsets.UTF_8)
            }
            val name = URLUtil.guessFileName(dataUrl, contentDisposition, effectiveMime)
            saveDecodedDownload(bytes, effectiveMime, name)
            true
        } catch (e: Exception) {
            Log.e("WebViewDownload", "data URL save failed", e)
            false
        }
    }

    /** DownloadListener 收到 blob: 时走 WebView 内 fetch + JsBridge（与页面注入逻辑一致） */
    private fun saveBlobUrlViaJavaScript(
        blobUrl: String,
        contentDisposition: String?,
        mimetype: String?
    ) {
        val quotedUrl = JSONObject.quote(blobUrl)
        val guessed = URLUtil.guessFileName(blobUrl, contentDisposition, mimetype)
        val quotedName = JSONObject.quote(guessed)
        val script = """
            (function(){
              try {
                var u = $quotedUrl;
                var defaultName = $quotedName;
                fetch(u).then(function(r){ return r.blob(); }).then(function(blob){
                  var reader = new FileReader();
                  reader.onloadend = function() {
                    try {
                      var dataUrl = reader.result || '';
                      var i = dataUrl.indexOf(',');
                      var b64 = i >= 0 ? dataUrl.substring(i + 1) : dataUrl;
                      var mime = blob.type || 'application/octet-stream';
                      if (window.JsBridge && window.JsBridge.downloadBase64File) {
                        window.JsBridge.downloadBase64File(b64, mime, defaultName);
                      }
                    } catch (e) { console.error(e); }
                  };
                  reader.readAsDataURL(blob);
                }).catch(function(e){ console.error('blob fetch', e); });
              } catch (e2) { console.error(e2); }
            })();
        """.trimIndent()
        webView.post {
            if (::webView.isInitialized) {
                webView.evaluateJavascript(script, null)
            }
        }
    }

    /**
     * 根据 URL / Content-Disposition / MIME 开始一个系统下载任务
     * - 对常见的 mp4 纠正被识别成 .bin 的问题
     * - 供 WebView DownloadListener 和 shouldOverrideUrlLoading 共用
     */
    private fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?
    ) {
        if (!allowDownloadFromConfig) {
            showTopToast(this, "Download is disabled", Toast.LENGTH_SHORT)
            return
        }
        // 1. 先根据 URL / Content-Disposition / MIME 推测文件名
        var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)

        // 2. 处理 mp4 被识别成 .bin 的场景
        val lowerMime = mimetype?.lowercase() ?: ""
        val lowerName = fileName.lowercase()

        val isVideoMp4 = lowerMime.contains("video/mp4") ||
                (lowerMime.contains("application/octet-stream") && url.contains(
                    ".mp4",
                    ignoreCase = true
                ))

        if (isVideoMp4) {
            fileName = when {
                lowerName.endsWith(".mp4") -> fileName
                lowerName.endsWith(".bin") -> fileName.replace(
                    Regex(
                        "\\.bin$",
                        RegexOption.IGNORE_CASE
                    ), ".mp4"
                )

                !fileName.contains('.') -> "$fileName.mp4"
                else -> fileName
            }
        }

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            // 对于 mp4 强制使用正确的 MIME，避免部分 ROM 再次误判
            if (isVideoMp4) {
                setMimeType("video/mp4")
            } else if (!mimetype.isNullOrEmpty()) {
                setMimeType(mimetype)
            }

            if (!userAgent.isNullOrEmpty()) {
                addRequestHeader("User-Agent", userAgent)
            }
            setDescription(getString(R.string.downloading))
            setTitle(fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        try {
            dm.enqueue(request)
            showTopToast(this, getString(R.string.download_started), Toast.LENGTH_SHORT)
        } catch (e: Exception) {
            Log.e("WebViewDownload", "DownloadManager.enqueue failed: $url", e)
            showTopToast(this, "下载失败: ${e.message}", Toast.LENGTH_LONG)
        }
    }

    /**
     * 将 Toast 显示在屏幕顶部
     */
    private fun showTopToast(context: Context, message: String, duration: Int) {
        val toast = Toast.makeText(context, message, duration)
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 120)
        toast.show()
    }

    /**
     * 判断一个 URL 是否是“常见文件类型”，用于自动触发下载
     */
    private fun isDownloadableFileUrl(url: String): Boolean {
        val checkUrl = url.substringBefore("?").substringBefore("#").lowercase()
        // 可按需要继续扩展
        val exts = listOf(
            "mp4", "mov", "mkv", "avi",
            "mp3", "aac", "wav", "flac",
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "txt", "pdf",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "rar", "7z"
        )
        return exts.any { checkUrl.endsWith(".$it") }
    }

    private fun isExternalSchemeAllowed(url: String): Boolean {
        val scheme = Uri.parse(url).scheme?.lowercase() ?: return false
        if (scheme == "weixin" || scheme == "wechat") return allowWechatFromConfig
        if (scheme == "snssdk1128" || scheme == "douyin" || scheme == "aweme") {
            return allowDouyinFromConfig
        }
        if (scheme == "intent") {
            return allowExternalBrowserFromConfig || allowWechatFromConfig || allowDouyinFromConfig
        }
        return allowExternalBrowserFromConfig
    }

//    override fun onCreateOptionsMenu(menu: Menu): Boolean {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        menuInflater.inflate(R.menu.main, menu)
//        return true
//    }

//    override fun onSupportNavigateUp(): Boolean {
//        val navController = findNavController(R.id.nav_host_fragment_content_main)
//        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
//    }

    private fun hideSplashOverlay() {
        if (!showLaunchSplash) return
        val overlay = findViewById<View>(R.id.splash_overlay)
        if (overlay.visibility != View.VISIBLE) return
        overlay.animate()
            .alpha(0f)
            .setDuration(200L)
            .withEndAction {
                overlay.visibility = View.GONE
                overlay.alpha = 1f
            }
            .start()
    }

    inner class MyWebViewClient(val debug: Boolean) : WebViewClient() {

        private fun handleOverrideUrl(view: WebView?, rawUrl: String?): Boolean {
            if (rawUrl.isNullOrBlank()) return false
            val fixedUrl = rawUrl.toString()

            // tel: 用系统拨号器打开（ACTION_DIAL 不需要 CALL_PHONE 权限）
            if (fixedUrl.startsWith("tel:", ignoreCase = true)) {
                if (!allowCallPhoneFromConfig) {
                    showTopToast(this@MainActivity, "已禁用拨打电话功能", Toast.LENGTH_SHORT)
                    return true
                }
                // Android 11+ 上 resolveActivity 可能因“包可见性”返回 null，即使系统存在拨号器；
                // 这里直接尝试启动并捕获异常更可靠。
                return try {
                    val intent = Intent(Intent.ACTION_DIAL, fixedUrl.toUri())
                    view?.context?.startActivity(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    showTopToast(this@MainActivity, "未找到可拨号的应用", Toast.LENGTH_SHORT)
                    true
                } catch (e: Exception) {
                    Log.e("WebViewClient", "Error handling tel url: $fixedUrl", e)
                    true
                }
            }

            // 对常见文件类型的 HTTP/HTTPS 链接，直接拦截为下载，不在 WebView 内打开
            if (fixedUrl.startsWith("http://") || fixedUrl.startsWith("https://")) {
                if (allowDownloadFromConfig && isDownloadableFileUrl(fixedUrl)) {
                    val ua = view?.settings?.userAgentString ?: ""
                    // 根据扩展名推断 MIME
                    val ext = MimeTypeMap.getFileExtensionFromUrl(fixedUrl)
                    val mime = ext?.let {
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase())
                    }
                        ?: "application/octet-stream"
                    this@MainActivity.startDownload(fixedUrl, ua, null, mime)
                    return true
                }
                // 普通网页，交给 WebView 处理
                return false
            }

            // file:// 链接仍交给 WebView 处理
            if (fixedUrl.startsWith("file://")) {
                return false
            }

            if (!isExternalSchemeAllowed(fixedUrl)) {
                showTopToast(this@MainActivity, "External app launch is disabled", Toast.LENGTH_SHORT)
                return true
            }

            // --- 处理外部应用链接 ---
            // 1. 检查是否是 Intent URI (e.g., intent://...)
            if (fixedUrl.startsWith("intent://")) {
                try {
                    // 解析 Intent URI
                    val intent = Intent.parseUri(fixedUrl, Intent.URI_INTENT_SCHEME)

                    val pm = view?.context?.packageManager
                    if (pm != null && intent.resolveActivity(pm) != null) {
                        view.context.startActivity(intent)
                        return true // 已经处理，阻止 WebView 加载
                    }

                    // 如果找不到能处理的应用，可以尝试打开备用 URL (如果 Intent 中有定义 fallback URL)
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (!fallbackUrl.isNullOrEmpty()) {
                        view?.loadUrl(fallbackUrl)
                        return true // 加载备用 URL
                    }

                } catch (e: URISyntaxException) {
                    // 解析 Intent URI 失败
                    Log.e("WebViewClient", "Bad Intent URI: $fixedUrl", e)
                } catch (e: ActivityNotFoundException) {
                    // 找不到匹配的 Activity (外部应用未安装)，此情况通常在 `resolveActivity` 后捕获
                    Log.e("WebViewClient", "No activity found to handle Intent: $fixedUrl", e)
                }
                // 如果是 Intent 但无法处理，继续执行下面的 Scheme 检查
            }

            // 3. 检查是否是其他自定义 Scheme (e.g., weixin://, zhihu://, mailto://, sms://)
            return try {
                val intent = Intent(Intent.ACTION_VIEW, fixedUrl.toUri())
                val pm = view?.context?.packageManager
                if (pm != null && intent.resolveActivity(pm) != null) {
                    view.context.startActivity(intent)
                    true // 已经处理，阻止 WebView 加载
                } else {
                    Log.w("WebViewClient", "No activity to handle: $fixedUrl")
                    // 拦截掉未知 scheme，避免 WebView 报 UNKNOWN_URL_SCHEME
                    !fixedUrl.startsWith("about:", ignoreCase = true) &&
                        !fixedUrl.startsWith("javascript:", ignoreCase = true)
                }
            } catch (e: Exception) {
                Log.e("WebViewClient", "Error starting external app: $fixedUrl", e)
                // 拦截掉异常的 scheme，避免 WebView 报 UNKNOWN_URL_SCHEME
                true
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("false"))
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return handleOverrideUrl(view, url)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString()
            return handleOverrideUrl(view, url)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            return tryBuildAndroidCacheResponse(request)
                ?: tryBuildAudioProxyResponse(request)
                ?: tryBuildBridgeHtmlResponse(request)
                ?: super.shouldInterceptRequest(view, request)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Log.e(
                "SPlayerWebView",
                "onReceivedError " +
                        "url=${request?.url} " +
                        "mainFrame=${request?.isForMainFrame} " +
                        "method=${request?.method} " +
                        "errorCode=${error?.errorCode} " +
                        "description=${error?.description} " +
                        "requestHeaders=${request?.requestHeaders}"
            )
            if (showLaunchSplash && request?.isForMainFrame == true) {
                mainFrameLoadError = true
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            Log.e(
                "SPlayerWebView",
                "onReceivedHttpError " +
                        "url=${request?.url} " +
                        "mainFrame=${request?.isForMainFrame} " +
                        "method=${request?.method} " +
                        "status=${errorResponse?.statusCode} " +
                        "reason=${errorResponse?.reasonPhrase} " +
                        "mime=${errorResponse?.mimeType} " +
                        "encoding=${errorResponse?.encoding} " +
                        "requestHeaders=${request?.requestHeaders} " +
                        "responseHeaders=${errorResponse?.responseHeaders}"
            )
            if (showLaunchSplash && request?.isForMainFrame == true) {
                val code = errorResponse?.statusCode ?: 0
                if (code >= 400) mainFrameLoadError = true
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            this@MainActivity.applyBlackSystemBars()
            this@MainActivity.injectAndroidWebBridge(view)
            // post 一次，尽量避免与 onReceivedError / onReceivedHttpError 的时序竞态
            view?.post {
                if (!mainFrameLoadError) hideSplashOverlay()
            }
            if (allowBackgroundPlayFromConfig) {
                injectMediaSessionBridge(view)
            }
            // 注入脚本，拦截 blob:/data: 链接并通过 JsBridge 保存到本地（避免走 DownloadManager 闪退）
            if (!allowDownloadFromConfig) return
            val blobInterceptor = """
                (function () {
                  if (window.__blobDownloadInjected) return;
                  window.__blobDownloadInjected = true;
                  
                  document.addEventListener('click', function (e) {
                    try {
                      var target = e.target;
                      // 寻找最近的 <a> 标签
                      while (target && target.tagName && target.tagName.toLowerCase() !== 'a') {
                        target = target.parentElement;
                      }
                      if (!target) return;
                      
                      var href = target.getAttribute('href');
                      if (!href) return;
                      var isBlob = href.indexOf('blob:') === 0;
                      var isData = href.indexOf('data:') === 0;
                      if (!isBlob && !isData) return;
                      
                      e.preventDefault();
                      e.stopPropagation();
                      
                      var fileName = target.getAttribute('download') || 'download-' + Date.now();
                      
                      if (isData) {
                        try {
                          var comma = href.indexOf(',');
                          if (comma < 0) return;
                          var meta = href.substring(5, comma);
                          var payload = href.substring(comma + 1);
                          if (meta.indexOf(';base64') === -1) return;
                          var mime = (meta.split(';')[0] || 'application/octet-stream').trim();
                          if (window.JsBridge && window.JsBridge.downloadBase64File) {
                            window.JsBridge.downloadBase64File(payload, mime, fileName);
                          }
                        } catch (errD) {
                          console.error('data: download error', errD);
                        }
                        return;
                      }
                      
                      fetch(href)
                        .then(function (res) { return res.blob(); })
                        .then(function (blob) {
                          var reader = new FileReader();
                          reader.onloadend = function () {
                            try {
                              var dataUrl = reader.result || '';
                              var commaIndex = dataUrl.indexOf(',');
                              var base64 = commaIndex >= 0 ? dataUrl.substring(commaIndex + 1) : dataUrl;
                              var mime = blob.type || 'application/octet-stream';
                              if (window.JsBridge && window.JsBridge.downloadBase64File) {
                                window.JsBridge.downloadBase64File(base64, mime, fileName);
                              } else {
                                console.error('JsBridge not found on window');
                              }
                            } catch (err) {
                              console.error('Blob download convert error', err);
                            }
                          };
                          reader.readAsDataURL(blob);
                        })
                        .catch(function (err) {
                          console.error('Blob download fetch error', err);
                        });
                    } catch (e2) {
                      console.error('Blob download interceptor error', e2);
                    }
                  }, true);
                })();
            """.trimIndent()

            view?.evaluateJavascript(blobInterceptor, null)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (showLaunchSplash) mainFrameLoadError = false
            if (debug) {
                // vConsole
                val vConsole = assets.open("vConsole.js").bufferedReader().use { it.readText() }
                val openDebug = """var vConsole = new window.VConsole()"""
                view?.evaluateJavascript(vConsole + openDebug, null)
            }
            if (allowInjectJsFromConfig) {
                val injectJs = assets.open("custom.js").bufferedReader().use { it.readText() }
                view?.evaluateJavascript(injectJs, null)
            }
        }
    }

    inner class MyChromeClient(private val activity: MainActivity) : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            val url = view?.url
            println("wev view url:$url")
        }

        // 处理 getUserMedia 权限请求（摄像头 / 麦克风）
        override fun onPermissionRequest(request: PermissionRequest?) {
            if (request == null) return

            activity.runOnUiThread {
                val resources = request.resources

                // 需要对应的原生权限
                val needPermissions = mutableListOf<String>()
                if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    if (!activity.allowCameraFromConfig) {
                        request.deny()
                        return@runOnUiThread
                    }
                    needPermissions.add(Manifest.permission.CAMERA)
                }
                if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    if (!activity.allowMicrophoneFromConfig) {
                        request.deny()
                        return@runOnUiThread
                    }
                    needPermissions.add(Manifest.permission.RECORD_AUDIO)
                }

                if (needPermissions.isEmpty()) {
                    // 不涉及摄像头/麦克风，直接允许
                    request.grant(resources)
                    return@runOnUiThread
                }

                // 检查是否已经有原生权限
                val notGranted = needPermissions.filter {
                    ContextCompat.checkSelfPermission(
                        activity,
                        it
                    ) != PackageManager.PERMISSION_GRANTED
                }

                if (notGranted.isEmpty()) {
                    // 已经有权限，直接授予给 WebView
                    request.grant(resources)
                } else {
                    // 先请求原生权限，保存 WebView 的请求
                    activity.pendingPermissionRequest?.deny()
                    activity.pendingPermissionRequest = request
                    activity.permissionLauncher.launch(notGranted.toTypedArray())
                }
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
            super.onPermissionRequestCanceled(request)
            if (activity.pendingPermissionRequest == request) {
                activity.pendingPermissionRequest = null
            }
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?
        ) {
            if (origin == null || callback == null) {
                super.onGeolocationPermissionsShowPrompt(origin, callback)
                return
            }
            if (!activity.allowPositionFromConfig) {
                callback.invoke(origin, false, false)
                return
            }
            activity.runOnUiThread {
                val fineOk = ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarseOk = ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (fineOk || coarseOk) {
                    callback.invoke(origin, true, false)
                    return@runOnUiThread
                }
                val need = buildList {
                    if (!fineOk) add(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (!coarseOk) add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }.toTypedArray()
                activity.pendingGeolocationCallback?.let { prevCb ->
                    activity.pendingGeolocationOrigin?.let { prevOrigin ->
                        prevCb.invoke(prevOrigin, false, false)
                    }
                }
                activity.pendingGeolocationOrigin = origin
                activity.pendingGeolocationCallback = callback
                activity.locationPermissionLauncher.launch(need)
            }
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (!activity.allowVideoFullFromConfig) {
                callback?.onCustomViewHidden()
                return
            }
            if (view != null && callback != null) {
                activity.showCustomView(view, callback)
            } else {
                super.onShowCustomView(view, callback)
            }
        }

        override fun onHideCustomView() {
            activity.hideCustomView()
            super.onHideCustomView()
        }

        // 处理文件选择（Android 5.0+）
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            if (!activity.allowUploadFromConfig && !activity.allowGalleryFromConfig) {
                filePathCallback?.onReceiveValue(null)
                return true
            }
            // 如果之前有未完成的回调，取消它
            if (activity.fileUploadCallback != null) {
                activity.fileUploadCallback?.onReceiveValue(null)
            }
            activity.fileUploadCallback = filePathCallback

            try {
                val acceptTypes = fileChooserParams?.acceptTypes
                    ?.flatMap { it.split(",") }
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() && it != "." }
                    ?.distinct()
                    ?.toTypedArray()
                    ?: emptyArray()

                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

                    if (acceptTypes.isEmpty()) {
                        type = "*/*"
                    } else if (acceptTypes.size == 1) {
                        type = acceptTypes[0]
                    } else {
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
                    }

                    // 支持多选
                    if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }

                // 创建选择器，允许用户选择不同的应用来打开文件
                val chooserIntent = Intent.createChooser(intent, "选择文件")
                activity.fileChooserLauncher.launch(chooserIntent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.e("WebChromeClient", "无法打开文件选择器", e)
                activity.fileUploadCallback?.onReceiveValue(null)
                activity.fileUploadCallback = null
                return false
            }
        }
    }
}








