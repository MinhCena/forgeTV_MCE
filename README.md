# Minh Cena TV – launcher cho FPT Play Box H650

Launcher Android TV viết thuần Java, **không dùng thư viện ngoài**. Giao diện tự vẽ bằng Canvas theo thiết kế
đã duyệt: nền đen, mảng đỏ, chữ vàng kim loại, kính trung tính, Dock kiểu macOS, 3 widget.

## Độ sắc nét trên mọi TV
- Toàn bộ bố cục tính theo khung 1280×720 rồi nhân hệ số `min(rộng/1280, cao/720)`, nên cùng một giao diện
  đúng tỷ lệ từ **TV HD 24 inch (1280×720, 1366×768)** đến **TV 4K 55 inch (3840×2160)**.
- Chữ, viền, biểu tượng hệ thống đều là vector được vẽ lại ở độ phân giải thật, không phóng ảnh.
- Icon ứng dụng lấy ở độ phân giải cao nhất của app (XXXHDPI) và vẽ sẵn đúng bằng kích thước điểm ảnh sẽ hiển thị.
  Icon adaptive bo squircle; icon cũ đặt trong ô kính tối. Icon dạng ảnh bitmap của app chỉ có tối đa 192 px,
  nên trên 4K sẽ mềm hơn một chút (giới hạn của chính app đó, không phải của launcher).
- Font nhúng: Anton, Be Vietnam Pro (SIL OFL 1.1), đủ dấu tiếng Việt.

## Điều khiển
- Phím mũi tên: di chuyển. **OK**: mở. **Giữ OK**: menu ứng dụng (ghim, ẩn, tự mở, thông tin, gỡ).
- **Menu / Settings**: mở Cài đặt. **Back**: đóng menu (launcher không thoát).
- Ô cuối Dock: *Tất cả ứng dụng* và *Cài đặt*.

## Widget (cần Internet)
App xin quyền `INTERNET` và `ACCESS_NETWORK_STATE` chỉ để lấy 3 loại dữ liệu công khai, không gửi dữ liệu cá nhân:
- **Thời tiết**: Open-Meteo (api.open-meteo.com), chọn thành phố trong Cài đặt.
- **Giá vàng SJC**: vnappmob (vapi.vnappmob.com), app tự xin khóa dùng thử 15 ngày và gia hạn.
- **Giá xăng**: VietFuelAPI (vietfuel-api.tranqui.workers.dev).
Hai nguồn sau là dịch vụ cộng đồng/bên thứ ba, **chưa được kiểm chứng trực tiếp** và có thể đổi hoặc ngừng bất cứ lúc nào.
Khi lấy lỗi, widget giữ số liệu lần trước hoặc báo "Chưa có dữ liệu". Địa chỉ nằm ở `WidgetData.java` nếu cần đổi nguồn.

## Giới hạn (nói thẳng)
- **Chưa được biên dịch hay chạy thử trên H650.** Mã đã qua kiểm tra cú pháp và tài nguyên, chưa build với Android SDK.
- Không mở khóa gói cước/hợp đồng FPT. "Bảo vệ nút Home" (Trợ năng) chỉ chuyển hướng về launcher này.
- Android 11 không có làm mờ kính thời gian thực, nên "kính" là gradient + viền sáng. Có công tắc chuyển sang màu phẳng.
- Ký bằng khóa debug: chỉ để sideload, không đưa lên Google Play.

## Build ra APK
GitHub Actions (đã có sẵn `.github/workflows/build.yml` trong bản trước của bạn) hoặc Android Studio:
`./gradlew assembleRelease` (JDK 17, Android SDK 34). File ra: `app/build/outputs/apk/release/app-release.apk`.

## Tùy biến
- Màu: `Ui.java`. Bố cục và kích thước: `HomeView.java` (đơn vị thiết kế 1280×720).
- Tên chào ("Minh"): `Prefs.userName()`. Ảnh poster riêng: Cài đặt → Chọn ảnh poster của bạn (chỉ nằm trên box).
