package database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import config.AppConfig;
import model.EmailMessage;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class MongoManager {
    private static MongoManager instance;

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;
    private boolean connected = false;
    private String lastStatusMessage = "Chưa kết nối";

    // Danh sách lưu trữ dự phòng trong bộ nhớ (đảm bảo app luôn chạy mượt kể cả khi chưa có mạng)
    private final List<EmailMessage> memoryFallbackList = new CopyOnWriteArrayList<>();

    private MongoManager() {
        initFromConfig();
    }

    public static synchronized MongoManager getInstance() {
        if (instance == null) {
            instance = new MongoManager();
        }
        return instance;
    }

    public synchronized void initFromConfig() {
        String uri = AppConfig.getMongoUri();
        String dbName = AppConfig.getMongoDbName();
        String collName = AppConfig.getMongoCollection();
        connect(uri, dbName, collName, null);
    }

    public synchronized boolean connect(String uri, String dbName, String collName, Consumer<String> logCallback) {
        if (uri == null || uri.trim().isEmpty() || !uri.startsWith("mongodb")) {
            connected = false;
            lastStatusMessage = "URI MongoDB không hợp lệ hoặc chưa cấu hình";
            if (logCallback != null) logCallback.accept("[MongoDB]: " + lastStatusMessage);
            return false;
        }

        try {
            if (mongoClient != null) {
                try {
                    mongoClient.close();
                } catch (Exception ignored) {}
            }

            if (logCallback != null) logCallback.accept("[MongoDB]: Đang kết nối tới cụm Atlas: " + dbName + "...");

            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(uri))
                    .applyToSocketSettings(builder ->
                            builder.connectTimeout(8, TimeUnit.SECONDS)
                                   .readTimeout(8, TimeUnit.SECONDS))
                    .applyToClusterSettings(builder ->
                            builder.serverSelectionTimeout(8, TimeUnit.SECONDS))
                    .build();

            mongoClient = MongoClients.create(settings);
            database = mongoClient.getDatabase(dbName);
            collection = database.getCollection(collName);

            // Kiểm tra kết nối nhanh bằng lệnh ping
            database.runCommand(new Document("ping", 1));

            connected = true;
            lastStatusMessage = "Kết nối thành công CSDL: " + dbName + " / " + collName;
            if (logCallback != null) logCallback.accept("[MongoDB]: " + lastStatusMessage);
            return true;
        } catch (Exception e) {
            connected = false;
            lastStatusMessage = "Lỗi kết nối: " + e.getMessage();
            if (logCallback != null) logCallback.accept("[MongoDB]: " + lastStatusMessage);
            return false;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getLastStatusMessage() {
        return lastStatusMessage;
    }

    public boolean saveEmail(EmailMessage msg, Consumer<String> logCallback) {
        // Luôn lưu vào danh sách bộ nhớ trước để an toàn
        memoryFallbackList.add(0, msg);

        if (!connected || collection == null) {
            if (logCallback != null) {
                logCallback.accept("[MongoDB Cache]: Lưu tạm vào bộ nhớ (Chưa kết nối Atlas).");
            }
            return false;
        }

        try {
            Document doc = new Document("_id", msg.getId())
                    .append("sender", msg.getSender())
                    .append("recipient", msg.getRecipient())
                    .append("subject", msg.getSubject())
                    .append("body", msg.getBody())
                    .append("sent_at", msg.getSentAt())
                    .append("status", msg.getStatus())
                    .append("protocol", msg.getProtocol());

            collection.insertOne(doc);
            if (logCallback != null) {
                logCallback.accept("[MongoDB Atlas]: Đã lưu thành công Document ID [" + msg.getId() + "] lên Cloud!");
            }
            return true;
        } catch (Exception e) {
            if (logCallback != null) {
                logCallback.accept("[MongoDB Lỗi]: Không thể ghi lên Atlas: " + e.getMessage());
            }
            return false;
        }
    }

    public List<EmailMessage> getEmailEmailsForUserSafely(String recipientEmail) {
        return getEmailsForUser(recipientEmail);
    }

    public List<EmailMessage> getEmailsForUser(String recipientEmail) {
        List<EmailMessage> list = new ArrayList<>();

        if (connected && collection != null) {
            try {
                FindIterable<Document> docs;
                if (recipientEmail != null && !recipientEmail.trim().isEmpty()) {
                    docs = collection.find(Filters.or(
                            Filters.eq("recipient", recipientEmail.trim()),
                            Filters.regex("recipient", "(?i)" + recipientEmail.trim())
                    )).sort(Sorts.descending("sent_at"));
                } else {
                    docs = collection.find().sort(Sorts.descending("sent_at")).limit(50);
                }

                for (Document doc : docs) {
                    EmailMessage msg = new EmailMessage();
                    msg.setId(doc.getString("_id"));
                    msg.setSender(doc.getString("sender"));
                    msg.setRecipient(doc.getString("recipient"));
                    msg.setSubject(doc.getString("subject"));
                    msg.setBody(doc.getString("body"));
                    msg.setSentAt(doc.getString("sent_at"));
                    msg.setStatus(doc.getString("status"));
                    msg.setProtocol(doc.getString("protocol"));
                    list.add(msg);
                }
                return list;
            } catch (Exception e) {
                System.err.println("Lỗi đọc từ MongoDB: " + e.getMessage());
            }
        }

        // Nếu chưa kết nối hoặc lỗi, lấy từ bộ nhớ
        for (EmailMessage m : memoryFallbackList) {
            if (recipientEmail == null || recipientEmail.trim().isEmpty() ||
                    m.getRecipient().equalsIgnoreCase(recipientEmail.trim())) {
                list.add(m);
            }
        }
        return list;
    }

    public boolean deleteEmail(String emailId) {
        memoryFallbackList.removeIf(m -> m.getId().equals(emailId));
        if (connected && collection != null) {
            try {
                collection.deleteOne(Filters.eq("_id", emailId));
                return true;
            } catch (Exception e) {
                System.err.println("Lỗi xóa từ MongoDB: " + e.getMessage());
            }
        }
        return true;
    }
}
