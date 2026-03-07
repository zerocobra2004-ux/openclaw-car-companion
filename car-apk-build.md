# OpenClaw Car Companion - 編譯說明

## 環境需求

- **JDK**: OpenJDK 17+
- **Android SDK**: API 33 (Android 13)
- **Gradle**: 8.0+
- **Android Studio**: Electric Eel (2022.1.1) 或更新版本

## 編譯步驟

### 1. 準備環境

```bash
# 確認 JDK 版本
java -version

# 確認 Android SDK
echo $ANDROID_HOME
# 或
echo $ANDROID_SDK_ROOT
```

### 2. 取得源代碼

```bash
cd /app/data/car-companion
```

### 3. 編譯 Debug APK

```bash
./gradlew assembleDebug
```

### 4. 編譯 Release APK

```bash
./gradlew assembleRelease
```

### 5. 清理專案

```bash
./gradlew clean
```

## 輸出檔案

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## 常見問題

### Q1: Gradle 版本過舊

```bash
# 更新 Gradle Wrapper
./gradlew wrapper --gradle-version 8.0
```

### Q2: Android SDK 路徑錯誤

在 `local.properties` 中設定:
```properties
sdk.dir=/path/to/android/sdk
```

### Q3: 依賴庫下載失敗

```bash
# 清除 Gradle 快取
./gradlew clean build --refresh-dependencies
```

### Q4: Kotlin 版本衝突

檢查 `build.gradle.kts` 中的 Kotlin 版本是否與 AGP 相容。

## 部署到裝置

```bash
# 透過 USB 安裝
adb install app/build/outputs/apk/debug/app-debug.apk

# 取得 APK 路徑
realpath app/build/outputs/apk/debug/app-debug.apk
```

## 設定 Gateway 位址

### 方法 1: 系統屬性 (推薦)

```bash
# 透過 adb 設定
adb shell setprop openclaw.gateway.host 192.168.1.100
adb shell setprop openclaw.gateway.port 8080
```

### 方法 2: 應用程式內設定

首次啟動時可在 UI 中輸入 Gateway 位址。

## 權限說明

安裝後需授予以下權限:
- 網際網路 (INTERNET)
- 精確位置 (ACCESS_FINE_LOCATION)
- 粗略位置 (ACCESS_COARSE_LOCATION)
- 錄音 (RECORD_AUDIO) - 用於 TTS

## 服務說明

APK 包含一個前景服務 (Foreground Service):
- 服務名稱: OpenClaw Car Companion Service
- 功能: 維持 WebSocket 連線與 GPS 追蹤
- 通知: 顯示連線狀態
