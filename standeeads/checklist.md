# 📱 App Android: Phát Video Quảng Cáo Theo Lịch

Ứng dụng Android đơn giản để phát video quảng cáo đã tải sẵn từ URL, phát theo Playlist có lịch phát cụ thể.

---

## ✅ Mục tiêu chính

1. Tạo MockupData dựa trên 2 Model ở dưới
1. Lần đầu mở app, kiểm tra và tải video từ danh sách URL cho sẵn.
2. Phát video theo thứ tự nếu khung giờ hiện tại nằm trong playlist.

---

## 📁 1. Quản lý Video

### ✅ Model Video
```json
{
  "id": "video_01",
  "name": "Quảng Cáo A",
  "url": "https://example.com/videoA.mp4",
  "isDownloaded": true
}
```

### ✅ Lưu trữ
- Dùng `SharedPreferences` (dưới dạng chuỗi JSON).
- Dùng thư viện `Gson` để convert JSON ↔️ List<Video>.

---

## 📁 2. Model Playlist

### ✅ Cấu trúc dữ liệu Playlist
```json
{
  "id": "playlist_01",
  "startTime": "08:00",
  "endTime": "10:00",
  "videoIds": ["video_01", "video_02"]
}
```

- Giờ định dạng `HH:mm`
- Lưu danh sách dưới dạng JSON trong SharedPreferences.

---

## 🔁 3. Luồng hoạt động app

### 1. Khi mở app
- Kiểm tra các video trong playlist đã được download chưa.
- Nếu chưa download thì download lưu vào 1 đường dẫn theo `getExternalFilesDir(Environment.DIRECTORY_MOVIES)`.
- Lưu lại dữ liệu model Videos và Playlist vào SharedPreferences
- Đọc video & playlist từ SharedPreferences (hoặc assets nếu lần đầu).
- Với mỗi video chưa được tải (`isDownloaded == false`) thì:
  - Gửi request tải bằng `DownloadManager`.
  - Đặt đích lưu trong `getExternalFilesDir(Environment.DIRECTORY_MOVIES)`.

### 2. Phát video theo playlist sử dụng VideoView và load full màn hình
- Lấy giờ hiện tại bằng `LocalTime.now()` (API 26+).
- So sánh với `startTime` và `endTime` của mỗi playlist:
```kotlin
val now = LocalTime.now()
val start = LocalTime.parse(playlist.startTime)
val end = LocalTime.parse(playlist.endTime)
if (now.isAfter(start) && now.isBefore(end)) {
    // Phát danh sách video
}
```

---

## ▶️ 4. Phát video bằng `VideoView`

```kotlin
fun playVideoList(videoView: VideoView, videoPaths: List<String>, index: Int = 0) {
    if (index >= videoPaths.size) return

    videoView.setVideoPath(videoPaths[index])
    videoView.setOnCompletionListener {
        playVideoList(videoView, videoPaths, index + 1)
    }
    videoView.start()
}
```