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
- Phím mũi tên: di chuyển (mũi tên LÊN từ hai nút lớn để vào thanh trên cùng: Trang chủ / Ứng dụng / Cài đặt). **OK**: mở. **Giữ OK**: menu ứng dụng (ghim, ẩn, tự mở, thông tin, gỡ).
- **Menu / Settings**: mở Cài đặt. **Back**: đóng menu (launcher không thoát).
- Ô cuối Dock: *Tất cả ứng dụng* và *Cài đặt*.

## Widget (cần Internet)
App xin quyền `INTERNET` và `ACCESS_NETWORK_STATE` chỉ để lấy 3 loại dữ liệu công khai, không gửi dữ liệu cá nhân:
- **Thời tiết**: Open-Meteo (api.open-meteo.com), chọn thành phố trong Cài đặt.
- **Giá vàng SJC**: vnappmob (vapi.vnappmob.com), app tự xin khóa dùng thử 15 ngày và gia hạn.
- **Giá xăng**: VietFuelAPI (vietfuel-api.tranqui.workers.dev).
Hai nguồn sau là dịch vụ cộng đồng/bên thứ ba, **chưa được kiểm chứng trực tiếp** và có thể đổi hoặc ngừng bất cứ lúc nào.
Khi lấy lỗi, widget giữ số liệu lần trước hoặc báo "Chưa có dữ liệu". Địa chỉ nằm ở `WidgetData.java` nếu cần đổi nguồn.

## Nút Home
Bật **Trợ năng → Minh Cena TV – Nút Home** (menu Cài đặt có mục "Bật nút Home" mở thẳng màn hình này khi chưa bật).
Dịch vụ bắt phím HOME và mở Minh Cena TV thay vì launcher gốc; dự phòng thêm: khi cửa sổ của launcher khác hiện lên,
đưa người dùng về lại đây. Cần "Mở launcher gốc (60 giây)" trong Cài đặt nếu muốn vào launcher FPT.
Nếu phím HOME của remote không đi qua Trợ năng trên một số box, cách dự phòng thứ hai vẫn chạy nhưng có độ trễ ngắn.

## Chẩn đoán widget
Khi widget không có dữ liệu, dòng chữ đỏ nhỏ bên dưới ghi lý do (ví dụ `HTTP 403`, `UnknownHostException`).
Giá xăng thử lần lượt 7 nguồn (VietFuelAPI, vtcnews.vn, webgia.com, giaxanghomnay.com, pvoil.com.vn...), đọc linh hoạt cả JSON lẫn HTML.

## Tự cập nhật (không cần USB)
- Mỗi lần mở app (tối đa 6 giờ một lần) app hỏi GitHub Releases của repo `MinhCena/forgeTV_MCE`. Có bản mới thì hiện thanh nhắc,
  vào **Cài đặt → Cài bản mới** để tải và cài. Lần đầu Android sẽ hỏi bật "Cho phép từ nguồn này" cho Minh Cena TV.
- Android **luôn hỏi xác nhận** khi cài, không thể cài ngầm trên box thông thường.
- Cần repo **công khai (public)**; nếu repo riêng tư, app không đọc được. Đổi repo ở `Updater.REPO`.
- Bản mới chỉ cài đè được nếu ký cùng khóa: **giữ nguyên `app/minhcena.jks`**. Khóa này nằm trong repo nên repo public
  nghĩa là ai cũng ký được bản giả mạo; chỉ dùng cách này cho app cá nhân. Muốn chặt hơn, chuyển khóa vào GitHub Secrets.
- **Lần đầu bắt buộc gỡ bản cũ** (ký bằng khóa khác) rồi cài bản này; từ đó trở đi cập nhật tự động.
- Workflow `.github/workflows/build.yml` mỗi lần có commit sẽ build, đánh số `versionCode` tăng dần và tạo một Release gắn APK.

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
