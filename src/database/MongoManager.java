package database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import config.AppConfig;
import model.EmailMessage;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private MongoCollection<Document> usersCollection;
    private MongoCollection<Document> serverInfoCollection;

    private boolean connected = false;
    private String lastStatusMessage = "Chưa kết nối";

    private final List<EmailMessage> memoryFallbackList = new CopyOnWriteArrayList<>();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

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
            usersCollection = database.getCollection("users");
            serverInfoCollection = database.getCollection("server_info");

            database.runCommand(new Document("ping", 1));

            connected = true;
            lastStatusMessage = "Kết nối thành công CSDL: " + dbName;
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

    public void updateServerInfo(String ip, int smtpPort, int pop3Port, int totalSent) {
        if (!connected || serverInfoCollection == null) return;
        try {
            Document doc = new Document("_id", "server_config")
                    .append("server_ip", ip)
                    .append("smtp_port", smtpPort)
                    .append("pop3_port", pop3Port)
                    .append("total_sent", totalSent)
                    .append("last_active", LocalDateTime.now().format(TIME_FMT));

            serverInfoCollection.replaceOne(Filters.eq("_id", "server_config"), doc, new ReplaceOptions().upsert(true));
        } catch (Exception ignored) {}
    }

    public void incrementServerSent() {
        if (!connected || serverInfoCollection == null) return;
        try {
            serverInfoCollection.updateOne(Filters.eq("_id", "server_config"), Updates.inc("total_sent", 1));
        } catch (Exception ignored) {}
    }

    public boolean saveUser(String email, String password, Consumer<String> logCallback) {
        if (!connected || usersCollection == null) return false;
        try {
            String cleanEmail = email.toLowerCase().trim();
            Document doc = new Document("_id", cleanEmail)
                    .append("email", cleanEmail)
                    .append("password", password)
                    .append("created_at", LocalDateTime.now().format(TIME_FMT))
                    .append("total_sent", 0)
                    .append("total_received", 0);

            usersCollection.replaceOne(Filters.eq("_id", cleanEmail), doc, new ReplaceOptions().upsert(true));
            if (logCallback != null) logCallback.accept("[MongoDB]: Đã lưu tài khoản " + cleanEmail);
            return true;
        } catch (Exception e) {
            if (logCallback != null) logCallback.accept("[MongoDB Lỗi]: Không thể lưu user: " + e.getMessage());
            return false;
        }
    }

    public void incrementUserSent(String email) {
        if (!connected || usersCollection == null) return;
        try {
            usersCollection.updateOne(Filters.eq("_id", email.toLowerCase().trim()), Updates.inc("total_sent", 1));
        } catch (Exception ignored) {}
    }

    public void incrementUserReceived(String email) {
        if (!connected || usersCollection == null) return;
        try {
            usersCollection.updateOne(Filters.eq("_id", email.toLowerCase().trim()), Updates.inc("total_received", 1));
        } catch (Exception ignored) {}
    }

    public boolean checkUserAuth(String email, String password) {
        if (!connected || usersCollection == null) return false;
        try {
            Document doc = usersCollection.find(Filters.and(
                    Filters.eq("_id", email.toLowerCase().trim()),
                    Filters.eq("password", password)
            )).first();
            return doc != null;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Document> getAllUsers() {
        List<Document> list = new ArrayList<>();
        if (connected && usersCollection != null) {
            try {
                for (Document doc : usersCollection.find().sort(Sorts.descending("created_at"))) {
                    list.add(doc);
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    public boolean saveEmail(EmailMessage msg, Consumer<String> logCallback) {
        memoryFallbackList.add(0, msg);

        if (!connected || collection == null) {
            return false;
        }

        try {
            Document doc = new Document("_id", msg.getId())
                    .append("sender", msg.getSender())
                    .append("recipient", msg.getRecipient())
                    .append("subject", msg.getSubject())
                    .append("body", msg.getBody())
                    .append("time", msg.getSentAt());

            collection.insertOne(doc);

            incrementUserSent(msg.getSender());
            incrementUserReceived(msg.getRecipient());
            incrementServerSent();

            if (logCallback != null) {
                logCallback.accept("[MongoDB]: Đã lưu thư vào database");
            }
            return true;
        } catch (Exception e) {
            if (logCallback != null) {
                logCallback.accept("[MongoDB Lỗi]: " + e.getMessage());
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
                    )).sort(Sorts.descending("time"));
                } else {
                    docs = collection.find().sort(Sorts.descending("time")).limit(50);
                }

                for (Document doc : docs) {
                    EmailMessage msg = new EmailMessage();
                    msg.setId(doc.getString("_id"));
                    msg.setSender(doc.getString("sender"));
                    msg.setRecipient(doc.getString("recipient"));
                    msg.setSubject(doc.getString("subject"));
                    msg.setBody(doc.getString("body"));
                    String t = doc.getString("time");
                    if (t == null) t = doc.getString("sent_at");
                    msg.setSentAt(t);
                    list.add(msg);
                }
                return list;
            } catch (Exception e) {
                System.err.println("Lỗi đọc từ MongoDB: " + e.getMessage());
            }
        }

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
