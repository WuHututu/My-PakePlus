const fs = require('fs-extra')
const path = require('path')
const { execSync } = require('child_process')
const sharp = require('sharp')
const ppconfig = require('./ppconfig.json')

const toBool = (value, defaultValue = false) =>
    typeof value === 'boolean' ? value : defaultValue

const deriveVersionCode = (version) => {
    const parts = String(version || '')
        .split('.')
        .map((part) => Number.parseInt(part, 10))
        .filter((part) => Number.isFinite(part))
    if (!parts.length) return 1
    const [major = 0, minor = 0, patch = 0] = parts
    return Math.max(1, major * 10000 + minor * 100 + patch)
}

async function generateAdaptiveIcons(input, output, options = {}) {
    const densities = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }

    // icon背景颜色,可设置为none透明
    const bgColor = options.backgroundColor || '#FFFFFF'
    // 一般0.75, 前景最大占比（安全区）
    const foregroundScale = Number(options.foregroundScale || 0.68)

    if (!fs.existsSync(output)) {
        fs.mkdirSync(output, { recursive: true })
    }

    for (const [folder, size] of Object.entries(densities)) {
        const dir = path.join(output, folder)
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true })

        const backgroundFile = path.join(dir, 'ic_launcher_background.png')
        const foregroundFile = path.join(dir, 'ic_launcher_foreground.png')

        // linux只能convert， 背景：纯色填充（全覆盖）
        const iconBackground =
            bgColor === 'none' ? { r: 0, g: 0, b: 0, alpha: 0 } : bgColor
        await sharp({
            create: {
                width: size,
                height: size,
                channels: 4,
                background: iconBackground,
            },
        })
            .png()
            .toFile(backgroundFile)

        // 前景大小 = 图标尺寸 × 0.75
        const fgSize = Math.round(size * foregroundScale)

        // 前景：缩放到安全区域，居中，四周自动留边
        const foreground = await sharp(input)
            .resize({
                width: fgSize,
                height: fgSize,
                fit: 'contain',
                background: { r: 0, g: 0, b: 0, alpha: 0 },
            })
            .png()
            .toBuffer()
        await sharp({
            create: {
                width: size,
                height: size,
                channels: 4,
                background: { r: 0, g: 0, b: 0, alpha: 0 },
            },
        })
            .composite([{ input: foreground, gravity: 'center' }])
            .png()
            .toFile(foregroundFile)
    }

    // 生成 Adaptive Icon XML (放到 mipmap-anydpi-v26)
    const anydpiDir = path.join(output, 'mipmap-anydpi-v26')
    if (!fs.existsSync(anydpiDir)) fs.mkdirSync(anydpiDir, { recursive: true })

    const xml = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>`

    fs.writeFileSync(path.join(anydpiDir, 'ic_launcher.xml'), xml, 'utf-8')
    fs.writeFileSync(path.join(anydpiDir, 'ic_launcher_round.xml'), xml, 'utf-8')

    console.log('✅ Adaptive Icons 已生成:', output)
}

const updateAppName = async (androidResDir, appName) => {
    // workerflow build apk name always is app-debug.apk
    try {
        const stringsPath = path.join(androidResDir, 'values', 'strings.xml')

        // Check if strings.xml exists
        const exists = await fs.pathExists(stringsPath)
        if (!exists) {
            console.log('⚠️ strings.xml not found, creating a new one')
            await fs.ensureDir(path.dirname(stringsPath))
            await fs.writeFile(
                stringsPath,
                `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${appName}</string>
