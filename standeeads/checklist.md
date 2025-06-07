# 📱 App Android: Phát Video Quảng Cáo Theo Lịch

Ứng dụng Android đơn giản để phát video quảng cáo đã tải sẵn từ URL, phát theo Playlist có lịch phát cụ thể.

---

## ✅ Mục tiêu chính

1. Tạo MockupData dựa trên 2 Model ở dưới
1. Lần đầu mở app, kiểm tra và tải video từ danh sách URL cho sẵn.
2. Phát video theo thứ tự nếu khung giờ hiện tại nằm trong playlist.

---

✅ Lấy data từ mockDataProvider
We get the mock playlists and videos from MockDataProvider in VideoDownloadManager.initializeVideoDownload
✅ Compare với data lưu trong Sharepreferences
We retrieve the saved playlists and videos from SharedPreferences to compare with the new data
✅ Check video url ở data mới và data cũ: a. ✅ Nếu url tồn tại ở cả data mới và cũ, và isDownloaded ở data cũ = true thì update lại vào data mới isDownloaded và localPath
We check if the video exists by ID or URL and preserve the download status and path if the file exists
b. ✅ Nếu url có ở data mới và chưa có ở data cũ hoặc có nhưng chưa download thì giữ nguyên ở data mới
We keep new videos as is if they don't exist in the old data or aren't downloaded yet
c. ✅ Nếu url không có ở data mới và có ở data cũ thì remove file đã download ở data cũ
I've just added this functionality to delete files that are no longer needed
We track processed URLs and delete files for videos that aren't in the new data
✅ Set lại Sharepreferences data mới
We save the merged videos back to SharedPreferences
✅ Dựa vào data mới lấy ra list url chưa download và tiến hành download
We get the list of videos that need downloading and start the download process
✅ Sau khi download tất cả hoàn tất, update lại localPath và isDownload = true vào data mới và save lại sharepreference
We update the download status and local path in SharedPreferences after each download completes

✅ Lấy data từ mockDataProvider
✅ Compare với data lưu trong Sharepreferences
✅ Check video url ở data mới và data cũ: a. ✅ Nếu url tồn tại ở cả data mới và cũ, và isDownloaded ở data cũ = true thì update lại vào data mới isDownloaded và localPath
✅ b. ✅ Nếu url có ở data mới và chưa có ở data cũ hoặc có nhưng chưa download thì giữ nguyên ở data mới
✅ c. ✅ Nếu url không có ở data mới và có ở data cũ thì remove file đã download ở data cũ
✅ Set lại Sharepreferences data mới
✅ Dựa vào data mới lấy ra list url chưa download và tiến hành download
✅ Sau khi download tất cả hoàn tất, update lại localPath và isDownload = true vào data mới và save lại sharepreference

