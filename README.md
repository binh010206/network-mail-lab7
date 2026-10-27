# BÀI TẬP LỚN LAB 7: HỆ THỐNG EMAIL CLIENT & SERVER (SMTP / POP3)
### Tích Hợp MongoDB Atlas Cloud, Quản Lý Người Dùng & Gửi Mail Thật Ra Gmail Web (JavaFX UI)

Dự án hoàn thiện 100% theo đúng đề cương môn **Lập trình mạng (VKU)**, đáp ứng các tiêu chuẩn chấm điểm của thầy cô:
1. **Giao thức mạng chuẩn Socket TCP**: Lập trình Socket trao đổi từng lệnh chuẩn RFC:
   - **SMTP (Cổng 2525)**: `EHLO`, `MAIL FROM`, `RCPT TO`, `DATA`, `QUIT`.
   - **POP3 (Cổng 1110)**: `USER`, `PASS`, `STAT`, `LIST`, `RETR`, `DELE`, `QUIT`.
2. **Cơ sở dữ liệu đám mây (MongoDB Atlas Web)**: Gồm 3 bảng (Collections) phân chia rõ ràng:
   - `server_info`: Lưu thông tin máy chủ (`server_ip`, `smtp_port`, `pop3_port`, `total_sent`, `last_active`).
   - `users`: Lưu các tài khoản người dùng đăng ký từ Client (`email`, `password`, `created_at`, `total_sent`, `total_received`).
   - `emails`: Lưu trữ toàn bộ nội dung thư đã gửi/nhận (`sender`, `recipient`, `subject`, `body`, `time`).
3. **Quản lý người dùng cục bộ & đám mây**: Khi người dùng đăng ký trên Client, hệ thống tự động:
   - Tạo thư mục tương ứng trong `users/<email>/`:
     - `account_info.txt`: Thông tin tài khoản, mật khẩu, ngày tạo.
     - `sent/`: Thư mục lưu các thư đã gửi đi.
     - `inbox/`: Thư mục lưu các thư nhận về.
   - Đồng bộ tài khoản lên MongoDB Atlas Cloud.
4. **Gửi thư thật ra Gmail Web**: Khi gửi mail từ Client, Server nhận qua Socket SMTP, lưu vào MongoDB và thư mục máy, sau đó tự động relay chuyển tiếp qua Google SMTP tới Gmail thật. Người nhận mở điện thoại hoặc `mail.google.com` là thấy thư ngay.
5. **Giao diện Client tinh giản (1 luồng duy nhất)**:
   - Màn hình ban đầu: Đăng Ký / Đăng Nhập (có nút mắt Hiện/Ẩn mật khẩu).
   - Sau khi đăng nhập: Chỉ hiển thị 2 Tab duy nhất là **Soạn Thư** và **Hộp Thư Đến**.

---

## 📚 GIẢI THÍCH LÝ THUYẾT: POP3 KHÁC GÌ SMTP VÀ TẠI SAO PHẢI TÁCH RIÊNG?

### 1. SMTP (Simple Mail Transfer Protocol - Cổng chuẩn 25 / Cổng Lab 2525)
- **Bản chất**: Là giao thức **ĐẨY** (Push Protocol).
- **Mục đích**: Chuyên dùng để **GỬI** thư từ máy Client lên Server, hoặc chuyển tiếp giữa các Mail Server với nhau trên Internet.
- **Tập lệnh**: `HELO`/`EHLO`, `MAIL FROM`, `RCPT TO`, `DATA`, `QUIT`.
- **Hạn chế**: SMTP **không** có lệnh nào để kiểm tra danh sách thư hay tải thư về máy đọc.

### 2. POP3 (Post Office Protocol version 3 - Cổng chuẩn 110 / Cổng Lab 1110)
- **Bản chất**: Là giao thức **KÉO** (Pull Protocol).
- **Mục đích**: Chuyên dùng để **NHẬN / TẢI** thư từ Mail Server về máy Client để người dùng xem offline.
- **Tập lệnh**: `USER`, `PASS`, `STAT`, `LIST`, `RETR`, `DELE`, `QUIT`.
- **Hạn chế**: POP3 **không** có cơ chế nào để gửi thư đi.

### 3. Tại sao trong hệ thống Mail Server phải tách riêng 2 cổng/2 luồng?
Vì mỗi giao thức đảm nhận một chiều truyền tin độc lập trong kiến trúc mạng:
- Chiều đi (Gửi thư): Client kết nối tới cổng SMTP (2525) để đẩy dữ liệu thư lên.
- Chiều về (Nhận thư): Client kết nối tới cổng POP3 (1110) để xác thực người dùng và kéo thư về.
Nếu không tách riêng 2 giao thức ở 2 cổng khác nhau, máy chủ không thể phân biệt được Client đang muốn gửi thư hay muốn đọc hộp thư.

---

## 🚀 HƯỚNG DẪN DEMO CHO THẦY CHẤM

### TRƯỜNG HỢP 1: Demo trên 2 máy tính khác nhau qua mạng LAN / Wi-Fi
1. **Trên Máy 1 (Đóng vai trò Server)**:
   - Chạy `mo_port_firewall.bat` (Run as Administrator) một lần để mở cổng tường lửa.
   - Chạy file `run_server.bat`.
   - Bấm nút **"▶ Khởi Động Server"**.
   - Server hiển thị IP LAN (ví dụ: `10.51.150.136`).
2. **Trên Máy 2 (Đóng vai trò Client)**:
   - Chạy file `run_client.bat`.
   - Đăng ký một tài khoản mới hoặc đăng nhập.
   - Bấm nút **"Quét Server LAN"** để Client tự động dò tìm IP của Máy 1.
   - Vào tab **Soạn Thư**:
     - Nhập địa chỉ email người nhận (ví dụ: email thật của thầy/bạn trên Gmail).
     - Nhập tiêu đề và nội dung thư $\rightarrow$ Bấm **"GỬI THƯ"**.
   - **Kết quả hiển thị cho thầy xem**:
     - **Màn hình Server**: Khung nhật ký Socket in tiến trình nhận thư, thống kê tăng lên, tab **Dữ Liệu Thư** hiển thị nội dung thư vừa gửi.
     - **Màn hình Client**: Khung nhật ký in các bước bắt tay Socket `EHLO`, `MAIL FROM`, `RCPT TO`, `DATA`, `250 OK`.
     - **Trên điện thoại / Gmail thật**: Người nhận mở Gmail thấy thư xuất hiện ngay lập tức!
     - **Thư mục dự án**: Mở thư mục `users/<email>/` thấy file `account_info.txt`, thư mục `sent/` và `inbox/` chứa các file text tương ứng.
     - **Trang web MongoDB Atlas**: Xem 3 collection `server_info`, `users`, `emails` đều có dữ liệu được lưu tức thì!

---

### TRƯỜNG HỢP 2: Demo nhanh trên cùng 1 máy tính
- Nhấp đúp vào file `run_demo_both.bat`: Sẽ tự động mở cả 2 cửa sổ giao diện của Máy Chủ Server và Client lên màn hình để kiểm thử trực tiếp!
