# FORGE TV – launcher đỏ · đen · vàng đồng cho FPT Play Box H650

Launcher Android TV thuần Java, **không dùng thư viện ngoài** (không AndroidX) nên nhẹ và ít lỗi khi build.
Thiết kế và biểu tượng là **của riêng dự án** (lục giác vàng đồng + lõi đỏ phát sáng); không dùng logo Iron Man hay icon Marvel.

## Tính năng
- Dark mode đỏ/đen/vàng đồng; nền HUD (lưới lục giác, vòng quay, vệt quét); ô app phát sáng khi chọn bằng remote.
- Icon app nằm trong tấm lục giác; app có banner (16:9) thì hiện banner.
- Hiển thị cả app TV và app cài ngoài (sideload). Ghim lên đầu, ẩn app, gỡ app, mở thông tin app (giữ nút OK).
- Tự mở một app khi bật box (ví dụ SmartTube / YouTube).
- Công tắc **Hiệu ứng động** (tắt để nhẹ máy; H650 chỉ có Mali-G31, RAM 2 GB).
- Nút Menu / Settings trên remote mở Cài đặt nhanh.
- **Bảo vệ nút Home** (Trợ năng): khi launcher gốc FPT hiện lên, tự đưa về FORGE TV – dùng khi box khóa ADB.
- Màn **Hướng dẫn** liệt kê launcher khác đang cài (kèm tên gói và lệnh `pm disable-user`), mở thẳng thông tin app,
  màn chọn launcher mặc định, Trợ năng, và Tùy chọn nhà phát triển.

## Giới hạn (nói thẳng)
- **Chưa được biên dịch hay chạy thử trên H650.** Mã đã kiểm tra cú pháp và tài nguyên, nhưng chưa build với Android SDK.
- Không mở khóa gói cước/hợp đồng FPT. Nếu FPT khóa ở mức hệ thống/firmware, đổi launcher không đổi được điều đó.
- Không tắt được launcher FPT nếu không có ADB hay quyền hệ thống; "Bảo vệ Home" chỉ là cách chuyển hướng,
  có độ trễ ngắn và chưa kiểm chứng trên iZiOS (Android có thể hạn chế mở app từ nền).
- Ký bằng khóa debug: chỉ để sideload, không đưa lên Google Play.

## Build ra APK
**Cách 1 – GitHub Actions (không cần cài gì):** đẩy thư mục này lên một repo GitHub riêng → tab Actions →
*Build APK* → Run workflow → tải artifact `ForgeTV-apk` (file `app-release.apk`).

**Cách 2 – Android Studio:** Open thư mục này → Build → Build APK(s). Hoặc: `./gradlew assembleRelease`
(cần JDK 17 và Android SDK 34).

## Cài lên box
1. Chép `app-release.apk` vào USB, cắm vào box, mở bằng trình quản lý file (hoặc dùng app *Send Files to TV* / *Downloader*).
   Cho phép "Cài từ nguồn không xác định" khi được hỏi.
2. Mở **FORGE TV – Thiết lập** (có trong danh sách app) và làm 4 bước.
3. Nếu có sự cố, đổi lại trong *Cài đặt → Ứng dụng → FORGE TV* hoặc gỡ cài đặt app: launcher FPT vẫn còn nguyên.

## Tùy biến
- Màu sắc: `UiKit.java` (BG, RED, GOLD, COPPER, GLOW).
- Số cột lưới: `MainActivity.buildUi()` (biến `cols`).
- Biểu tượng/banner: `res/drawable/ic_launcher.xml`, `banner.xml`, và `EmblemView.java`.
