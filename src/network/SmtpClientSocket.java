package network;

import model.EmailMessage;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class SmtpClientSocket {

    public static boolean sendMailToServer(
            String serverIp,
            int serverPort,
            EmailMessage email,
            Consumer<String> logger
    ) {
        if (logger == null) logger = System.out::println;

        logger.accept(String.format("--> [Socket TCP]: Đang kết nối tới Mail Server tại %s:%d ...", serverIp, serverPort));

        try (Socket socket = new Socket(serverIp, serverPort)) {
            socket.setSoTimeout(15000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            String banner = readMultiline(reader, logger);
            if (!banner.startsWith("220")) {
                logger.accept("<!> Server không phản hồi 220: " + banner);
                return false;
            }

            // 1. EHLO
            sendLine(writer, "EHLO client.vku.udn.vn", logger);
            String ehloResp = readMultiline(reader, logger);
            if (!ehloResp.startsWith("250")) {
                logger.accept("<!> EHLO bị lỗi: " + ehloResp);
                return false;
            }

            // 2. MAIL FROM
            sendLine(writer, "MAIL FROM:<" + email.getSender() + ">", logger);
            String mailFromResp = readMultiline(reader, logger);
            if (!mailFromResp.startsWith("250")) {
                logger.accept("<!> MAIL FROM bị từ chối: " + mailFromResp);
                return false;
            }

            // 3. RCPT TO
            sendLine(writer, "RCPT TO:<" + email.getRecipient() + ">", logger);
            String rcptResp = readMultiline(reader, logger);
            if (!rcptResp.startsWith("250")) {
                logger.accept("<!> RCPT TO bị từ chối: " + rcptResp);
                return false;
            }

            // 4. DATA
            sendLine(writer, "DATA", logger);
            String dataResp = readMultiline(reader, logger);
            if (!dataResp.startsWith("354")) {
                logger.accept("<!> DATA bị từ chối: " + dataResp);
                return false;
            }

            // 5. Gửi nội dung thư
            writer.write("From: " + email.getSender() + "\r\n");
            writer.write("To: " + email.getRecipient() + "\r\n");
            writer.write("Subject: " + email.getSubject() + "\r\n");
            writer.write("Date: " + email.getSentAt() + "\r\n");
            writer.write("\r\n");
            writer.write(email.getBody() + "\r\n");
            writer.write(".\r\n");
            writer.flush();

            logger.accept("C: [Đã truyền dữ liệu thư và dấu chấm .]");

            String resp = readMultiline(reader, logger);
            if (!resp.startsWith("250")) {
                logger.accept("<!> Server báo lỗi khi nhận thư: " + resp);
                return false;
            }

            logger.accept("==> GỬI THƯ THÀNH CÔNG TỚI SERVER! (" + resp.trim() + ")");

            // 6. QUIT
            sendLine(writer, "QUIT", logger);
            readMultiline(reader, logger);

            return true;
        } catch (Exception e) {
            logger.accept("<!> Lỗi Socket Client: " + e.getMessage());
            return false;
        }
    }

    private static void sendLine(BufferedWriter writer, String line, Consumer<String> logger) throws IOException {
        logger.accept("C: " + line);
        writer.write(line + "\r\n");
        writer.flush();
    }

    private static String readMultiline(BufferedReader reader, Consumer<String> logger) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
            logger.accept("S: " + line);
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
