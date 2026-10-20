package network;

import model.EmailMessage;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.function.Consumer;

public class SmtpRelaySender {

    public static boolean sendRealMail(
            String gmailUser,
            String gmailPass,
            EmailMessage email,
            Consumer<String> logger
    ) {
        if (logger == null) logger = System.out::println;

        String host = "smtp.gmail.com";
        int port = 465;

        logger.accept(String.format("--> [SSL Socket]: Bắt đầu mở kết nối bảo mật tới %s:%d ...", host, port));

        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, port)) {
            socket.setSoTimeout(15000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // Đọc dòng chào của máy chủ
            String banner = readMultiline(reader, logger);
            if (!banner.startsWith("220")) {
                logger.accept("<!> Lỗi máy chủ không trả lời 220: " + banner);
                return false;
            }

            // 1. Gửi EHLO
            sendLine(writer, "EHLO localhost", logger);
            String ehloResp = readMultiline(reader, logger);
            if (!ehloResp.startsWith("250")) {
                logger.accept("<!> EHLO bị từ chối: " + ehloResp);
                return false;
            }

            // 2. Xác thực AUTH LOGIN
            sendLine(writer, "AUTH LOGIN", logger);
            String authResp = readMultiline(reader, logger);
            if (!authResp.startsWith("334")) {
                logger.accept("<!> AUTH LOGIN không được hỗ trợ: " + authResp);
                return false;
            }

            // Gửi Username (Base64)
            String userB64 = Base64.getEncoder().encodeToString(gmailUser.trim().getBytes(StandardCharsets.UTF_8));
            sendLine(writer, userB64, logger);
            String userResp = readMultiline(reader, logger);
            if (!userResp.startsWith("334")) {
                logger.accept("<!> Tài khoản bị từ chối: " + userResp);
                return false;
            }

            // Gửi Password (Base64)
            // Loại bỏ dấu cách nếu là App Password 16 chữ số
            String cleanPass = gmailPass.trim().replace(" ", "");
            String passB64 = Base64.getEncoder().encodeToString(cleanPass.getBytes(StandardCharsets.UTF_8));
            sendLine(writer, passB64, logger);
            String passResp = readMultiline(reader, logger);
            if (!passResp.startsWith("235")) {
                logger.accept("<!> ĐĂNG NHẬP THẤT BẠI (Mã 535)!");
                logger.accept("-> Lưu ý: Google yêu cầu dùng MẬT KHẨU ỨNG DỤNG (16 chữ số) từ myaccount.google.com/apppasswords");
                logger.accept("-> Chi tiết lỗi từ Google: " + passResp);
                return false;
            }
            logger.accept("[OK] Đăng nhập SMTP Google thành công!");

            // 3. MAIL FROM
            sendLine(writer, "MAIL FROM:<" + gmailUser.trim() + ">", logger);
            String mailFromResp = readMultiline(reader, logger);
            if (!mailFromResp.startsWith("250")) {
                logger.accept("<!> MAIL FROM bị từ chối: " + mailFromResp);
                return false;
            }

            // 4. RCPT TO
            sendLine(writer, "RCPT TO:<" + email.getRecipient().trim() + ">", logger);
            String rcptResp = readMultiline(reader, logger);
            if (!rcptResp.startsWith("250")) {
                logger.accept("<!> RCPT TO bị từ chối (Địa chỉ người nhận không hợp lệ): " + rcptResp);
                return false;
            }

            // 5. DATA
            sendLine(writer, "DATA", logger);
            String dataResp = readMultiline(reader, logger);
            if (!dataResp.startsWith("354")) {
                logger.accept("<!> DATA bị từ chối: " + dataResp);
                return false;
            }

            // 6. Gửi Header và Body RFC 822 / MIME
            String dateRfc = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME);
            writer.write("From: " + gmailUser + "\r\n");
            writer.write("To: " + email.getRecipient() + "\r\n");
            writer.write("Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(email.getSubject().getBytes(StandardCharsets.UTF_8)) + "?=\r\n");
            writer.write("Date: " + dateRfc + "\r\n");
            writer.write("MIME-Version: 1.0\r\n");
            writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
            writer.write("Content-Transfer-Encoding: 8bit\r\n");
            writer.write("X-Mailer: VKU-Network-MailClient-Lab7/1.0\r\n");
            writer.write("\r\n"); // Dòng trống bắt buộc phân cách Header và Body
            writer.write(email.getBody() + "\r\n");
            writer.write(".\r\n"); // Kết thúc truyền thư
            writer.flush();

            logger.accept("C: [Đã truyền Header & Body, kết thúc bằng dấu chấm .]");

            String sendResp = readMultiline(reader, logger);
            if (!sendResp.startsWith("250")) {
                logger.accept("<!> Gửi nội dung thư thất bại: " + sendResp);
                return false;
            }
            logger.accept("==> THƯ THẬT ĐÃ ĐƯỢC GỬI THÀNH CÔNG VÀO GMAIL CỦA NGƯỜI NHẬN (" + sendResp.trim() + ")");

            // 7. QUIT
            sendLine(writer, "QUIT", logger);
            readMultiline(reader, logger);

            return true;
        } catch (Exception e) {
            logger.accept("<!> Lỗi ngoại lệ trong quá trình Socket SMTP: " + e.getMessage());
            return false;
        }
    }

    private static void sendLine(BufferedWriter writer, String line, Consumer<String> logger) throws IOException {
        logger.accept("C: " + (line.length() > 60 ? line.substring(0, 60) + "..." : line));
        writer.write(line + "\r\n");
        writer.flush();
    }

    private static String readMultiline(BufferedReader reader, Consumer<String> logger) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
            logger.accept("S: " + line);
            // Chuẩn RFC: Dòng cuối cùng của phản hồi nhiều dòng có mã 3 chữ số theo sau bởi dấu cách
            if (line.length() >= 4 && Character.isDigit(line.charAt(0)) && line.charAt(3) == ' ') {
                break;
            }
            if (line.length() == 3 && Character.isDigit(line.charAt(0))) {
                break;
            }
        }
        return sb.toString();
    }
}
