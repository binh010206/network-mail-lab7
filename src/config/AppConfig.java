package config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class AppConfig {
    private static final String CONFIG_FILE = "config.properties";
    private static final Properties props = new Properties();

    static {
        load();
    }

    public static synchronized void load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                System.err.println("Không thể đọc config.properties: " + e.getMessage());
            }
        } else {
            // Thiết lập giá trị mặc định theo yêu cầu của bạn
            props.setProperty("server.ip", "127.0.0.1");
            props.setProperty("server.smtp.port", "2525");
            props.setProperty("server.pop3.port", "1110");
            props.setProperty("gmail.sender", "binhcs2006@gmail.com");
            props.setProperty("gmail.app_password", "icty lcrh mybv mwir");
            props.setProperty("mongodb.uri", "mongodb+srv://globalcashflowmonitor:binh010206@cluster0.xjhpeid.mongodb.net/vku_network_mail?retryWrites=true&w=majority");
            props.setProperty("mongodb.database", "vku_network_mail");
            props.setProperty("mongodb.collection", "emails");
            save();
        }
    }

    public static synchronized void save() {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            props.store(writer, "Cấu hình Lab 7 - Mail Server & Client (SMTP/POP3 + MongoDB Atlas + JavaFX)");
        } catch (IOException e) {
            System.err.println("Không thể lưu config.properties: " + e.getMessage());
        }
    }

    public static String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static void set(String key, String value) {
        props.setProperty(key, value);
        save();
    }

    public static String getServerIp() {
        return get("server.ip", "127.0.0.1");
    }

    public static int getSmtpPort() {
        try {
            return Integer.parseInt(get("server.smtp.port", "2525"));
        } catch (NumberFormatException e) {
            return 2525;
        }
    }

    public static int getPop3Port() {
        try {
            return Integer.parseInt(get("server.pop3.port", "1110"));
        } catch (NumberFormatException e) {
            return 1110;
        }
    }

    public static String getGmailSender() {
        return get("gmail.sender", "binhcs2006@gmail.com");
    }

    public static String getGmailPassword() {
        return get("gmail.app_password", "icty lcrh mybv mwir");
    }

    public static String getMongoUri() {
        return get("mongodb.uri", "mongodb+srv://globalcashflowmonitor:binh010206@cluster0.xjhpeid.mongodb.net/vku_network_mail?retryWrites=true&w=majority");
    }

    public static String getMongoDbName() {
        return get("mongodb.database", "vku_network_mail");
    }

    public static String getMongoCollection() {
        return get("mongodb.collection", "emails");
    }
}