</resources>`
            )
            console.log(`✅ Created strings.xml with app_name: ${appName}`)
            return
        }

        // Read and update existing strings.xml
        let content = await fs.readFile(stringsPath, 'utf8')

        // Check if app_name already exists
        if (content.includes('<string name="app_name">')) {
            content = content.replace(
                /<string name="app_name">.*?<\/string>/,
                `<string name="app_name">${appName}</string>`
            )
        } else {
            // Add app_name if it doesn't exist
            content = content.replace(
                /<\/resources>/,
                `    <string name="app_name">${appName}</string>\n</resources>`
            )
        }

        await fs.writeFile(stringsPath, content)
        console.log(`✅ Updated app_name to: ${appName}`)
    } catch (error) {
        console.error('❌ Error updating app name:', error)
    }
}

const updateSafeArea = async (androidResDir, safeArea) => {
    try {
        // Assuming MainActivity.kt is in the standard location
        const mainActivityPath = path.join(
            androidResDir.replace('res', ''),
            'java/com/app/pakeplus/MainActivity.kt'
        )

        // Check if file exists
        const exists = await fs.pathExists(mainActivityPath)
        if (!exists) {
            console.log(
                '⚠️ MainActivity.kt not found at expected location:',
                mainActivityPath
            )
            return
        }
        // Read and update the file
        let content = await fs.readFile(mainActivityPath, 'utf8')

        // update safeArea
        if (safeArea) {
            if (safeArea === 'all') {
                console.log('webview debug to all')
            } else if (safeArea === 'top') {
                content = content.replace(
                    'view.setPadding(systemBar.left, systemBar.top, systemBar.right, systemBar.bottom)',
                    `view.setPadding(0, systemBar.top, 0, 0)`
                )
            } else if (safeArea === 'bottom') {
                content = content.replace(
                    'view.setPadding(systemBar.left, systemBar.top, systemBar.right, systemBar.bottom)',
                    `view.setPadding(0, 0, 0, systemBar.bottom)`
                )
            } else if (safeArea === 'left') {
                content = content.replace(
                    'view.setPadding(systemBar.left, systemBar.top, systemBar.right, systemBar.bottom)',
                    `view.setPadding(systemBar.left, 0, 0, 0)`
                )
            } else if (safeArea === 'right') {
                content = content.replace(
                    'view.setPadding(systemBar.left, systemBar.top, systemBar.right, systemBar.bottom)',
                    `view.setPadding(0, 0, systemBar.right, 0)`
                )
            } else if (safeArea === 'horizontal') {
                content = content.replace(
                    'view.setPadding(systemBar.left, systemBar.top, systemBar.right, systemBar.bottom)',
                    `view.setPadding(systemBar.left, 0, systemBar.right, 0)`
                )
            } else if (safeArea === 'vertical') {
                content = content.replace(
                    'view.setPadding(systemBar.left, systemBar.top, systemBar.right, systemBar.bottom)',
                    `view.setPadding(0, systemBar.top, 0, systemBar.bottom)`
                )
            }
        }
        await fs.writeFile(mainActivityPath, content)
        console.log(`✅ Updated safeArea to: ${safeArea}`)
    } catch (error) {
        console.error('❌ Error updating safeArea:', error)
    }
}

// update build yml
const updateBuildYml = async (tagName, releaseName, releaseBody) => {
    try {
        const buildYmlPath = path.join('.github', 'workflows', 'build.yml')
        const exists = await fs.pathExists(buildYmlPath)
        if (!exists) {
            console.log(
                '⚠️ build.yml not found at expected location:',
                buildYmlPath
            )
            return
        }

        // Read the file
        let content = await fs.readFile(buildYmlPath, 'utf8')

        // Replace all occurrences of PakePlus-v0.0.1
        const tagUpdate = content.replaceAll('PakePlus-v0.0.1', tagName)
        const releaseUpdate = tagUpdate.replaceAll(
            'PakePlus v0.0.1',
            releaseName
        )
        const bodyUpdate = releaseUpdate.replaceAll(
            'PakePlus ReleaseBody',
            releaseBody
        )

        // Write back only if changes were made
        if (bodyUpdate !== content) {
            await fs.writeFile(buildYmlPath, bodyUpdate)
            console.log(`✅ Updated build.yml with new app name: ${tagName}`)
        } else {
            console.log('ℹ️ No changes needed in build.yml')
        }
    } catch (error) {
        console.error('❌ Error updating build.yml:', error)
    }
}

// set github env
const setGithubEnv = (name, version, pubBody) => {
    console.log('setGithubEnv......')
    const envPath = process.env.GITHUB_ENV
    if (!envPath) {
        console.error('GITHUB_ENV is not defined')
        return
    }
    try {
        const entries = {
            NAME: name,
            VERSION: version,
            PUBBODY: pubBody,
        }
        for (const [key, value] of Object.entries(entries)) {
            if (value !== undefined) {
                fs.appendFileSync(envPath, `${key}=${value}\n`)
            }
        }
        console.log('✅ Environment variables written to GITHUB_ENV')
        // 查看env 变量
        console.log(fs.readFileSync(envPath, 'utf-8'))
    } catch (err) {
        console.error('❌ Failed to parse config or write to GITHUB_ENV:', err)
    }
    console.log('setGithubEnv success')
}

// update android applicationId
const upAppIdVersion = async (id, version, versionCode) => {
    const gradlePath = path.join(__dirname, '../app/build.gradle.kts')
    const exists = await fs.pathExists(gradlePath)
    if (!exists) {
        console.log('⚠️ build.gradle.kts not found, creating a new one')
        return
    }

    // Read and update the file
    let content = await fs.readFile(gradlePath, 'utf8')

    // Replace the applicationId
    content = content.replace(
        /applicationId = ".*?"/,
        `applicationId = "${id}"`
    )

    // Replace the versionName
    content = content.replace(
        /versionName = ".*?"/,
        `versionName = "${version}"`
    )

    const nextVersionCode = Number(versionCode) || deriveVersionCode(version)
    content = content.replace(/versionCode = \d+/, `versionCode = ${nextVersionCode}`)

    // Write back only if changes were made
    await fs.writeFile(gradlePath, content)
    console.log(`✅ Updated Id: ${id} and version: ${version}`)
}

const updateLaunchLayout = (enabled) => {
    const singleMainPath = path.join(
        __dirname,
        '../app/src/main/res/layout/single_main.xml'
    )
    if (!fs.existsSync(singleMainPath)) return
    let content = fs.readFileSync(singleMainPath, 'utf8')
    content = content.replace(/\s*android:src="@drawable\/launch"/g, '')
    if (enabled) {
        content = content.replace(
            'android:scaleType="centerCrop"',
            'android:scaleType="centerCrop"\n            android:src="@drawable/launch"'
        )
    }
    fs.writeFileSync(singleMainPath, content)
    console.log(`launch layout updated: ${singleMainPath}`)
}

// clear launch config
const clearLaunch = async () => {
    const launchPath = path.join(
        __dirname,
        '../app/src/main/res/drawable/launch.jpg'
    )
    if (fs.existsSync(launchPath)) {
        fs.removeSync(launchPath)
        console.log(`📦 launch.jpg deleted from Android res dir`)
    }
    // clear single_main.xml
    const singleMainPath = path.join(
        __dirname,
        '../app/src/main/res/layout/single_main.xml'
    )
    if (fs.existsSync(singleMainPath)) {
        let content = fs.readFileSync(singleMainPath, 'utf8')
        content = content.replace('android:src="@drawable/launch"', '')
        fs.writeFileSync(singleMainPath, content)
        console.log(`📦 single_main.xml updated: ${singleMainPath}`)
    }
}

// update pppwd.html
const updatePPPwdHtml = (
    startMethod,
    startPwd,
    pwdTitle,
    pwdBtn,
    pwdPlace,
    pwdTip,
    pwdError,
    pwdStyle,
    pwdTheme,
    webUrl,
    isHtml
) => {
    console.log('updatePPPwdHtml......')
    const indexHtmlPath = path.join(__dirname, './www/pppwd.html')
    const indexHtml = fs.readFileSync(indexHtmlPath, 'utf-8')
    const targetUrl = isHtml ? './index.html' : webUrl
    const newIndexHtml = indexHtml
        .replaceAll('startMethod', startMethod)
        .replaceAll('startPwd', startPwd || '123456')
        .replaceAll('pwdTitle', pwdTitle || '请输入密码')
        .replaceAll('pwdBtn', pwdBtn || '验证')
        .replaceAll('pwdPlace', pwdPlace || '')
        .replaceAll('pwdTip', pwdTip || '')
        .replaceAll('pwdError', pwdError || '密码错误')
        .replaceAll('pwdStyle', pwdStyle || 'flat')
        .replaceAll('pwdTheme', pwdTheme || 'dark')
        .replaceAll('https://pakeplus.com/', targetUrl)
    fs.writeFileSync(indexHtmlPath, newIndexHtml)
    console.log('updatePPPwdHtml success')
}

// copy html to android res dir
const initWebEnv = async (
    isHtml,
    webUrl,
    debug,
    safeArea,
    userAgent,
    launchImage,
    screenOn,
    clearCache,
    callPhone,
    download,
    internet,
    position,
    startMethod,
    runtimeOptions = {}
) => {
    const assetsPath = path.join(__dirname, '../app/src/main/assets')
    const appJsonPath = path.join(assetsPath, 'app.json')
    // load app.json
    const appJson = fs.readFileSync(appJsonPath, 'utf8')
    // appJson object
    const appJsonObj = JSON.parse(appJson)
    appJsonObj.name = runtimeOptions.name || appJsonObj.name
    appJsonObj.version = runtimeOptions.version || appJsonObj.version
    appJsonObj.description = runtimeOptions.description || appJsonObj.description
    appJsonObj.author = runtimeOptions.author || appJsonObj.author
    // userAgent
    if (userAgent) {
        appJsonObj.userAgent = userAgent
    } else {
        appJsonObj.userAgent = ''
    }
    // set fullScreen
    if (safeArea === 'fullscreen') {
        appJsonObj.fullScreen = true
    } else {
        appJsonObj.fullScreen = false
    }
    // set screenOn
    if (screenOn) {
        appJsonObj.screenOn = true
    } else {
        appJsonObj.screenOn = false
    }
    // clearCache
    if (clearCache) {
        appJsonObj.clearCache = true
    } else {
        appJsonObj.clearCache = false
    }
    // set callPhone
    if (callPhone) {
        appJsonObj.callPhone = true
    } else {
        appJsonObj.callPhone = false
    }
    // set download
    if (download) {
        appJsonObj.download = true
    } else {
        appJsonObj.download = false
    }
    // set internet
    if (internet) {
        appJsonObj.internet = true
    } else {
        appJsonObj.internet = false
    }
    // set position
    if (position) {
        appJsonObj.position = true
    } else {
        appJsonObj.position = false
    }
    appJsonObj.camera = toBool(runtimeOptions.camera)
    appJsonObj.microphone = toBool(runtimeOptions.microphone)
    appJsonObj.gallery = toBool(runtimeOptions.gallery)
    appJsonObj.upload = toBool(runtimeOptions.upload)
    appJsonObj.videoFull = toBool(runtimeOptions.videoFull, true)
    appJsonObj.backgroundPlay = toBool(runtimeOptions.backgroundPlay)
    appJsonObj.gesture = toBool(runtimeOptions.gesture)
    appJsonObj.browser = toBool(runtimeOptions.browser)
    appJsonObj.wechat = toBool(runtimeOptions.wechat)
    appJsonObj.douyin = toBool(runtimeOptions.douyin)
    appJsonObj.javaScriptEnabled = toBool(runtimeOptions.javaScriptEnabled, true)
    appJsonObj.domStorageEnabled = toBool(runtimeOptions.domStorageEnabled, true)
    appJsonObj.allowFileAccess = toBool(runtimeOptions.allowFileAccess)
    appJsonObj.loadWithOverviewMode = toBool(runtimeOptions.loadWithOverviewMode, true)
    appJsonObj.setZoom = toBool(runtimeOptions.setSupportZoom)
    appJsonObj.injectJs = toBool(runtimeOptions.injectJs, true)
    // set html
    if (startMethod === 'password' || startMethod === 'oncePwd') {
        appJsonObj.webUrl = 'file:///android_asset/pppwd.html'
    } else if (isHtml) {
        // update webUrl
        appJsonObj.webUrl = 'file:///android_asset/index.html'
    } else {
        appJsonObj.webUrl = webUrl
    }
    // is debug
    if (debug) {
        // update debug
        appJsonObj.debug = true
    } else {
        appJsonObj.debug = false
        const vConsolePath = path.join(assetsPath, 'vConsole.js')
        // delete vConsole.js
        fs.removeSync(vConsolePath)
        console.log(`📦 vConsole.js deleted from Android res dir`)
    }
    if (startMethod === 'password' || startMethod === 'oncePwd') {
        // scripts/www/*
        const htmlPath = path.join(__dirname, './www/*')
        // copy to app/src/main/assets
        execSync(`cp -r ${htmlPath} ${assetsPath}`)
        console.log(`📦 HTML copied to Android res dir: ${assetsPath}`)
    } else if (isHtml) {
        // scripts/www/*
        const htmlPath = path.join(__dirname, './www/*')
        // copy to app/src/main/assets
        execSync(`cp -r ${htmlPath} ${assetsPath}`)
        console.log(`📦 HTML copied to Android res dir: ${assetsPath}`)
    } else {
        // delete app/src/main/assets/pppwd.html
        const indexHtmlPath = path.join(assetsPath, 'pppwd.html')
        await fs.remove(indexHtmlPath)
        console.log(`📦 pppwd.html deleted from Android assets`)
    }
    // set launch
    if (launchImage) {
        appJsonObj.launch = launchImage
        // copy launch image to android res dir
        const launchPath = path.join(__dirname, `../launch.jpg`)
        const launchResPath = path.join(
            __dirname,
            '../app/src/main/res/drawable/launch.jpg'
        )
        fs.copySync(launchPath, launchResPath)
        updateLaunchLayout(true)
        console.log(`📦 launch copied to Android res dir: ${launchResPath}`)
    } else {
        appJsonObj.launch = ''
        clearLaunch()
        console.log(`📦 launch deleted from Android res dir`)
    }
    // update app.json
    await fs.writeFile(appJsonPath, JSON.stringify(appJsonObj, null, 2), 'utf8')
    console.log(`✅ app.json updated: ${appJsonPath}`)
}

// create keystore
const createKeystore = async () => {
    const keystore = path.join(__dirname, './pakeplus.txt')
    const keystorePath = path.join(__dirname, '../pakeplus.keystore')
    // copy keystore to keystorePath
    fs.copySync(keystore, keystorePath)
    console.log(`📦 pakeplus.keystore created: ${keystorePath}`)
}

// update manifest.xml
const updateManifest = async (direction = 'default', runtimeOptions = {}) => {
    const manifestPath = path.join(
        __dirname,
        '../app/src/main/AndroidManifest.xml'
    )
    let content = await fs.readFile(manifestPath, 'utf8')
    // update screenOrientation
    if (direction === 'default') {
        content = content.replace(
            /android:screenOrientation=".*?"/,
            `android:screenOrientation="unspecified"`
        )
    } else if (direction === 'horizontal') {
        content = content.replace(
            /android:screenOrientation=".*?"/,
            `android:screenOrientation="sensorLandscape"`
        )
    } else if (direction === 'vertical') {
        content = content.replace(
            /android:screenOrientation=".*?"/,
            `android:screenOrientation="sensorPortrait"`
        )
    } else {
        console.log('⚠️ Invalid direction:', direction)
    }
    const hasPermission = (permissionName) =>
        content.includes(`android:name="${permissionName}"`)
    const ensureBeforeApplication = (xml) => {
        if (!content.includes(xml)) {
            content = content.replace(/\s*<application/, `\n    ${xml}\n    <application`)
        }
    }
    const ensurePermission = (permissionName, attrs = '') => {
        if (!hasPermission(permissionName)) {
            ensureBeforeApplication(`<uses-permission android:name="${permissionName}"${attrs} />`)
        }
    }
    const ensureFeature = (featureName) => {
        if (!content.includes(`android:name="${featureName}"`)) {
            ensureBeforeApplication(`<uses-feature android:name="${featureName}" android:required="false" />`)
        }
    }
    const removePermission = (permissionName) => {
        const escaped = permissionName.replaceAll('.', '\\.')
        content = content.replace(
            new RegExp(`\\s*<uses-permission\\s+android:name="${escaped}"[^>]*/>`, 'g'),
            ''
        )
    }
    const removeFeature = (featureName) => {
        const escaped = featureName.replaceAll('.', '\\.')
        content = content.replace(
            new RegExp(`\\s*<uses-feature\\s+android:name="${escaped}"[^>]*/>`, 'g'),
            ''
        )
    }
    if (toBool(runtimeOptions.position)) {
        ensurePermission('android.permission.ACCESS_FINE_LOCATION')
        ensurePermission('android.permission.ACCESS_COARSE_LOCATION')
    } else {
        removePermission('android.permission.ACCESS_FINE_LOCATION')
        removePermission('android.permission.ACCESS_COARSE_LOCATION')
    }
    if (toBool(runtimeOptions.camera)) {
        ensurePermission('android.permission.CAMERA')
        ensureFeature('android.hardware.camera')
    } else {
        removePermission('android.permission.CAMERA')
        removeFeature('android.hardware.camera')
    }
    if (toBool(runtimeOptions.microphone)) {
        ensurePermission('android.permission.RECORD_AUDIO')
        ensurePermission('android.permission.MODIFY_AUDIO_SETTINGS')
    } else {
        removePermission('android.permission.RECORD_AUDIO')
        removePermission('android.permission.MODIFY_AUDIO_SETTINGS')
    }
    if (toBool(runtimeOptions.backgroundPlay)) {
        ensurePermission('android.permission.POST_NOTIFICATIONS')
    } else {
        removePermission('android.permission.POST_NOTIFICATIONS')
    }
    if (toBool(runtimeOptions.gallery) || toBool(runtimeOptions.upload)) {
        ensurePermission('android.permission.READ_EXTERNAL_STORAGE')
        ensurePermission('android.permission.READ_MEDIA_IMAGES')
        ensurePermission('android.permission.READ_MEDIA_VIDEO')
        ensurePermission('android.permission.READ_MEDIA_AUDIO')
    } else {
        removePermission('android.permission.READ_EXTERNAL_STORAGE')
        removePermission('android.permission.READ_MEDIA_IMAGES')
        removePermission('android.permission.READ_MEDIA_VIDEO')
        removePermission('android.permission.READ_MEDIA_AUDIO')
    }
    removePermission('android.webkit.PermissionRequest')
    removePermission('android.permission.SYSTEM_ALERT_WINDOW')
    await fs.writeFile(manifestPath, content)
    console.log(`✅ manifest.xml updated: ${manifestPath}`)
}

// Main execution
const main = async () => {
    const {
        webview,
        launchImage,
        screenOn,
        direction,
        callPhone,
        download,
        internet,
        position,
        camera,
        microphone,
        gallery,
        upload,
        backgroundPlay,
        videoFull,
        gesture,
        browser,
        wechat,
        douyin,
        startMethod,
        startPwd,
        pwdTitle,
        pwdBtn,
        pwdPlace,
        pwdTip,
        pwdError,
        pwdStyle,
        pwdTheme,
    } = ppconfig.phone

    const {
        name,
        version,
        versionCode,
        id,
        pubBody,
        input,
        output,
        copyTo,
        webUrl,
        showName,
        debug,
        safeArea,
        iconBackgroundColor,
        iconForegroundScale,
        isHtml,
    } = ppconfig.android

    const outPath = path.resolve(output)
    await generateAdaptiveIcons(input, outPath, {
        backgroundColor: iconBackgroundColor,
        foregroundScale: iconForegroundScale,
    })

    const dest = path.resolve(copyTo)
    await fs.copy(outPath, dest, { overwrite: true })
    console.log(`📦 Icons copied to Android res dir: ${dest}`)

    // Update app name if provided
    await updateAppName(dest, showName)

    // Update web URL if provided
    await updateSafeArea(dest, safeArea)

    // update pppwd.html
    updatePPPwdHtml(
        startMethod,
        startPwd,
        pwdTitle,
        pwdBtn,
        pwdPlace,
        pwdTip,
        pwdError,
        pwdStyle,
        pwdTheme,
        webUrl,
        isHtml
    )

    // 删除根目录的res
    await fs.remove(outPath)

    // update android applicationId
    await upAppIdVersion(id, version, versionCode)

    // set github env
    setGithubEnv(name, version, pubBody)

    // create keystore
    await createKeystore()

    // copy html to android res dir
    const userAgent = webview.userAgent
    const clearCache = webview.clearCache
    const runtimeOptions = {
        name,
        version,
        description: ppconfig.android.desc,
        author: ppconfig.phone.author,
        camera,
        microphone,
        gallery,
        upload,
        backgroundPlay,
        videoFull,
        gesture,
        browser,
        wechat,
        douyin,
        position,
        javaScriptEnabled: webview.javaScriptEnabled,
        domStorageEnabled: webview.domStorageEnabled,
        allowFileAccess: webview.allowFileAccess,
        loadWithOverviewMode: webview.loadWithOverviewMode,
        setSupportZoom: webview.setSupportZoom,
        injectJs: ppconfig.injectJq,
    }
    // set app.json
    await initWebEnv(
        isHtml,
        webUrl,
        debug,
        safeArea,
        userAgent,
        launchImage,
        screenOn,
        clearCache,
        callPhone,
        download,
        internet,
        position,
        startMethod,
        runtimeOptions
    )

    // update manifest.xml
    await updateManifest(direction, runtimeOptions)

    // success
    console.log('✅ Worker Success')
}

// run
;(async () => {
    try {
        console.log('🚀 worker start')
        await main()
        console.log('🚀 worker end')
    } catch (error) {
        console.error('❌ Worker Error:', error)
    }
})()
