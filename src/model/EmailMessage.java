package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class EmailMessage {
    private String id;
    private String sender;
    private String recipient;
    private String subject;
    private String body;
    private String sentAt;
    private String status;
    private String protocol;

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public EmailMessage() {
        this.id = UUID.randomUUID().toString();
        this.sentAt = LocalDateTime.now().format(FORMATTER);
        this.status = "SUCCESS";
        this.protocol = "SMTP";
    }

    public EmailMessage(String sender, String recipient, String subject, String body) {
        this();
        this.sender = sender;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender != null ? sender : "";
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient != null ? recipient : "";
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject != null ? subject : "(Không có tiêu đề)";
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body != null ? body : "";
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSentAt() {
        return sentAt != null ? sentAt : "";
    }

    public void setSentAt(String sentAt) {
        this.sentAt = sentAt;
    }

    public String getStatus() {
        return status != null ? status : "UNKNOWN";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProtocol() {
        return protocol != null ? protocol : "SMTP";
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public String toString() {
        return String.format("[%s] Từ: %s | Tiêu đề: %s", sentAt, sender, subject);
    }
}
