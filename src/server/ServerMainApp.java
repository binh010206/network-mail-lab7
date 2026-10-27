package server;

import config.AppConfig;
import database.MongoManager;
import database.UserManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import model.EmailMessage;
import network.Pop3ServerThread;
import network.SmtpServerThread;
import org.bson.Document;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class ServerMainApp extends Application {

    private boolean isRunning = false;
    private ServerSocket smtpServerSocket;
    private ServerSocket pop3ServerSocket;

    private Label lblStatusServer;
    private Label lblStatusMongo;
    private Button btnToggleServer;
    private TextArea txtLog;

    private Label lblStatReceived;
    private Label lblStatMongo;
    private Label lblStatRelay;
    private int statReceived = 0;
    private int statMongo = 0;
    private int statRelay = 0;

    private ListView<EmailMessage> listMongoMails;
    private TextArea txtMailDetail;
    private ListView<String> listUsers;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("VKU Mail Server");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));

        root.setTop(createHeaderPanel());

        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(createMonitorTab());
        tabPane.getTabs().add(createMongoViewerTab());
        tabPane.getTabs().add(createUsersTab());
        root.setCenter(tabPane);

        Scene scene = new Scene(root, 1050, 720);

        File cssFile = new File("src/resources/style.css");
        if (cssFile.exists()) {
            scene.getStylesheets().add(cssFile.toURI().toString());
        }

        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> stopServer());
        primaryStage.show();

        checkMongoConnectionAsync();
    }

    private VBox createHeaderPanel() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(0, 0, 14, 0));

        HBox topRow = new HBox(15);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("MÁY CHỦ THƯ ĐIỆN TỬ");
        title.getStyleClass().add("header-title");
        HBox.setHgrow(title, Priority.ALWAYS);

        lblStatusServer = new Label("SERVER: ĐÃ DỪNG");
        lblStatusServer.getStyleClass().add("badge-offline");

        lblStatusMongo = new Label("DỮ LIỆU: ĐANG KIỂM TRA");
        lblStatusMongo.getStyleClass().add("badge-offline");

        btnToggleServer = new Button("▶ Khởi Động Server");
        btnToggleServer.getStyleClass().add("btn-success");
        btnToggleServer.setOnAction(e -> toggleServer());

        topRow.getChildren().addAll(title, lblStatusServer, lblStatusMongo, btnToggleServer);

        String lanIp = getLocalLanIp();
        HBox ipBanner = new HBox(15);
        ipBanner.setAlignment(Pos.CENTER_LEFT);
        ipBanner.setStyle("-fx-background-color: #e0f2fe; -fx-padding: 10px 14px; -fx-background-radius: 8px; -fx-border-color: #bae6fd; -fx-border-radius: 8px;");

        Label lblIpInfo = new Label(String.format("IP Server: %s  |  Cổng SMTP: %d  |  Cổng POP3: %d",
                lanIp, AppConfig.getSmtpPort(), AppConfig.getPop3Port()));
        lblIpInfo.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0369a1;");

        ipBanner.getChildren().add(lblIpInfo);

        box.getChildren().addAll(topRow, ipBanner);
        return box;
    }

    private Tab createMonitorTab() {
        Tab tab = new Tab("🖥️ Giám Sát");
        tab.setClosable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(14, 0, 0, 0));

        HBox statsBox = new HBox(12);
        statsBox.getChildren().addAll(
                createStatCard("Thư Đã Nhận", lblStatReceived = new Label("0"), "#3b82f6"),
                createStatCard("Đã Lưu Cơ Sở Dữ Liệu", lblStatMongo = new Label("0"), "#10b981"),
                createStatCard("Đã Chuyển Tiếp Thư Thật", lblStatRelay = new Label("0"), "#8b5cf6")
        );

        VBox terminalBox = new VBox(8);
        terminalBox.getStyleClass().add("card");
        VBox.setVgrow(terminalBox, Priority.ALWAYS);

        HBox termHeader = new HBox(10);
        termHeader.setAlignment(Pos.CENTER_LEFT);
        Label termTitle = new Label("Nhật Ký Giao Tiếp Socket");
        termTitle.getStyleClass().add("section-title");
        HBox.setHgrow(termTitle, Priority.ALWAYS);

        Button btnClearLog = new Button("Xóa Nhật Ký");
        btnClearLog.getStyleClass().add("btn-secondary");
        btnClearLog.setOnAction(e -> txtLog.clear());

        termHeader.getChildren().addAll(termTitle, btnClearLog);

        txtLog = new TextArea();
        txtLog.setEditable(false);
        txtLog.getStyleClass().add("terminal-area");
        VBox.setVgrow(txtLog, Priority.ALWAYS);

        terminalBox.getChildren().addAll(termHeader, txtLog);

        content.getChildren().addAll(statsBox, terminalBox);
        tab.setContent(content);
        return tab;
    }

    private VBox createStatCard(String title, Label valLabel, String colorHex) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lblT = new Label(title);
        lblT.getStyleClass().add("sub-title");

        valLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + colorHex + ";");

        card.getChildren().addAll(lblT, valLabel);
        return card;
    }

    private Tab createMongoViewerTab() {
        Tab tab = new Tab("✉️ Dữ Liệu Thư");
        tab.setClosable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(14, 0, 0, 0));

        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Button btnReload = new Button("Làm Mới Dữ Liệu");
        btnReload.getStyleClass().add("btn-primary");
        btnReload.setOnAction(e -> reloadMongoData());

        bar.getChildren().add(btnReload);

        SplitPane split = new SplitPane();
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox leftBox = new VBox(8);
        leftBox.setPadding(new Insets(10));
        Label lblList = new Label("Danh Sách Thư Trong Hệ Thống");
        lblList.getStyleClass().add("section-title");

        listMongoMails = new ListView<>();
        VBox.setVgrow(listMongoMails, Priority.ALWAYS);
        listMongoMails.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                showMailDetail(newV);
            }
        });
        leftBox.getChildren().addAll(lblList, listMongoMails);

        VBox rightBox = new VBox(8);
        rightBox.setPadding(new Insets(10));
        Label lblDetail = new Label("Chi Tiết Thư");
        lblDetail.getStyleClass().add("section-title");

        txtMailDetail = new TextArea();
        txtMailDetail.setEditable(false);
        txtMailDetail.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 13px;");
        VBox.setVgrow(txtMailDetail, Priority.ALWAYS);
        rightBox.getChildren().addAll(lblDetail, txtMailDetail);

        split.getItems().addAll(leftBox, rightBox);
        split.setDividerPositions(0.42);

        content.getChildren().addAll(bar, split);
        tab.setContent(content);
        return tab;
    }

    private void showMailDetail(EmailMessage m) {
        String detail = String.format("Thời gian: %s\n"
                + "Người gửi: %s\n"
                + "Người nhận: %s\n"
                + "Tiêu đề: %s\n\n"
                + "Nội dung:\n%s",
                m.getSentAt(), m.getSender(), m.getRecipient(),
                m.getSubject(), m.getBody());
        txtMailDetail.setText(detail);
    }

    private Tab createUsersTab() {
        Tab tab = new Tab("👥 Tài Khoản");
        tab.setClosable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(14, 0, 0, 0));

        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Button btnReloadUsers = new Button("Làm Mới Danh Sách Tài Khoản");
        btnReloadUsers.getStyleClass().add("btn-primary");
        btnReloadUsers.setOnAction(e -> reloadUsersList());

        bar.getChildren().add(btnReloadUsers);

        listUsers = new ListView<>();
        VBox.setVgrow(listUsers, Priority.ALWAYS);

        content.getChildren().addAll(bar, listUsers);
        tab.setContent(content);
        return tab;
    }

    private void reloadUsersList() {
        listUsers.getItems().clear();
        new Thread(() -> {
            UserManager.syncAllUsersFromMongo();
            List<Document> mongoUsers = MongoManager.getInstance().getAllUsers();
            Platform.runLater(() -> {
                for (Document doc : mongoUsers) {
                    String email = doc.getString("email");
                    String pass = doc.getString("password");
                    String createdAt = doc.getString("created_at");
                    Integer sent = doc.getInteger("total_sent", 0);
                    Integer recv = doc.getInteger("total_received", 0);

                    String item = String.format("Tài khoản: %s  |  Mật khẩu: %s  |  Ngày tạo: %s  |  Đã gửi: %d  |  Đã nhận: %d",
                            email, pass, createdAt, sent, recv);
                    listUsers.getItems().add(item);
                }

                File usersDir = UserManager.USERS_DIR;
                if (usersDir.exists()) {
                    File[] dirs = usersDir.listFiles(File::isDirectory);
                    if (dirs != null) {
                        for (File d : dirs) {
                            String email = d.getName();
                            boolean exists = false;
                            for (String it : listUsers.getItems()) {
                                if (it.contains("Tài khoản: " + email)) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                listUsers.getItems().add(String.format("Tài khoản: %s (Lưu cục bộ)", email));
                            }
                        }
                    }
                }

                if (listUsers.getItems().isEmpty()) {
                    listUsers.getItems().add("Chưa có tài khoản nào được tạo trên hệ thống.");
                }
            });
        }).start();
    }

    private void toggleServer() {
        if (!isRunning) {
            startServer();
        } else {
            stopServer();
        }
    }

    private void startServer() {
        int smtpPort = AppConfig.getSmtpPort();
        int pop3Port = AppConfig.getPop3Port();
        String lanIp = getLocalLanIp();

        new Thread(() -> {
            try {
                smtpServerSocket = new ServerSocket(smtpPort, 50, InetAddress.getByName("0.0.0.0"));
                pop3ServerSocket = new ServerSocket(pop3Port, 50, InetAddress.getByName("0.0.0.0"));

                isRunning = true;
                Platform.runLater(() -> {
                    lblStatusServer.setText("SERVER: ĐANG CHẠY");
                    lblStatusServer.getStyleClass().clear();
                    lblStatusServer.getStyleClass().add("badge-online");

                    btnToggleServer.setText("⏹ Dừng Server");
                    btnToggleServer.getStyleClass().clear();
                    btnToggleServer.getStyleClass().add("btn-danger");
                });

                // Cập nhật thông tin server lên MongoDB Atlas và đồng bộ người dùng
                MongoManager.getInstance().updateServerInfo(lanIp, smtpPort, pop3Port, statReceived);
                UserManager.syncAllUsersFromMongo();

                log("=== MÁY CHỦ KHỞI ĐỘNG THÀNH CÔNG ===");
                log("IP Máy chủ: " + lanIp);
                log("Lắng nghe SMTP cổng: " + smtpPort);
                log("Lắng nghe POP3 cổng: " + pop3Port);

                new Thread(() -> {
                    while (isRunning && !smtpServerSocket.isClosed()) {
                        try {
                            Socket client = smtpServerSocket.accept();
                            Platform.runLater(() -> {
                                statReceived++;
                                statMongo++;
                                statRelay++;
                                lblStatReceived.setText(String.valueOf(statReceived));
                                lblStatMongo.setText(String.valueOf(statMongo));
                                lblStatRelay.setText(String.valueOf(statRelay));
                            });
                            new Thread(new SmtpServerThread(client, this::log)).start();
                        } catch (IOException ignored) {}
                    }
                }).start();

                new Thread(() -> {
                    while (isRunning && !pop3ServerSocket.isClosed()) {
                        try {
                            Socket client = pop3ServerSocket.accept();
                            new Thread(new Pop3ServerThread(client, this::log)).start();
                        } catch (IOException ignored) {}
                    }
                }).start();

            } catch (Exception e) {
                log("<!> Không thể khởi động Server: " + e.getMessage());
                stopServer();
            }
        }).start();
    }

    private void stopServer() {
        isRunning = false;
        try {
            if (smtpServerSocket != null && !smtpServerSocket.isClosed()) smtpServerSocket.close();
            if (pop3ServerSocket != null && !pop3ServerSocket.isClosed()) pop3ServerSocket.close();
        } catch (IOException ignored) {}

        Platform.runLater(() -> {
            lblStatusServer.setText("SERVER: ĐÃ DỪNG");
            lblStatusServer.getStyleClass().clear();
            lblStatusServer.getStyleClass().add("badge-offline");

            btnToggleServer.setText("▶ Khởi Động Server");
            btnToggleServer.getStyleClass().clear();
            btnToggleServer.getStyleClass().add("btn-success");
        });

        log("=== MÁY CHỦ ĐÃ DỪNG HOẠT ĐỘNG ===");
    }

    private void checkMongoConnectionAsync() {
        new Thread(() -> {
            boolean ok = MongoManager.getInstance().isConnected();
            Platform.runLater(() -> updateMongoBadge(ok));
        }).start();
    }

    private void updateMongoBadge(boolean ok) {
        if (ok) {
            lblStatusMongo.setText("DỮ LIỆU: ĐÃ KẾT NỐI");
            lblStatusMongo.getStyleClass().clear();
            lblStatusMongo.getStyleClass().add("badge-online");
        } else {
            lblStatusMongo.setText("DỮ LIỆU: CHƯA KẾT NỐI");
            lblStatusMongo.getStyleClass().clear();
            lblStatusMongo.getStyleClass().add("badge-offline");
        }
    }

    private void reloadMongoData() {
        new Thread(() -> {
            List<EmailMessage> list = MongoManager.getInstance().getEmailsForUser(null);
            Platform.runLater(() -> {
                listMongoMails.getItems().clear();
                listMongoMails.getItems().addAll(list);
                if (list.isEmpty()) {
                    txtMailDetail.setText("Chưa có email nào trong cơ sở dữ liệu");
                }
            });
        }).start();
    }

    private void log(String msg) {
        String timestamp = LocalTime.now().format(TIME_FMT);
        Platform.runLater(() -> {
            if (txtLog != null) {
                txtLog.appendText("[" + timestamp + "] " + msg + "\n");
            }
        });
    }

    private String getLocalLanIp() {
        String wifiIp = null;
        String ethIp = null;
        String fallbackIp = null;

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(interfaces)) {
                if (iface.isLoopback() || !iface.isUp()) continue;

                String name = (iface.getName() + " " + iface.getDisplayName()).toLowerCase();
                if (name.contains("virtual") || name.contains("vmnet") || name.contains("vbox") ||
                    name.contains("wsl") || name.contains("hyper-v") || name.contains("pseudo") ||
                    name.contains("bluetooth") || name.contains("teredo") || name.contains("host-only")) {
                    continue;
                }

                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (name.contains("wi-fi") || name.contains("wlan") || name.contains("wireless")) {
                            wifiIp = ip;
                        } else if (name.contains("ethernet") || name.contains("eth") || name.contains("lan")) {
                            ethIp = ip;
                        } else if (fallbackIp == null) {
                            fallbackIp = ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (wifiIp != null) return wifiIp;
        if (ethIp != null) return ethIp;
        if (fallbackIp != null) return fallbackIp;
        return "127.0.0.1";
    }

    public static void main(String[] args) {
        launch(args);
    }
}
