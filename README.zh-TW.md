# Food Journal

[English](README.md) · **繁體中文**

一款安靜的 Android App，幫你記住吃了什麼、在哪裡吃、花了多少錢。拍張照、輸入價格、儲存，記下一餐的目標是不到 15 秒；回顧時，則是一週一週地翻看。

它是日記，不是減重工具。沒有卡路里、沒有目標、沒有連續紀錄，也沒有分數。除非你自己匯出備份或分享回顧圖，資料不會離開你的手機。

> **版本 1.01.01** · 需要 Android 16 以上 · 介面為英文 · 金額以新台幣計

## 功能

### 三個步驟記下一餐

- 點 Add，相機隨即開啟。拍照、輸入價格，再點 Save。
- 一餐只需要照片或名稱，加上價格。名稱、備註、地點和收藏星號，想填的時候再填。
- 輸入時，會跳出你用過的餐點名稱和已儲存的地點。

### 從相簿補記

- 忘了記午餐？從相簿挑出那張照片，這一餐就會採用照片拍攝的日期和時間。
- 在 Android 17 上，如果相片挑選器分享了照片的位置，而且你儲存的地點中剛好只有一個在 150 公尺內，這一餐就會自動帶入該地點。在 Android 16 上，地點需要自己選。App 從不使用手機本身的定位。

### Journal（日誌）

- 每週一列，從週一到週日，最新的一週在最上面，並附上該週總額。
- 每一週是一條照片膠捲：左右滑動瀏覽那一週，往下捲動查看更早的週。
- 沒有照片的餐點，會顯示成寫著名稱的素色方塊。

### Spending（花費）

- 可切換週、月、年，並往回滑動查看先前的期間。
- 顯示總額、與上一期相比的變化，以及每日和每餐的平均花費。
- 長條圖、花費最多的前五個地點、各時段（早上、中午、晚上、深夜）的花費，以及最貴的三餐。
- 只呈現數字，不做評判：沒有預算、目標或警告。

### Search（搜尋）

- 搜尋餐點名稱、備註和地點，並可依日期或收藏篩選。
- 結果會顯示找到幾餐，以及總共花了多少。
- 在 Spending 點一個地點，可查看它在該期間的餐點；在餐點的地點選單點 Meals here，可查看在那裡吃過的所有餐點。

### 地點

- 可以重新命名地點、把一個地點改成另一個地點的名稱來合併兩者，或刪除地點。
- 刪除或合併地點時，和刪除餐點一樣，會出現幾秒鐘的 Undo（復原）列。

### 備份與還原

- 把所有資料匯出成一個 ZIP 檔，存到你選擇的位置：紀錄以 JSON 儲存，另附上你的照片。
- 匯入時會先檢查整個檔案，在取代你的餐點前先詢問你，再一次完成取代。如果匯入失敗，你的餐點會維持原樣。
- 上次備份超過 30 天時，Journal 上方會出現一行低調的提醒。

### 分享回顧

- 在 Spending 點 Share，就能把正在查看的那一週、那個月或那一年做成一張圖：裡面是這段期間每一餐的照片拼貼，加上期間與年份。
- 其他內容由你決定：餐數、地點數、總額，以及與上一期的比較，各有自己的開關，App 也會記住你的選擇。
- 圖片是高解析度的 PNG，尺寸適合 Instagram 限時動態，並透過 Android 的分享選單送出，可以發布、傳給朋友或儲存。
- 沒有照片的餐點，會和 Journal 一樣，顯示成寫著名稱的素色方塊。

### 設計

- 只用黑、白、灰，讓食物照片成為畫面上唯一的色彩。
- 支援淺色、深色主題，也可以跟隨系統設定。

## 隱私

- 不需要帳號，也不用登入，完全離線運作。
- App 沒有網路權限，因此無法把任何資料傳出去。App 裡也沒有任何分析工具或廣告。
- 唯一會要求的權限是相機，在你第一次新增餐點時詢問。相簿照片透過 Android 的相片挑選器取得，不需要儲存空間權限；App 也從不要求你的位置。
- 照片會縮小後儲存，不保留原本的中繼資料（metadata）。
- 這個 App 關閉了 Android 的雲端備份與裝置間資料轉移。你匯出的備份檔，就是手機以外唯一的一份資料。

> [!WARNING]
> **解除安裝前請先備份。** 解除安裝會刪除 App 裡所有的餐點和照片。請先匯出備份，並存放在安全的地方。

## 安裝

1. 在 Android 手機上下載 [food-journal-1.01.01.apk](release/food-journal-1.01.01.apk)。
2. 開啟檔案；當 Android 詢問時，允許從該來源安裝。

需要 Android 16 以上。App 在搭載 Android 17 的 Pixel 8a 上開發與測試，尚未在 Android 16 上測試。之後的新版本只要以相同金鑰簽署，就能直接覆蓋安裝，並保留你的餐點。

## 未包含的功能

這個 App 沒有卡路里、營養、減重目標、社群動態、多種貨幣、地圖，也沒有雲端同步。它刻意保持精簡。

## 開發者資訊

### 建置

需要 Android Studio 2026.2 以上，以及 Android 17（API 37）SDK。

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # JVM 測試
./gradlew :app:lintDebug
```

在命令列建置時，請把 `JAVA_HOME` 設為 Android Studio 內建的 JDK。在 Mac 上，路徑是 `/Applications/Android Studio.app/Contents/jbr/Contents/Home`。

裝置測試會先安裝 App，結束後再解除安裝，這會刪除實體手機上的餐點。因此，除非 `ANDROID_SERIAL` 指定的是模擬器，否則測試會拒絕執行：

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Release 版本會以 R8 縮減。只有在 `~/.gradle/gradle.properties` 裡設定了 `foodJournal.signing.storeFile`、`.storePassword`、`.keyAlias` 和 `.keyPassword` 這四個屬性時，才會簽署；否則產出的 release 版本不會簽署。

> [!CAUTION]
> 以不同金鑰簽署的版本，無法更新已發布的 APK。Android 會要求你先解除安裝，而這會刪除你的餐點。切換之前，請先匯出備份。

### 使用的技術

Kotlin、Jetpack Compose（Material 3）、Navigation Compose、Room、DataStore、CameraX、Coil，以及 kotlinx.serialization。圖表以 Compose Canvas 繪製，大型數字與標題使用 [Fraunces](https://fonts.google.com/specimen/Fraunces) 字型。

### 文件（英文）

- [產品文件](PRODUCT.md)：App 的功能、外觀與建置方式。

## 授權

Food Journal 採用 [Apache License 2.0](LICENSE) 授權。內附的 Fraunces 字型另有授權：[SIL Open Font License 1.1](licenses/Fraunces-OFL.txt)。
