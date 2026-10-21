# CleanURL for Android ✨

**CleanURL** 是一款極簡、無感、零背景耗電的 Android 網址淨化工具。專為清除 **Facebook、Instagram、Threads、YouTube** 分享連結中的追蹤參數（如 `igsh`、`fbclid`、`si`、`xmt` 等）而設計，保護分享者的隱私並還原乾淨俐落的純淨網址。

---

## 🎯 核心設計理念

從 Android 10 (API 29) 開始，系統嚴格限制背景程式讀取剪貼簿。**CleanURL** 採用最符合現代 Android 規範的 **「系統分享選單 (Share Sheet) + 透明無感處理」** 架構：

- **0 背景常駐、0 耗電**：平時完全不在後台執行，不佔用記憶體與電量，也永遠不會被系統省電機制殺後台。
- **無畫面閃爍體驗**：在社群 App 按下「分享 ➔ CleanURL」時，透過全透明、無動畫的 `ShareReceiverActivity` 在幾毫秒內完成「提取網址 ➔ 淨化規則處理 ➔ 寫入剪貼簿 ➔ 彈出輕量提示」，畫面完全不會跳離原本正在瀏覽的 App。

---

## ✨ 功能特色

### 1. 系統分享選單一鍵淨化 (`ShareReceiverActivity`)
- **純網址提取**：自動過濾分享內容中夾帶的標題或廢話文字，精準提取乾淨網址。
- **全自動寫入剪貼簿**：處理完成後自動覆寫系統剪貼簿，直接貼上即可使用。
- **智慧輕量提示**：
  - **Android 12 及以下**：自動彈出 Toast 提示（`✨ 已複製純淨連結`）。
  - **Android 13 及以上**：配合系統原生左下角剪貼簿預覽面板，並可於設定中自由開關額外的 Toast 提示。

### 2. 四大平台深度淨化規則 (`UrlCleaner`)

| 平台 | 支援網域 | 淨化與轉換邏輯 |
| :--- | :--- | :--- |
| **YouTube** | `youtube.com`<br>`youtu.be`<br>`m.youtube.com` | • 移除 `si`、`feature`、`pp`、`kw` 等追蹤參數<br>• **保留必要參數**：`v` (影片 ID)、`t` / `start` (時間戳記)、`list` / `index` (播放清單)<br>• **Shorts 轉換（可選）**：支援將 `/shorts/{id}` 自動轉為一般播放器網址 `/watch?v={id}` |
| **Instagram** | `instagram.com` | • 移除 `igsh`、`igshid`、`utm_*` 等會暴露分享者個人帳號的追蹤參數<br>• 保留乾淨路徑（如 `/p/xxx/`、`/reel/xxx/`、`/stories/xxx/`） |
| **Threads** | `threads.net`<br>`threads.com` | • 移除 `xmt`、`igshid`、`utm_*` 等追蹤參數<br>• 保留乾淨貼文路徑（如 `/@user/post/xxx`） |
| **Facebook** | `facebook.com`<br>`m.facebook.com`<br>`fb.watch`<br>`fb.com` | • **短網址網路還原**：偵測到 `/share/p/`、`/share/r/`、`/share/v/`、`/share/` 或 `fb.watch/` 等加密分享短網址時，自動在背景發送不帶 Cookie 的輕量 HTTP 請求（含 3 秒 Timeout 與錯誤降級機制），解析跳轉後的真實貼文網址<br>• **參數清洗**：移除 `fbclid`、`mibextid`、`rdid`、`share_url`、`__cft__`、`__tn__`、`ref` 等追蹤碼<br>• **保留必要參數**：保留 `story_fbid`、`id`、`v` 等貼文與影片必要識別碼 |
| **通用網址** | 任意網站 | • 自動清除常見跨站追蹤參數：`utm_source`、`utm_medium`、`utm_campaign`、`utm_term`、`utm_content`、`gclid`、`fbclid` |

### 3. 桌面主程式介面 (`MainActivity`)
當直接從桌面點開 CleanURL 圖示時，提供簡潔的 **Material 3 (Jetpack Compose)** 操作介面：
- **一鍵淨化目前剪貼簿**：若不小心在社群 App 按到「複製連結」而非「分享」，打開 CleanURL 點擊大按鈕即可立即讀取並淨化當前剪貼簿。
- **手動輸入 / 測試區**：提供文字輸入框，可隨時貼上包含網址的文字進行測試與轉換。
- **最近淨化歷史紀錄**：於本機保留最近 20 筆淨化紀錄（顯示平台標籤、淨化後網址、處理時間），支援單筆點擊快速複製與一鍵清空。
- **偏好設定開關**：
  - 啟用 / 停用 **Facebook 短網址網路還原**
  - 啟用 / 停用 **YouTube Shorts 轉一般影片網址 (`/watch?v=`)**
  - 啟用 / 停用 **Android 13+ 額外顯示 Toast 提示**

---

## 🛠️ 技術架構與規格

- **開發語言**：100% 原生 Kotlin
- **UI 框架**：Jetpack Compose (Material 3)
- **非同步處理**：Kotlin Coroutines (`Dispatchers.IO` 處理短網址網路跳轉解析)
- **本機資料儲存**：SharedPreferences / Jetpack DataStore（儲存偏好設定與最近 20 筆歷史紀錄）
- **最低支援版本**：Android 8.0 (API 26)
- **目標支援版本**：Android 15 (API 35)

### 專案預計目錄結構

```text
app/src/main/
├── AndroidManifest.xml
└── java/com/cleanurl/app/
    ├── MainActivity.kt              # 桌面主畫面（一鍵淨化、測試區、歷史紀錄、設定開關）
    ├── ShareReceiverActivity.kt     # 透明無感分享接收器（處理 ACTION_SEND 並寫入剪貼簿）
    ├── core/
    │   ├── UrlCleaner.kt            # 核心網址提取與四大平台參數清洗引擎
    │   ├── UrlUnshortener.kt        # Facebook 短網址 HTTP Redirect 解析器
    │   ├── LinkProcessor.kt         # 提取 ➔ 短網址還原 ➔ 淨化 的共用流程
    │   └── ClipboardHelper.kt       # 剪貼簿讀寫與 Android 12/13+ 提示封裝
    ├── data/
    │   └── AppPreferences.kt        # 設定開關與最近 20 筆歷史紀錄管理
    └── ui/
        └── theme/                   # Material 3 主題配色與樣式
```

---

## 🔒 權限說明

本應用程式遵循最小權限原則，僅申請以下單一權限：
- `android.permission.INTERNET`：**僅用於**將 Facebook 加密短網址（如 `facebook.com/share/...`、`fb.watch/...`）展開還原為真實貼文網址。所有請求皆不攜帶 Cookie，且絕不收集或上傳任何使用者資料。

---

## 🚀 使用方式

1. 在 **YouTube / Instagram / Threads / Facebook** 看到想分享的貼文或影片時，點擊 **「分享 (Share)」**。
2. 在系統彈出的分享選單中，點選 **「CleanURL」**（建議長按將 CleanURL **「釘選 (Pin)」** 至分享選單最前方，之後操作就跟按複製一樣快）。
3. 畫面下方彈出輕量提示，此時剪貼簿內已經是洗乾淨的純淨網址，可直接貼給朋友或任何聊天室！
