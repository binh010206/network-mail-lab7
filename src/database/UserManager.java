package database;

import model.EmailMessage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class UserManager {
    public static final File USERS_DIR = new File("users");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    static {
        if (!USERS_DIR.exists()) {
            USERS_DIR.mkdirs();
        }
    }

    public static synchronized boolean registerLocal(String email, String password, Consumer<String> logger) {
        String cleanEmail = email.trim().toLowerCase();
        File userDir = new File(USERS_DIR, cleanEmail);
        File sentDir = new File(userDir, "sent");
        File inboxDir = new File(userDir, "inbox");
        File infoFile = new File(userDir, "account_info.txt");

        if (infoFile.exists()) {
            if (logger != null) logger.accept("[Tài khoản]: Email " + cleanEmail + " đã tồn tại trên hệ thống.");
            return false;
        }

        try {
            if (!userDir.exists()) userDir.mkdirs();
            if (!sentDir.exists()) sentDir.mkdirs();
            if (!inboxDir.exists()) inboxDir.mkdirs();

            String timeStr = LocalDateTime.now().format(TIME_FMT);
            String content = "Tài khoản: " + cleanEmail + "\r\n"
                           + "Mật khẩu: " + password + "\r\n"
                           + "Ngày tạo: " + timeStr + "\r\n";

            Files.write(infoFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            if (logger != null) {
                logger.accept("[Tài khoản]: Đã tạo thư mục users/" + cleanEmail + " và file account_info.txt thành công.");
            }

            // Đồng bộ tài khoản lên MongoDB Atlas Cloud
            MongoManager.getInstance().saveUser(cleanEmail, password, logger);
            return true;
        } catch (IOException e) {
            if (logger != null) logger.accept("[Tài khoản Lỗi]: " + e.getMessage());
            return false;
        }
    }

    public static synchronized boolean loginLocal(String email, String password) {
        String cleanEmail = email.trim().toLowerCase();
        File userDir = new File(USERS_DIR, cleanEmail);
        File infoFile = new File(userDir, "account_info.txt");

        if (infoFile.exists()) {
            try {
                for (String line : Files.readAllLines(infoFile.toPath(), StandardCharsets.UTF_8)) {
                    if (line.startsWith("Mật khẩu:")) {
                        String pass = line.substring("Mật khẩu:".length()).trim();
                        if (pass.equals(password)) {
                            return true;
                        }
                    }
                }
            } catch (IOException ignored) {}
        }

        // Nếu cục bộ chưa có, thử kiểm tra trên MongoDB Atlas
        boolean mongoOk = MongoManager.getInstance().checkUserAuth(cleanEmail, password);
        if (mongoOk) {
            try {
                if (!userDir.exists()) userDir.mkdirs();
                File sentDir = new File(userDir, "sent");
                File inboxDir = new File(userDir, "inbox");
                if (!sentDir.exists()) sentDir.mkdirs();
                if (!inboxDir.exists()) inboxDir.mkdirs();
                if (!infoFile.exists()) {
                    String timeStr = LocalDateTime.now().format(TIME_FMT);
                    String content = "Tài khoản: " + cleanEmail + "\r\n"
                                   + "Mật khẩu: " + password + "\r\n"
                                   + "Ngày tạo: " + timeStr + "\r\n";
                    Files.write(infoFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    public static synchronized void syncAllUsersFromMongo() {
        try {
            java.util.List<org.bson.Document> mongoUsers = MongoManager.getInstance().getAllUsers();
            for (org.bson.Document doc : mongoUsers) {
                String email = doc.getString("email");
                String pass = doc.getString("password");
                String createdAt = doc.getString("created_at");
                if (email == null || email.trim().isEmpty()) continue;

                String cleanEmail = email.trim().toLowerCase();
                File userDir = new File(USERS_DIR, cleanEmail);
                File sentDir = new File(userDir, "sent");
                File inboxDir = new File(userDir, "inbox");
                File infoFile = new File(userDir, "account_info.txt");

                if (!userDir.exists()) userDir.mkdirs();
                if (!sentDir.exists()) sentDir.mkdirs();
                if (!inboxDir.exists()) inboxDir.mkdirs();

                if (!infoFile.exists()) {
                    String content = "Tài khoản: " + cleanEmail + "\r\n"
                                   + "Mật khẩu: " + (pass != null ? pass : "123456") + "\r\n"
                                   + "Ngày tạo: " + (createdAt != null ? createdAt : LocalDateTime.now().format(TIME_FMT)) + "\r\n";
                    Files.write(infoFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void saveSentMailLocal(String senderEmail, EmailMessage email) {
        try {
            String cleanEmail = senderEmail.trim().toLowerCase();
            File userDir = new File(USERS_DIR, cleanEmail);
            if (!userDir.exists()) return; // Chỉ lưu nếu tài khoản này đã được tạo trên hệ thống

            File sentDir = new File(userDir, "sent");
            if (!sentDir.exists()) sentDir.mkdirs();

            String filename = "mail_" + System.currentTimeMillis() + ".txt";
            File mailFile = new File(sentDir, filename);

            String content = "Thời gian: " + email.getSentAt() + "\r\n"
                           + "Người gửi: " + email.getSender() + "\r\n"
                           + "Người nhận: " + email.getRecipient() + "\r\n"
                           + "Tiêu đề: " + email.getSubject() + "\r\n"
                           + "Nội dung:\r\n" + email.getBody() + "\r\n";

            Files.write(mailFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    public static synchronized void saveInboxMailLocal(String recipientEmail, EmailMessage email) {
        try {
            String cleanEmail = recipientEmail.trim().toLowerCase();
            File userDir = new File(USERS_DIR, cleanEmail);
            if (!userDir.exists()) return; // Chỉ lưu nếu tài khoản này đã được tạo trên hệ thống

            File inboxDir = new File(userDir, "inbox");
            if (!inboxDir.exists()) inboxDir.mkdirs();

            String filename = "mail_" + System.currentTimeMillis() + ".txt";
            File mailFile = new File(inboxDir, filename);

            String content = "Thời gian: " + email.getSentAt() + "\r\n"
                           + "Người gửi: " + email.getSender() + "\r\n"
                           + "Người nhận: " + email.getRecipient() + "\r\n"
                           + "Tiêu đề: " + email.getSubject() + "\r\n"
                           + "Nội dung:\r\n" + email.getBody() + "\r\n";

            Files.write(mailFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
}
