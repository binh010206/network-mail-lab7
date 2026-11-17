package database;

import model.EmailMessage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class UserManager {
    public static final File USERS_DIR = new File("users");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/lab8_server1";
    private static final String PG_USER = "lab8_server1";
    private static final String PG_PASS = "Lab8_e03980b458a7046436b168e426cee1aa5f2db97cf615811f";

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

            // Đồng bộ tài khoản lên PostgreSQL và MongoDB Atlas Cloud
            saveUserToPostgres(cleanEmail, password, timeStr, logger);
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

        // Nếu cục bộ chưa có, thử kiểm tra trên PostgreSQL
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS);
                 PreparedStatement ps = conn.prepareStatement("SELECT password FROM mail_users WHERE username = ? OR email = ?")) {
                ps.setString(1, cleanEmail);
                ps.setString(2, cleanEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String pass = rs.getString("password");
                        if (pass != null && pass.equals(password)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

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
            if (!userDir.exists()) userDir.mkdirs();

            File infoFile = new File(userDir, "account_info.txt");
            if (!infoFile.exists()) {
                String content = "Tài khoản: " + cleanEmail + "\r\n"
                               + "Mật khẩu: 123456\r\n"
                               + "Ngày tạo: " + email.getSentAt() + "\r\n";
                Files.write(infoFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            }

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
            if (!userDir.exists()) userDir.mkdirs();

            File infoFile = new File(userDir, "account_info.txt");
            if (!infoFile.exists()) {
                String content = "Tài khoản: " + cleanEmail + "\r\n"
                               + "Mật khẩu: 123456\r\n"
                               + "Ngày tạo: " + email.getSentAt() + "\r\n";
                Files.write(infoFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            }

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
            saveEmailToPostgres(email.getSender(), email.getRecipient(), email.getSubject(), email.getBody(), email.getSentAt(), null);
        } catch (Exception ignored) {}
    }

    public static void saveUserToPostgres(String email, String password, String timeStr, Consumer<String> logger) {
        new Thread(() -> {
            try {
                Class.forName("org.postgresql.Driver");
                try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS);
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO mail_users (username, password, email, created_at) VALUES (?, ?, ?, ?) ON CONFLICT (username) DO NOTHING")) {
                    ps.setString(1, email);
                    ps.setString(2, password);
                    ps.setString(3, email);
                    ps.setString(4, timeStr);
                    ps.executeUpdate();
                    if (logger != null) logger.accept("[CSDL PostgreSQL]: Đã lưu tài khoản [" + email + "] vào bảng mail_users.");
                }
            } catch (Throwable ignored) {}
        }).start();
    }

    public static void saveEmailToPostgres(String sender, String receiver, String subject, String body, String timeStr, Consumer<String> logger) {
        new Thread(() -> {
            try {
                Class.forName("org.postgresql.Driver");
                try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS);
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO mail_emails (sender, receiver, subject, body, timestamp, status) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, sender);
                    ps.setString(2, receiver);
                    ps.setString(3, subject);
                    ps.setString(4, body);
                    ps.setString(5, timeStr);
                    ps.setString(6, "DELIVERED");
                    ps.executeUpdate();
                    if (logger != null) logger.accept("[CSDL PostgreSQL]: Đã lưu thư từ [" + sender + "] vào bảng mail_emails.");
                }
            } catch (Throwable ignored) {}
        }).start();
    }
}
