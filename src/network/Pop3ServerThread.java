package network;

import database.MongoManager;
import model.EmailMessage;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Pop3ServerThread implements Runnable {
    private final Socket clientSocket;
    private final Consumer<String> logger;

    public Pop3ServerThread(Socket socket, Consumer<String> logger) {
        this.clientSocket = socket;
        this.logger = logger != null ? logger : System.out::println;
    }

    @Override
    public void run() {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        log("[POP3 Srv] Nhận kết nối Client từ: " + clientIp);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8))) {

            clientSocket.setSoTimeout(30000);

            sendLine(out, "+OK VKU-LAN-POP3 Server ready (Connected to MongoDB Atlas)");

            String currentUser = "";
            boolean authenticated = false;
            List<EmailMessage> userMails = new ArrayList<>();

            String line;
            while ((line = in.readLine()) != null) {
                log("C: " + line);
                String upper = line.toUpperCase().trim();

                if (upper.startsWith("USER ")) {
                    currentUser = line.substring(5).trim();
                    sendLine(out, "+OK User name accepted");
                } else if (upper.startsWith("PASS ")) {
                    // Chấp nhận đăng nhập cho tài khoản
                    authenticated = true;
                    // Tải thư của user này từ MongoDB Atlas
                    userMails = MongoManager.getInstance().getEmailEmailsForUserSafely(currentUser);
                    log("[POP3 Srv] Đã nạp " + userMails.size() + " thư từ MongoDB Atlas cho hộp thư: " + currentUser);
                    sendLine(out, "+OK Mailbox open, " + userMails.size() + " messages");
                } else if (upper.equals("STAT")) {
                    if (!authenticated) {
                        sendLine(out, "-ERR Please authenticate first");
                        continue;
                    }
                    long totalBytes = 0;
                    for (EmailMessage m : userMails) {
                        totalBytes += m.getBody().getBytes(StandardCharsets.UTF_8).length + 200;
                    }
                    sendLine(out, "+OK " + userMails.size() + " " + totalBytes);
                } else if (upper.equals("LIST")) {
                    if (!authenticated) {
                        sendLine(out, "-ERR Please authenticate first");
                        continue;
                    }
                    sendLine(out, "+OK " + userMails.size() + " messages");
                    for (int i = 0; i < userMails.size(); i++) {
                        EmailMessage m = userMails.get(i);
                        int len = m.getBody().getBytes(StandardCharsets.UTF_8).length + 200;
                        sendLine(out, (i + 1) + " " + len);
                    }
                    sendLine(out, ".");
                } else if (upper.startsWith("RETR ")) {
                    if (!authenticated) {
                        sendLine(out, "-ERR Please authenticate first");
                        continue;
                    }
                    try {
                        int index = Integer.parseInt(line.substring(5).trim()) - 1;
                        if (index >= 0 && index < userMails.size()) {
                            EmailMessage m = userMails.get(index);
                            String rawMail = buildRawMail(m);
                            sendLine(out, "+OK " + rawMail.getBytes(StandardCharsets.UTF_8).length + " octets");
                            out.write(rawMail + "\r\n.\r\n");
                            out.flush();
                            log("S: [Đã gửi nội dung thư #" + (index + 1) + " ID: " + m.getId() + "]");
                        } else {
                            sendLine(out, "-ERR No such message");
                        }
                    } catch (NumberFormatException e) {
                        sendLine(out, "-ERR Invalid message index");
                    }
                } else if (upper.startsWith("DELE ")) {
                    if (!authenticated) {
                        sendLine(out, "-ERR Please authenticate first");
                        continue;
                    }
                    try {
                        int index = Integer.parseInt(line.substring(5).trim()) - 1;
                        if (index >= 0 && index < userMails.size()) {
                            EmailMessage m = userMails.get(index);
                            MongoManager.getInstance().deleteEmail(m.getId());
                            userMails.remove(index);
                            sendLine(out, "+OK Message " + (index + 1) + " deleted from MongoDB Atlas");
                            log("[POP3 Srv] Đã xóa thư ID: " + m.getId() + " trên MongoDB");
                        } else {
                            sendLine(out, "-ERR No such message");
                        }
                    } catch (NumberFormatException e) {
                        sendLine(out, "-ERR Invalid message index");
                    }
                } else if (upper.equals("NOOP")) {
                    sendLine(out, "+OK");
                } else if (upper.equals("QUIT")) {
                    sendLine(out, "+OK VKU-LAN-POP3 Server closing connection");
                    break;
                } else {
                    sendLine(out, "-ERR Command not recognized: " + line);
                }
            }

        } catch (Exception e) {
            log("[POP3 Srv] Lỗi giao tiếp Socket với Client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private String buildRawMail(EmailMessage m) {
        return "Message-ID: <" + m.getId() + "@vku.udn.vn>\r\n"
             + "Date: " + m.getSentAt() + "\r\n"
             + "From: " + m.getSender() + "\r\n"
             + "To: " + m.getRecipient() + "\r\n"
             + "Subject: " + m.getSubject() + "\r\n"
             + "X-Database-Source: MongoDB-Atlas-Cloud\r\n"
             + "\r\n"
             + m.getBody();
    }

    private void sendLine(BufferedWriter writer, String line) throws IOException {
        log("S: " + line);
        writer.write(line + "\r\n");
        writer.flush();
    }

    private void log(String msg) {
        if (logger != null) logger.accept(msg);
    }
}
