package network;

import config.AppConfig;
import database.MongoManager;
import model.EmailMessage;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SmtpServerThread implements Runnable {
    private final Socket clientSocket;
    private final Consumer<String> logger;

    public SmtpServerThread(Socket socket, Consumer<String> logger) {
        this.clientSocket = socket;
        this.logger = logger != null ? logger : System.out::println;
    }

    @Override
    public void run() {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        log("[SMTP Srv] Nhận kết nối Client từ: " + clientIp);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8))) {

            clientSocket.setSoTimeout(30000);

            // Gửi banner chào
            sendLine(out, "220 VKU-LAN-Mail-Server Ready (RFC 2821 / MongoDB Atlas Supported)");

            String sender = "";
            List<String> recipients = new ArrayList<>();
            String subject = "(Không có tiêu đề)";

            String line;
            while ((line = in.readLine()) != null) {
                log("C: " + line);
                String upper = line.toUpperCase().trim();

                if (upper.startsWith("HELO") || upper.startsWith("EHLO")) {
                    sendLine(out, "250-VKU-LAN-Mail-Server greets " + clientIp);
                    sendLine(out, "250-AUTH LOGIN PLAIN");
                    sendLine(out, "250-8BITMIME");
                    sendLine(out, "250 OK");
                } else if (upper.startsWith("AUTH LOGIN")) {
                    sendLine(out, "334 VXNlcm5hbWU6");
                    String u = in.readLine();
                    sendLine(out, "334 UGFzc3dvcmQ6");
                    String p = in.readLine();
                    sendLine(out, "235 2.7.0 Authentication successful");
                } else if (upper.startsWith("MAIL FROM:")) {
                    sender = extractEmail(line);
                    sendLine(out, "250 2.1.0 Sender <" + sender + "> OK");
                } else if (upper.startsWith("RCPT TO:")) {
                    String rcpt = extractEmail(line);
                    recipients.add(rcpt);
                    sendLine(out, "250 2.1.5 Recipient <" + rcpt + "> OK");
                } else if (upper.equals("DATA")) {
                    if (recipients.isEmpty()) {
                        sendLine(out, "503 Error: need RCPT command");
                        continue;
                    }

                    sendLine(out, "354 Start mail input; end with <CR><LF>.<CR><LF>");

                    StringBuilder bodyBuilder = new StringBuilder();
                    boolean inBody = false;

                    String dataLine;
                    while ((dataLine = in.readLine()) != null) {
                        if (dataLine.equals(".")) {
                            break;
                        }

                        if (!inBody) {
                            if (dataLine.isEmpty()) {
                                inBody = true; // Hết phần Header, sang Body
                            } else if (dataLine.toLowerCase().startsWith("subject:")) {
                                subject = dataLine.substring(8).trim();
                            }
                        } else {
                            bodyBuilder.append(dataLine).append("\r\n");
                        }
                    }

                    String bodyContent = bodyBuilder.toString().trim();
                    log("[SMTP Srv] Đã nhận trọn vẹn thư từ [" + sender + "] gửi tới: " + recipients + " | Tiêu đề: " + subject);

                    // 1. Lưu bản ghi thư vào MongoDB Atlas Cloud và thư mục users/
                    for (String rcpt : recipients) {
                        EmailMessage email = new EmailMessage(sender, rcpt, subject, bodyContent);
                        email.setProtocol("LAN_SMTP_TO_ATLAS");
                        MongoManager.getInstance().saveEmail(email, this::log);
                        database.UserManager.saveSentMailLocal(sender, email);
                        database.UserManager.saveInboxMailLocal(rcpt, email);

                        // 2. Relay chuyển tiếp thư thật tới Gmail để người nhận mở web Gmail là có
                        String gmailUser = AppConfig.getGmailSender();
                        String gmailPass = AppConfig.getGmailPassword();

                        if (rcpt != null && rcpt.contains("@")) {
                            log("[Relay Gateway]: Đang chuyển tiếp thư thật ra Internet tới: " + rcpt + "...");
                            new Thread(() -> {
                                boolean ok = SmtpRelaySender.sendRealMail(gmailUser, gmailPass, email, this::log);
                                if (ok) {
                                    log("[Relay Gateway]: Chuyển tiếp thành công tới " + rcpt + "! Người nhận mở ứng dụng Gmail sẽ thấy thư ngay.");
                                } else {
                                    log("[Relay Gateway]: Chuyển tiếp ra Gmail chưa thành công.");
                                }
                            }).start();
                        }
                    }

                    sendLine(out, "250 2.0.0 OK: message stored in MongoDB Atlas and queued for delivery");

                    // Reset trạng thái
                    recipients.clear();
                    sender = "";
                    subject = "(Không có tiêu đề)";

                } else if (upper.equals("RSET")) {
                    recipients.clear();
                    sender = "";
                    sendLine(out, "250 2.0.0 Reset state OK");
                } else if (upper.equals("NOOP")) {
                    sendLine(out, "250 2.0.0 OK");
                } else if (upper.equals("QUIT")) {
                    sendLine(out, "221 2.0.0 VKU-LAN-Mail-Server closing transmission channel");
                    break;
                } else {
                    sendLine(out, "500 5.5.1 Command unrecognized: " + line);
                }
            }

        } catch (Exception e) {
            log("[SMTP Srv] Lỗi giao tiếp Socket với Client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private void sendLine(BufferedWriter writer, String line) throws IOException {
        log("S: " + line);
        writer.write(line + "\r\n");
        writer.flush();
    }

    private void log(String msg) {
        if (logger != null) logger.accept(msg);
    }

    private String extractEmail(String line) {
        int start = line.indexOf('<');
        int end = line.indexOf('>');
        if (start != -1 && end != -1 && end > start) {
            return line.substring(start + 1, end).trim();
        }
        String[] parts = line.split(":", 2);
        return parts.length > 1 ? parts[1].trim() : line.trim();
    }
}
