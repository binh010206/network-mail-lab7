package network;

import model.EmailMessage;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Pop3ClientSocket {

    public static List<EmailMessage> fetchMails(
            String serverIp,
            int serverPort,
            String username,
            String password,
            Consumer<String> logger
    ) {
        if (logger == null) logger = System.out::println;
        List<EmailMessage> mailList = new ArrayList<>();

        logger.accept(String.format("--> [POP3 TCP]: Đang kết nối tới %s:%d ...", serverIp, serverPort));

        try (Socket socket = new Socket(serverIp, serverPort)) {
            socket.setSoTimeout(15000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            String banner = reader.readLine();
            logger.accept("S: " + banner);
            if (banner == null || !banner.startsWith("+OK")) {
                logger.accept("<!> Lỗi kết nối POP3: " + banner);
                return mailList;
            }

            // USER
            sendLine(writer, "USER " + username, logger);
            String userResp = reader.readLine();
            logger.accept("S: " + userResp);

            // PASS
            sendLine(writer, "PASS " + password, logger);
            String passResp = reader.readLine();
            logger.accept("S: " + passResp);
            if (passResp == null || !passResp.startsWith("+OK")) {
                logger.accept("<!> Đăng nhập POP3 thất bại: " + passResp);
                return mailList;
            }

            // STAT
            sendLine(writer, "STAT", logger);
            String statResp = reader.readLine();
            logger.accept("S: " + statResp);

            int msgCount = 0;
            if (statResp != null && statResp.startsWith("+OK")) {
                String[] parts = statResp.split(" ");
                if (parts.length >= 2) {
                    try {
                        msgCount = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {}
                }
            }

            logger.accept("--> Tìm thấy " + msgCount + " thư trong hòm thư.");

            // Lấy từng thư bằng lệnh RETR
            for (int i = 1; i <= msgCount; i++) {
                sendLine(writer, "RETR " + i, logger);
                String retrResp = reader.readLine();
                logger.accept("S: " + retrResp);

                if (retrResp != null && retrResp.startsWith("+OK")) {
                    EmailMessage msg = new EmailMessage();
                    StringBuilder bodyBuilder = new StringBuilder();
                    boolean inBody = false;

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.equals(".")) break;

                        if (!inBody) {
                            if (line.isEmpty()) {
                                inBody = true;
                            } else if (line.toLowerCase().startsWith("subject:")) {
                                msg.setSubject(line.substring(8).trim());
                            } else if (line.toLowerCase().startsWith("from:")) {
                                msg.setSender(line.substring(5).trim());
                            } else if (line.toLowerCase().startsWith("to:")) {
                                msg.setRecipient(line.substring(3).trim());
                            } else if (line.toLowerCase().startsWith("date:")) {
                                msg.setSentAt(line.substring(5).trim());
                            } else if (line.toLowerCase().startsWith("message-id:")) {
                                String mid = line.substring(11).trim();
                                if (mid.startsWith("<") && mid.contains("@")) {
                                    msg.setId(mid.substring(1, mid.indexOf('@')));
                                }
                            }
                        } else {
                            bodyBuilder.append(line).append("\n");
                        }
                    }
                    msg.setBody(bodyBuilder.toString().trim());
                    mailList.add(msg);
                }
            }

            // QUIT
            sendLine(writer, "QUIT", logger);
            String quitResp = reader.readLine();
            logger.accept("S: " + quitResp);

        } catch (Exception e) {
            logger.accept("<!> Lỗi Socket POP3: " + e.getMessage());
        }

        return mailList;
    }

    private static void sendLine(BufferedWriter writer, String line, Consumer<String> logger) throws IOException {
        logger.accept("C: " + line);
        writer.write(line + "\r\n");
        writer.flush();
    }
}
