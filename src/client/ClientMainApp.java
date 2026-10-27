package client;

import config.AppConfig;
import database.MongoManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import model.EmailMessage;
import network.Pop3ClientSocket;
import network.SmtpClientSocket;
import network.SmtpRelaySender;

import java.io.File;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class ClientMainApp extends Application {

    private TextField txtServerHost;
    private TextField txtSmtpPort;
    private TextField txtPop3Port;

    // Các trường gửi thư
    private TextField txtFrom;
    private TextField txtTo;
    private TextField txtSubject;
    private TextArea txtBody;
    private RadioButton radViaServer;
    private RadioButton radDirectGmail;
    private Button btnSend;
    private TextArea txtProtocolLog;

    // Hộp thư đến POP3
    private TextField txtPopUser;
    private PasswordField txtPopPass;
    private ListView<EmailMessage> listInbox;
    private TextArea txtMailView;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("VKU Email Client - SMTP / POP3 & MongoDB Atlas (Lab 7)");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));

        // 1. Top Panel: Kết nối Server
        root.setTop(createTopBar());

        // 2. TabPane
        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(createComposeTab());
        tabPane.getTabs().add(createInboxTab());
        tabPane.getTabs().add(createSettingsTab());
        root.setCenter(tabPane);

        Scene scene = new Scene(root, 1060, 740);

        File cssFile = new File("src/resources/style.css");
        if (cssFile.exists()) {
            scene.getStylesheets().add(cssFile.toURI().toString());
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createTopBar() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(0, 0, 14, 0));

        HBox topTitleRow = new HBox(15);
        topTitleRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("ỨNG DỤNG THƯ ĐIỆN TỬ (EMAIL CLIENT)");
        title.getStyleClass().add("header-title");
        HBox.setHgrow(title, Priority.ALWAYS);

        Button btnScanLan = new Button("🔍 Tự Động Quét Server LAN");
        btnScanLan.getStyleClass().add("btn-secondary");
        btnScanLan.setOnAction(e -> autoScanLanServer());

        topTitleRow.getChildren().addAll(title, btnScanLan);

        // Thanh nhập kết nối Server
        HBox connBar = new HBox(12);
        connBar.setAlignment(Pos.CENTER_LEFT);
        connBar.setStyle("-fx-background-color: #ffffff; -fx-padding: 10px 14px; -fx-background-radius: 8px; -fx-border-color: #cbd5e1; -fx-border-radius: 8px;");

        Label lblHost = new Label("Địa chỉ IP Server:");
        lblHost.setStyle("-fx-font-weight: bold;");
        txtServerHost = new TextField(AppConfig.getServerIp());
        txtServerHost.setPrefWidth(160);

        Label lblSmtp = new Label("Cổng SMTP:");
        txtSmtpPort = new TextField(String.valueOf(AppConfig.getSmtpPort()));
        txtSmtpPort.setPrefWidth(70);

        Label lblPop = new Label("Cổng POP3:");
        txtPop3Port = new TextField(String.valueOf(AppConfig.getPop3Port()));
        txtPop3Port.setPrefWidth(70);

        Label lblHelp = new Label("(Nhập IP máy chủ Server đang chạy trong cùng mạng Wi-Fi/LAN)");
        lblHelp.getStyleClass().add("sub-title");

        connBar.getChildren().addAll(lblHost, txtServerHost, lblSmtp, txtSmtpPort, lblPop, txtPop3Port, lblHelp);

        box.getChildren().addAll(topTitleRow, connBar);
        return box;
    }

    private Tab createComposeTab() {
        Tab tab = new Tab("✉️ Soạn & Gửi Thư (SMTP)");
        tab.setClosable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(14, 0, 0, 0));

        // Form soạn thư bên trên
        VBox formCard = new VBox(10);
        formCard.getStyleClass().add("card");

        Label formTitle = new Label("SOẠN THƯ MỚI (CHUẨN GIAO THỨC SMTP)");
        formTitle.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        grid.add(new Label("Người gửi (From):"), 0, 0);
        txtFrom = new TextField(AppConfig.getGmailSender());
        txtFrom.setPrefWidth(400);
        grid.add(txtFrom, 1, 0);

        grid.add(new Label("Người nhận (To):"), 0, 1);
        txtTo = new TextField("lenhuanphat1711@gmail.com");
        txtTo.setPromptText("Nhập địa chỉ Gmail người nhận (VD: recipient@gmail.com)...");
        grid.add(txtTo, 1, 1);

        grid.add(new Label("Tiêu đề (Subject):"), 0, 2);
        txtSubject = new TextField("Kiểm Thử Bài Tập Lập Trình Mạng Lab 7 - VKU");
        grid.add(txtSubject, 1, 2);

        // Chế độ gửi
        HBox modeBox = new HBox(15);
        modeBox.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup tg = new ToggleGroup();

        radViaServer = new RadioButton("🟢 Gửi qua LAN Mail Server (Lưu MongoDB Atlas + Relay Vào Gmail Thật)");
        radViaServer.setToggleGroup(tg);
        radViaServer.setSelected(true);

        radDirectGmail = new RadioButton("🔵 Gửi trực tiếp tới smtp.gmail.com:465 (SSL Socket)");
        radDirectGmail.setToggleGroup(tg);

        modeBox.getChildren().addAll(radViaServer, radDirectGmail);
        grid.add(new Label("Chế độ gửi:"), 0, 3);
        grid.add(modeBox, 1, 3);

        grid.add(new Label("Nội dung thư:"), 0, 4);
        txtBody = new TextArea("Chào bạn!\nThư này được gửi từ ứng dụng Lập Trình Mạng (Socket SMTP) của nhóm.\nNội dung thư đã được đồng bộ lên MongoDB Atlas Cloud và chuyển tiếp trực tiếp vào hộp thư Gmail của bạn.");
        txtBody.setPrefRowCount(4);
        grid.add(txtBody, 1, 4);

        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        btnSend = new Button("🚀 GỬI THƯ NGAY");
        btnSend.getStyleClass().add("btn-primary");
        btnSend.setStyle("-fx-font-size: 14px; -fx-padding: 9px 24px;");
        btnSend.setOnAction(e -> executeSendMail());

        Label lblSendNote = new Label("Thư sẽ được gửi qua Socket mạng, lưu vào MongoDB và xuất hiện ngay trên Gmail web!");
        lblSendNote.getStyleClass().add("sub-title");

        actionRow.getChildren().addAll(btnSend, lblSendNote);

        formCard.getChildren().addAll(formTitle, grid, actionRow);

        // Khung Console Protocol Log bên dưới
        VBox logBox = new VBox(6);
        logBox.getStyleClass().add("card");
        VBox.setVgrow(logBox, Priority.ALWAYS);

        HBox logHeader = new HBox(10);
        logHeader.setAlignment(Pos.CENTER_LEFT);

        Label lblLogT = new Label("Nhật Ký Giao Tiếp Socket SMTP (Protocol Stream Live)");
        lblLogT.getStyleClass().add("section-title");
        HBox.setHgrow(lblLogT, Priority.ALWAYS);

        Button btnClear = new Button("Xóa Log");
        btnClear.getStyleClass().add("btn-secondary");
        btnClear.setOnAction(e -> txtProtocolLog.clear());

        logHeader.getChildren().addAll(lblLogT, btnClear);

        txtProtocolLog = new TextArea();
        txtProtocolLog.setEditable(false);
        txtProtocolLog.getStyleClass().add("terminal-area");
        VBox.setVgrow(txtProtocolLog, Priority.ALWAYS);

        logBox.getChildren().addAll(logHeader, txtProtocolLog);

        content.getChildren().addAll(formCard, logBox);
        tab.setContent(content);
        return tab;
    }

    private void executeSendMail() {
        String from = txtFrom.getText().trim();
        String to = txtTo.getText().trim();
        String subject = txtSubject.getText().trim();
        String body = txtBody.getText().trim();

        if (to.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng nhập địa chỉ người nhận (To)!");
            return;
        }

        EmailMessage email = new EmailMessage(from, to, subject, body);

        btnSend.setDisable(true);
        btnSend.setText("Đang Gửi...");

        log("[SMTP Client]: Bắt đầu gửi thư tới: " + to + " | Tiêu đề: " + subject);

        new Thread(() -> {
            boolean success;
            if (radViaServer.isSelected()) {
                // Chế độ 1: Gửi qua LAN Server
                String sHost = txtServerHost.getText().trim();
                int sPort;
                try {
                    sPort = Integer.parseInt(txtSmtpPort.getText().trim());
                } catch (NumberFormatException e) {
                    sPort = 2525;
                }
                log("--> [Socket Mode]: Kết nối tới Máy Chủ Server tại " + sHost + ":" + sPort);
                success = SmtpClientSocket.sendMailToServer(sHost, sPort, email, this::log);
            } else {
                // Chế độ 2: Gửi trực tiếp Gmail SSL 465
                log("--> [Direct Mode]: Kết nối trực tiếp SSL tới smtp.gmail.com:465");
                String gUser = AppConfig.getGmailSender();
                String gPass = AppConfig.getGmailPassword();
                success = SmtpRelaySender.sendRealMail(gUser, gPass, email, this::log);
            }

            Platform.runLater(() -> {
                btnSend.setDisable(false);
                btnSend.setText("🚀 GỬI THƯ NGAY");
                if (success) {
                    showAlert(Alert.AlertType.INFORMATION, "Thành Công!",
                            "Thư đã được gửi thành công!\n- Dữ liệu đã lưu trên MongoDB Atlas Cloud.\n- Thư thật đã gửi vào Gmail của: " + to);
                } else {
                    showAlert(Alert.AlertType.ERROR, "Lỗi Gửi Thư",
                            "Chưa thể gửi thư. Vui lòng kiểm tra lại kết nối mạng hoặc xem chi tiết lỗi trong khung Terminal Log bên dưới.");
                }
            });
        }).start();
    }

    private Tab createInboxTab() {
        Tab tab = new Tab("📥 Hộp Thư Đến (POP3 & MongoDB)");
        tab.setClosable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(14, 0, 0, 0));

        // Thanh thao tác POP3
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("card");

        Label lblUser = new Label("Tài khoản:");
        txtPopUser = new TextField(AppConfig.getGmailSender());
        txtPopUser.setPrefWidth(220);

        Label lblPass = new Label("Mật khẩu:");
        txtPopPass = new PasswordField();
        txtPopPass.setText(AppConfig.getGmailPassword());
        txtPopPass.setPrefWidth(140);

        Button btnFetchPop = new Button("📥 Lấy Thư Qua POP3 Socket");
        btnFetchPop.getStyleClass().add("btn-primary");
        btnFetchPop.setOnAction(e -> fetchMailsViaPop3());

        Button btnSyncMongo = new Button("☁️ Đồng Bộ Trực Tiếp Từ MongoDB Atlas");
        btnSyncMongo.getStyleClass().add("btn-secondary");
        btnSyncMongo.setOnAction(e -> syncFromMongoDB());

        bar.getChildren().addAll(lblUser, txtPopUser, lblPass, txtPopPass, btnFetchPop, btnSyncMongo);

        // Khu vực xem thư 2 cột
        SplitPane split = new SplitPane();
        VBox.setVgrow(split, Priority.ALWAYS);

        // Cột trái: Danh sách thư
        VBox left = new VBox(8);
        left.setPadding(new Insets(8));
        Label lblBoxTitle = new Label("Hộp Thư Đến (Inbox)");
        lblBoxTitle.getStyleClass().add("section-title");

        listInbox = new ListView<>();
        VBox.setVgrow(listInbox, Priority.ALWAYS);
        listInbox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                renderMailView(newV);
            }
        });
        left.getChildren().addAll(lblBoxTitle, listInbox);

        // Cột phải: Xem chi tiết thư
        VBox right = new VBox(8);
        right.setPadding(new Insets(8));

        HBox viewHeader = new HBox(10);
        viewHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblViewTitle = new Label("Nội Dung Chi Tiết");
        lblViewTitle.getStyleClass().add("section-title");
        HBox.setHgrow(lblViewTitle, Priority.ALWAYS);

        Button btnDelete = new Button("🗑 Xóa Thư Này");
        btnDelete.getStyleClass().add("btn-danger");
        btnDelete.setOnAction(e -> deleteSelectedMail());

        viewHeader.getChildren().addAll(lblViewTitle, btnDelete);

        txtMailView = new TextArea();
        txtMailView.setEditable(false);
        txtMailView.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px;");
        VBox.setVgrow(txtMailView, Priority.ALWAYS);

        right.getChildren().addAll(viewHeader, txtMailView);

        split.getItems().addAll(left, right);
        split.setDividerPositions(0.42);

        content.getChildren().addAll(bar, split);
        tab.setContent(content);
        return tab;
    }

    private void renderMailView(EmailMessage m) {
        String content = String.format("Tiêu đề  : %s\n"
                + "Thời gian: %s\n"
                + "Từ       : %s\n"
                + "Đến      : %s\n"
                + "Nguồn    : %s\n"
                + "----------------------------------------------------------------------------------\n\n%s",
                m.getSubject(), m.getSentAt(), m.getSender(), m.getRecipient(), m.getProtocol(), m.getBody());
        txtMailView.setText(content);
    }

    private void fetchMailsViaPop3() {
        String host = txtServerHost.getText().trim();
        int port;
        try {
            port = Integer.parseInt(txtPop3Port.getText().trim());
        } catch (NumberFormatException e) {
            port = 1110;
        }
        final int finalPort = port;
        String u = txtPopUser.getText().trim();
        String p = txtPopPass.getText().trim();

        log("[POP3 Client]: Đang lấy danh sách thư từ máy chủ " + host + ":" + finalPort + "...");

        new Thread(() -> {
            List<EmailMessage> mails = Pop3ClientSocket.fetchMails(host, finalPort, u, p, this::log);
            Platform.runLater(() -> {
                listInbox.getItems().clear();
                listInbox.getItems().addAll(mails);
                log("[POP3 Client]: Tải thành công " + mails.size() + " thư qua giao thức POP3.");
                if (mails.isEmpty()) {
                    txtMailView.setText("(Hộp thư trống hoặc chưa có thư nào gửi tới " + u + ")");
                }
            });
        }).start();
    }

    private void syncFromMongoDB() {
        String u = txtPopUser.getText().trim();
        log("[MongoDB Client]: Đang đồng bộ danh sách thư từ cụm Atlas Cloud...");
        new Thread(() -> {
            List<EmailMessage> mails = MongoManager.getInstance().getEmailsForUser(u);
            Platform.runLater(() -> {
                listInbox.getItems().clear();
                listInbox.getItems().addAll(mails);
                log("[MongoDB Client]: Đã nạp " + mails.size() + " thư từ MongoDB Atlas.");
                if (mails.isEmpty()) {
                    txtMailView.setText("(Chưa tìm thấy bản ghi nào trên MongoDB Atlas cho tài khoản: " + u + ")");
                }
            });
        }).start();
    }

    private void deleteSelectedMail() {
        EmailMessage selected = listInbox.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng chọn một bức thư để xóa!");
            return;
        }

        new Thread(() -> {
            MongoManager.getInstance().deleteEmail(selected.getId());
            Platform.runLater(() -> {
                listInbox.getItems().remove(selected);
                txtMailView.clear();
                showAlert(Alert.AlertType.INFORMATION, "Đã Xóa", "Đã xóa thư thành công khỏi cơ sở dữ liệu!");
            });
        }).start();
    }

    private Tab createSettingsTab() {
        Tab tab = new Tab("⚙️ Cấu Hình");
        tab.setClosable(false);

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.getStyleClass().add("card");

        Label title = new Label("CÀI ĐẶT THÔNG SỐ HỆ THỐNG");
        title.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label("Tài khoản Gmail Relay:"), 0, 0);
        TextField txtSetUser = new TextField(AppConfig.getGmailSender());
        txtSetUser.setPrefWidth(350);
        grid.add(txtSetUser, 1, 0);

        grid.add(new Label("Mật khẩu / App Password:"), 0, 1);
        PasswordField txtSetPass = new PasswordField();
        txtSetPass.setText(AppConfig.getGmailPassword());
        grid.add(txtSetPass, 1, 1);

        grid.add(new Label("MongoDB Atlas URI:"), 0, 2);
        TextField txtSetMongo = new TextField(AppConfig.getMongoUri());
        txtSetMongo.setPrefWidth(550);
        grid.add(txtSetMongo, 1, 2);

        Button btnSave = new Button("💾 Lưu Cài Đặt Vào config.properties");
        btnSave.getStyleClass().add("btn-primary");
        btnSave.setOnAction(e -> {
            AppConfig.set("gmail.sender", txtSetUser.getText().trim());
            AppConfig.set("gmail.app_password", txtSetPass.getText().trim());
            AppConfig.set("mongodb.uri", txtSetMongo.getText().trim());
            AppConfig.set("server.ip", txtServerHost.getText().trim());
            AppConfig.set("server.smtp.port", txtSmtpPort.getText().trim());
            AppConfig.set("server.pop3.port", txtPop3Port.getText().trim());
            showAlert(Alert.AlertType.INFORMATION, "Cấu hình", "Đã lưu cài đặt thành công!");
        });

        content.getChildren().addAll(title, grid, btnSave);
        tab.setContent(content);
        return tab;
    }

    private void autoScanLanServer() {
        int targetPort;
        try {
            targetPort = Integer.parseInt(txtSmtpPort.getText().trim());
        } catch (Exception e) {
            targetPort = 2525;
        }

        final int port = targetPort;
        log("[Quét LAN]: Đang bắt đầu dò tìm Mail Server (cổng " + port + ") trên các dải mạng LAN...");

        new Thread(() -> {
            List<String> foundIps = new ArrayList<>();

            // 1. Kiểm tra ưu tiên IP đang nhập trong ô trước
            String currentTypedIp = txtServerHost.getText().trim();
            if (!currentTypedIp.isEmpty() && !currentTypedIp.equals("127.0.0.1") && !currentTypedIp.equals("localhost")) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(currentTypedIp, port), 800);
                    foundIps.add(currentTypedIp);
                } catch (Exception ignored) {}
            }

            // 2. Quét toàn bộ các dải mạng vật lý thực tế (Wi-Fi, LAN)
            if (foundIps.isEmpty()) {
                Set<String> subnets = getLocalPhysicalSubnets();
                for (String subnet : subnets) {
                    log("[Quét LAN]: Đang quét dải: " + subnet + "1 -> " + subnet + "254");
                    ExecutorService pool = Executors.newFixedThreadPool(60);
                    List<Future<String>> futures = new ArrayList<>();

                    for (int i = 1; i <= 254; i++) {
                        final String testIp = subnet + i;
                        futures.add(pool.submit(() -> {
                            try (Socket s = new Socket()) {
                                s.connect(new InetSocketAddress(testIp, port), 500);
                                return testIp;
                            } catch (Exception ignored) {
                                return null;
                            }
                        }));
                    }

                    for (Future<String> f : futures) {
                        try {
                            String res = f.get();
                            if (res != null && !foundIps.contains(res)) {
                                foundIps.add(res);
                            }
                        } catch (Exception ignored) {}
                    }
                    pool.shutdown();
                    if (!foundIps.isEmpty()) break; // Tìm thấy server thì dừng quét
                }
            }

            Platform.runLater(() -> {
                if (!foundIps.isEmpty()) {
                    String found = foundIps.get(0);
                    txtServerHost.setText(found);
                    log("[Quét LAN]: Tìm thấy Mail Server tại địa chỉ IP: " + found);
                    showAlert(Alert.AlertType.INFORMATION, "Quét LAN Thành Công",
                            "Đã tìm thấy Mail Server tại IP: " + found + "\nĐã tự động điền vào ô kết nối!");
                } else {
                    log("[Quét LAN]: Không tìm thấy server nào đang mở cổng " + port + ".");
                    showAlert(Alert.AlertType.WARNING, "Không Tìm Thấy Server",
                            "Không tìm thấy máy chủ nào đang mở cổng " + port + " trong mạng LAN.\n\n"
                            + "Nguyên nhân phổ biến:\n"
                            + "1. Máy Server chưa nhấp 'Khởi Động Server'.\n"
                            + "2. Tường lửa (Windows Firewall) trên máy Server đang chặn kết nối mạng ngoài.\n"
                            + "   -> Hãy chạy file 'mo_port_firewall.bat' trên máy Server để mở port!");
                }
            });
        }).start();
    }

    private Set<String> getLocalPhysicalSubnets() {
        Set<String> subnets = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(interfaces)) {
                if (iface.isLoopback() || !iface.isUp()) continue;

                String name = (iface.getName() + " " + iface.getDisplayName()).toLowerCase();
                // Bỏ qua card mạng ảo VMware, VirtualBox, WSL, Bluetooth
                if (name.contains("virtual") || name.contains("vmnet") || name.contains("vbox") ||
                    name.contains("wsl") || name.contains("hyper-v") || name.contains("pseudo") ||
                    name.contains("bluetooth") || name.contains("teredo") || name.contains("host-only")) {
                    continue;
                }

                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        int lastDot = ip.lastIndexOf('.');
                        if (lastDot > 0) {
                            subnets.add(ip.substring(0, lastDot + 1));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        if (subnets.isEmpty()) subnets.add("192.168.1.");
        return subnets;
    }

    private void log(String msg) {
        String timestamp = LocalTime.now().format(TIME_FMT);
        Platform.runLater(() -> {
            if (txtProtocolLog != null) {
                txtProtocolLog.appendText("[" + timestamp + "] " + msg + "\n");
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
