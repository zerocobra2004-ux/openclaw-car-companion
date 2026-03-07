# OpenClaw Car Companion - 開發規格文檔

## 1. 專案概述

- **專案名稱**: OpenClaw Car Companion
- **專案類型**: Android 車機應用程式 (APK)
- **核心功能**: 透過 WebSocket 連線至 OpenClaw Gateway，接收文字訊息並以 TTS 朗讀，定時回傳 GPS 位置
- **目標用戶**: OpenClaw 車機使用者

## 2. 功能需求

### 2.1 核心功能

| 功能 | 描述 | 優先級 |
|------|------|--------|
| WebSocket 連線 | 連線至 OpenClaw Gateway，收發訊息 | 必須 |
| TTS 語音朗讀 | 接收文字訊息並用語音朗讀 | 必須 |
| GPS 位置回傳 | 定時回傳 GPS 座標給 Gateway | 必須 |
| 螢幕擷取 | 擷取螢幕畫面回傳 (可選功能) | 選項 |

### 2.2 使用者介面

- 簡單的狀態顯示頁面
- 顯示連線狀態 (已連線/未連線)
- 顯示目前 GPS 位置
- 顯示最後收到訊息的時間
- 緊急停止按鈕 (停止 TTS 播放)

## 3. 技術架構

### 3.1 技術堆疊

- **語言**: Kotlin 1.9.x
- **最低 SDK**: Android 5.0 (API 21)
- **目標 SDK**: Android 13 (API 33)
- **Build Tool**: Gradle 8.0 / AGP 8.1.0
- **依賴庫**:
  - OkHttp 4.12.0 (WebSocket)
  - Kotlin Coroutines 1.7.3
  - AndroidX Core KTX
  - AndroidX AppCompat
  - Material Components 1.10.0

### 3.2 架構模式

採用 MVVM 架構：
- **Model**: 資料類別 (WebSocket 訊息、GPS 位置等)
- **View**: Activity / Layout
- **ViewModel**: 業務邏輯處理

### 3.3 專案結構

```
car-companion/
├── app/
│   ├── src/main/
│   │   ├── java/com/openclaw/carcompanion/
│   │   │   ├── MainActivity.kt
│   │   │   ├── MainViewModel.kt
│   │   │   ├── WebSocketManager.kt
│   │   │   ├── TtsManager.kt
│   │   │   ├── LocationManager.kt
│   │   │   ├── ScreenCaptureManager.kt
│   │   │   └── models/
│   │   │       └── Message.kt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   └── colors.xml
│   │   │   └── manifest.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── wrapper/
└── build.gradle.kts
```

## 4. 功能規格

### 4.1 WebSocket 連線

- **連線 URL**: `ws://{gateway_host}:{gateway_port}/ws/car`
- **自動重連**: 連線中斷時，自動重試 (間隔 5 秒)
- **心跳機制**: 每 30 秒發送 ping 訊息
- **訊息格式**:
  ```json
  // 發送 GPS 位置
  {"type": "location", "lat": 25.033, "lng": 121.565, "timestamp": 1699999999}
  
  // 接收文字訊息
  {"type": "tts", "text": "要朗讀的文字"}
  
  // 接收螢幕擷取請求
  {"type": "screenshot_request"}
  
  // 發送螢幕擷取
  {"type": "screenshot", "data": "base64_image_data"}
  ```

### 4.2 TTS 語音朗讀

- 使用 Android 內建 TextToSpeech
- 支援中文、英文等多語言
- 朗讀完成後回傳狀態給 Gateway

### 4.3 GPS 位置回傳

- 使用 LocationManager 獲取 GPS
- 回傳頻率: 每 60 秒一次
- 精確度: ACCESS_FINE_LOCATION

### 4.4 螢幕擷取 (可選)

- 使用 MediaProjection API
- 擷取後轉為 Base64 編碼
- 回傳給 Gateway

## 5. 權限需求

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

## 6. Gateway 配置

應用程式需從以下位置讀取 Gateway 配置:
- 系統屬性: `openclaw.gateway.host`
- 系統屬性: `openclaw.gateway.port`
- 預設值: `localhost:8080`

## 7. 驗收標準

- [ ] APK 可成功編譯
- [ ] 可連線至 WebSocket Gateway
- [ ] 收到文字訊息可正確朗讀
- [ ] GPS 位置可定時回傳
- [ ] 螢幕擷取功能正常運作 (如已啟用)
- [ ] 權限請求正確處理
