# BÀI TẬP LỚN LAB 7: HỆ THỐNG EMAIL CLIENT & SERVER (SMTP / POP3)
### Tích Hợp MongoDB Atlas Cloud & Gửi Mail Thật Ra Gmail Web (JavaFX UI)

Dự án hoàn thiện 100% theo đúng đề cương môn **Lập trình mạng (VKU)**, đáp ứng các tiêu chuẩn chấm điểm khắt khe của thầy cô:
1. **Giao thức mạng chuẩn Socket**: Tự lập trình Socket trao đổi từng lệnh chuẩn RFC (SMTP: `EHLO`, `MAIL FROM`, `RCPT TO`, `DATA`, `QUIT` / POP3: `USER`, `PASS`, `STAT`, `LIST`, `RETR`, `DELE`).
2. **Cơ sở dữ liệu đám mây (MongoDB Atlas Web)**: Không dùng file text, toàn bộ email được lưu trữ dưới dạng Document trực tiếp trên cụm MongoDB Atlas Cloud (`globalcashflow`).
3. **Gửi thư thật ra Gmail Web**: Khi gửi mail, thư bay thẳng vào hộp thư Gmail thật, người nhận mở ứng dụng Gmail hoặc `mail.google.com` là thấy thư ngay.
4. **Mô hình 2 máy (1 Server - 1 Client)**: Thiết kế hỗ trợ chạy demo trên 2 máy khác nhau qua mạng LAN / Wi-Fi phòng lab.
5. **Giao diện JavaFX hiện đại**: Thiết kế theo phong cách hiện đại với CSS bo góc, card layout, terminal log hiển thị luồng Socket thời gian thực.

---

## 🚀 HƯỚNG DẪN DEMO CHO THẦY CHẤM

### TRƯỜNG HỢP 1: Demo trên 2 máy tính khác nhau (Mô hình phòng Lab)
1. **Trên Máy 1 (Đóng vai trò Server)**:
   - Chạy file `run_server.bat`.
   - Bấm nút **"▶ Khởi Động Server"**.
   - Server sẽ hiển thị rõ ràng địa chỉ IP LAN (ví dụ: `192.168.1.105`).
2. **Trên Máy 2 (Đóng vai trò Client)**:
   - Copy thư mục `lab7` sang máy 2 (hoặc chỉ cần chạy `run_client.bat`).
   - Trên giao diện Client, nhập địa chỉ IP của Máy 1 vào ô **Địa chỉ IP Server** (hoặc bấm nút **"🔍 Tự Động Quét Server LAN"** để tự tìm).
   - Vào tab **✉️ Soạn & Gửi Thư**:
     - Nhập email người nhận thật (ví dụ: email của thầy hoặc của bạn: `...@gmail.com` / `...@vku.udn.vn`).
     - Bấm **"🚀 GỬI THƯ NGAY"**.
   - **Kết quả demo trực tiếp cho thầy xem**:
     - **Màn hình Server**: Nhận kết nối Socket từ Máy 2, in Protocol Log, thông báo đã lưu vào MongoDB và đã relay ra Gmail.
     - **Màn hình Client**: Khung Terminal bên dưới in từng bước lệnh SMTP `EHLO`, `MAIL FROM`, `RCPT TO`, `DATA`, `250 OK`.
     - **Trình duyệt Web (Gmail)**: Mở `mail.google.com` của người nhận lên $\rightarrow$ Thư thật đã nằm trong hộp thư đến!
     - **Trình duyệt Web (MongoDB Atlas)**: Mở tab **☁️ Dữ Liệu MongoDB Atlas** trên Server hoặc mở trang web Atlas $\rightarrow$ Thấy Document của email vừa gửi được lưu trên đám mây!

---

### TRƯỜNG HỢP 2: Demo kiểm tra nhanh trên cùng 1 máy tính
- Chỉ cần nhấp đúp vào file [`run_demo_both.bat`](file:///D:/L%E1%BA%ADp%20tr%C3%ACnh%20m%E1%BA%A1ng/lab7/run_demo_both.bat): Sẽ tự động mở cả 2 cửa sổ giao diện JavaFX của Máy Chủ Server và Máy Khách Client lên màn hình để kiểm thử trực tiếp!

---

## 🛠 HƯỚNG DẪN CẤU HÌNH GMAIL APP PASSWORD
Google Workspace và Gmail chặn mật khẩu thường trên giao thức SMTP. Để gửi mail thật thành công 100%:
1. Đăng nhập tài khoản Google của bạn tại: [https://myaccount.google.com/](https://myaccount.google.com/)
2. Bật **Xác minh 2 bước** (2-Step Verification) nếu chưa bật.
3. Vào trang tạo mật khẩu ứng dụng: [https://myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)
4. Đặt tên app (ví dụ: `Lab7LTM`) và bấm **Tạo** $\rightarrow$ Google sẽ cấp một chuỗi **16 ký tự** (ví dụ: `abcd efgh ijkl mnop`).
5. Dán 16 ký tự này vào ô **Mật khẩu / App Password** trên giao diện ứng dụng hoặc file `config.properties`.

---

## 📂 DANH MỤC CÁC FILE CHÍNH
- `src/server/ServerMainApp.java`: Ứng dụng Máy Chủ Server JavaFX.
- `src/client/ClientMainApp.java`: Ứng dụng Máy Khách Client JavaFX.
- `src/network/SmtpServerThread.java`: Luồng xử lý giao thức SMTP trên Server.
- `src/network/Pop3ServerThread.java`: Luồng xử lý giao thức POP3 trên Server.
- `src/network/SmtpRelaySender.java`: Module kết nối SSL Socket trực tiếp gửi mail thật ra `smtp.gmail.com:465`.
- `src/network/SmtpClientSocket.java`: Client Socket gửi SMTP sang Server.
- `src/network/Pop3ClientSocket.java`: Client Socket nhận POP3 từ Server.
- `src/database/MongoManager.java`: Quản lý kết nối và thao tác dữ liệu MongoDB Atlas Cloud.
- `src/resources/style.css`: Giao diện JavaFX hiện đại, bo góc, card design, terminal tối màu.
- `run_server.bat`: Khởi động Server.
- `run_client.bat`: Khởi động Client.
- `run_demo_both.bat`: Mở cả Server và Client cùng lúc.
- `compile.bat`: Biên dịch lại khi có chỉnh sửa.
