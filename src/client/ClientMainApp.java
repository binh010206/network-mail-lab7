package client;

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
import network.Pop3ClientSocket;
import network.SmtpClientSocket;
import network.SmtpRelaySender;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class ClientMainApp extends Application {

    private TextField txtServerHost;
    private TextField txtSmtpPort;
    private TextField txtPop3Port;

    // Quản lý Đăng nhập / Đăng ký
    private TextField txtAuthEmail;
    private PasswordEyeBox passAuth;
    private Label lblCurrentLoggedIn;
    private String currentUserEmail = "";

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
    private PasswordEyeBox passPop;
    private ListView<EmailMessage> listInbox;
    private TextArea txtMailView;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static class PasswordEyeBox extends HBox {
        private final PasswordField passwordField = new PasswordField();
        private final TextField textField = new TextField();
        private final Button btnEye = new Button("👁");
        private boolean isVisible = false;

        public PasswordEyeBox(String defaultPass) {
            super(5);
            setAlignment(Pos.CENTER_LEFT);
            passwordField.setText(defaultPass);
            textField.setText(defaultPass);
            textField.setManaged(false);
            textField.setVisible(false);

            HBox.setHgrow(passwordField, Priority.ALWAYS);
            HBox.setHgrow(textField, Priority.ALWAYS);

            btnEye.getStyleClass().add("btn-secondary");
            btnEye.setStyle("-fx-padding: 7px 11px; -fx-font-size: 13px;");

            btnEye.setOnAction(e -> {
                isVisible = !isVisible;
                if (isVisible) {
                    textField.setText(passwordField.getText());
                    passwordField.setManaged(false);
                    passwordField.setVisible(false);
                    textField.setManaged(true);
                    textField.setVisible(true);
                    btnEye.setText("🙈");
                } else {
                    passwordField.setText(textField.getText());
                    textField.setManaged(false);
                    textField.setVisible(false);
                    passwordField.setManaged(true);
                    passwordField.setVisible(true);
                    btnEye.setText("👁");
                }
            });

            getChildren().addAll(passwordField, textField, btnEye);
        }

        public String getPassword() {
            return isVisible ? textField.getText() : passwordField.getText();
        }

        public void setPassword(String pass) {
            passwordField.setText(pass);
            textField.setText(pass);
        }

        public void setFieldWidth(double w) {
            super.setPrefWidth(w);
            passwordField.setPrefWidth(w);
            textField.setPrefWidth(w);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("VKU Email Client");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));

        root.setTop(createTopBar());

        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(createAuthTab());
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

        Label title = new Label("EMAIL CLIENT");
        title.getStyleClass().add("header-title");
        HBox.setHgrow(title, Priority.ALWAYS);

        lblCurrentLoggedIn = new Label("Chưa đăng nhập");
        lblCurrentLoggedIn.getStyleClass().add("badge-offline");

        Button btnScanLan = new Button("Quét Server LAN");
        btnScanLan.getStyleClass().add("btn-secondary");
        btnScanLan.setOnAction(e -> autoScanLanServer());

        topTitleRow.getChildren().addAll(title, lblCurrentLoggedIn, btnScanLan);

        HBox connBar = new HBox(12);
        connBar.setAlignment(Pos.CENTER_LEFT);
        connBar.setStyle("-fx-background-color: #ffffff; -fx-padding: 10px 14px; -fx-background-radius: 8px; -fx-border-color: #cbd5e1; -fx-border-radius: 8px;");

        Label lblHost = new Label("IP Server:");
        lblHost.setStyle("-fx-font-weight: bold;");
        txtServerHost = new TextField(AppConfig.getServerIp());
        txtServerHost.setPrefWidth(180);

        Label lblSmtp = new Label("Cổng SMTP:");
        txtSmtpPort = new TextField(String.valueOf(AppConfig.getSmtpPort()));
        txtSmtpPort.setPrefWidth(75);

        Label lblPop = new Label("Cổng POP3:");
        txtPop3Port = new TextField(String.valueOf(AppConfig.getPop3Port()));
        txtPop3Port.setPrefWidth(75);

        connBar.getChildren().addAll(lblHost, txtServerHost, lblSmtp, txtSmtpPort, lblPop, txtPop3Port);

        box.getChildren().addAll(topTitleRow, connBar);
        return box;
    }

    private Tab createAuthTab() {
        Tab tab = new Tab("👤 Tài Khoản");
        tab.setClosable(false);

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.getStyleClass().add("card");

        Label formTitle = new Label("ĐĂNG KÝ HOẶC ĐĂNG NHẬP");
        formTitle.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        grid.add(new Label("Email tài khoản:"), 0, 0);
        txtAuthEmail = new TextField(AppConfig.getGmailSender());
        txtAuthEmail.setPrefWidth(350);
        grid.add(txtAuthEmail, 1, 0);

        grid.add(new Label("Mật khẩu:"), 0, 1);
        passAuth = new PasswordEyeBox(AppConfig.getGmailPassword());
        passAuth.setFieldWidth(350);
        grid.add(passAuth, 1, 1);

        HBox btnRow = new HBox(14);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        Button btnRegister = new Button("Đăng Ký Tài Khoản Mới");
        btnRegister.getStyleClass().add("btn-success");
        btnRegister.setOnAction(e -> executeRegister());

        Button btnLogin = new Button("Đăng Nhập");
        btnLogin.getStyleClass().add("btn-primary");
        btnLogin.setOnAction(e -> executeLogin());

        btnRow.getChildren().addAll(btnRegister, btnLogin);

        content.getChildren().addAll(formTitle, grid, btnRow);
        tab.setContent(content);
        return tab;
    }

    private void executeRegister() {
        String email = txtAuthEmail.getText().trim();
        String pass = passAuth.getPassword().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng nhập đầy đủ Email và Mật khẩu!");
            return;
        }

        boolean ok = UserManager.registerLocal(email, pass, this::log);
        if (ok) {
            currentUserEmail = email;
            txtFrom.setText(email);
            txtPopUser.setText(email);
            passPop.setPassword(pass);
            lblCurrentLoggedIn.setText("User: " + email);
            lblCurrentLoggedIn.getStyleClass().clear();
            lblCurrentLoggedIn.getStyleClass().add("badge-online");

            showAlert(Alert.AlertType.INFORMATION, "Thành Công",
                    "Đăng ký thành công tài khoản: " + email + "\n"
                    + "- Đã tạo thư mục users/" + email + "\n"
                    + "- Đã lưu thông tin tài khoản lên MongoDB Atlas.");
        } else {
            showAlert(Alert.AlertType.ERROR, "Thất Bại", "Tài khoản " + email + " đã tồn tại hoặc xảy ra lỗi!");
        }
    }

    private void executeLogin() {
        String email = txtAuthEmail.getText().trim();
        String pass = passAuth.getPassword().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng nhập Email và Mật khẩu!");
            return;
        }

        boolean ok = UserManager.loginLocal(email, pass);
        if (ok) {
            currentUserEmail = email;
            txtFrom.setText(email);
            txtPopUser.setText(email);
            passPop.setPassword(pass);
            lblCurrentLoggedIn.setText("User: " + email);
            lblCurrentLoggedIn.getStyleClass().clear();
            lblCurrentLoggedIn.getStyleClass().add("badge-online");

            showAlert(Alert.AlertType.INFORMATION, "Thành Công", "Đăng nhập thành công cho tài khoản: " + email);
        } else {
            showAlert(Alert.AlertType.ERROR, "Thất Bại", "Email hoặc Mật khẩu không chính xác!");
        }
    }

    private Tab createComposeTab() {
        Tab tab = new Tab("✉️ Soạn & Gửi Thư");
        tab.setClosable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(14, 0, 0, 0));

        VBox formCard = new VBox(10);
        formCard.getStyleClass().add("card");

        Label formTitle = new Label("SOẠN THƯ MỚI");
        formTitle.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        grid.add(new Label("Người gửi:"), 0, 0);
        txtFrom = new TextField(AppConfig.getGmailSender());
        txtFrom.setPrefWidth(420);
        grid.add(txtFrom, 1, 0);

        grid.add(new Label("Người nhận:"), 0, 1);
        txtTo = new TextField("lenhuanphat1711@gmail.com");
        grid.add(txtTo, 1, 1);

        grid.add(new Label("Tiêu đề:"), 0, 2);
        txtSubject = new TextField("Kiểm Thử Bài Tập Lập Trình Mạng Lab 7 - VKU");
        grid.add(txtSubject, 1, 2);

        HBox modeBox = new HBox(20);
        modeBox.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup tg = new ToggleGroup();

        radViaServer = new RadioButton("Gửi qua Server");
        radViaServer.setToggleGroup(tg);
        radViaServer.setSelected(true);

        radDirectGmail = new RadioButton("Gửi trực tiếp Gmail");
        radDirectGmail.setToggleGroup(tg);

        modeBox.getChildren().addAll(radViaServer, radDirectGmail);
        grid.add(new Label("Chế độ gửi:"), 0, 3);
        grid.add(modeBox, 1, 3);

        grid.add(new Label("Nội dung:"), 0, 4);
        txtBody = new TextArea("Chào bạn!\nThư này được gửi từ ứng dụng Lập Trình Mạng của nhóm.\nNội dung thư đã được đồng bộ lên MongoDB Atlas và gửi trực tiếp vào hộp thư Gmail của bạn.");
        txtBody.setPrefRowCount(4);
        grid.add(txtBody, 1, 4);

        btnSend = new Button("GỬI THƯ");
        btnSend.getStyleClass().add("btn-primary");
        btnSend.setStyle("-fx-font-size: 14px; -fx-padding: 9px 28px;");
        btnSend.setOnAction(e -> executeSendMail());

        formCard.getChildren().addAll(formTitle, grid, btnSend);

        VBox logBox = new VBox(6);
        logBox.getStyleClass().add("card");
        VBox.setVgrow(logBox, Priority.ALWAYS);

        HBox logHeader = new HBox(10);
        logHeader.setAlignment(Pos.CENTER_LEFT);

        Label lblLogT = new Label("Nhật Ký Giao Tiếp Socket");
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
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng nhập địa chỉ người nhận!");
            return;
        }

        EmailMessage email = new EmailMessage(from, to, subject, body);

        btnSend.setDisable(true);
        btnSend.setText("Đang Gửi...");

        log("[SMTP Client]: Gửi thư tới " + to + " | Tiêu đề: " + subject);

        new Thread(() -> {
            boolean success;
            if (radViaServer.isSelected()) {
                String sHost = txtServerHost.getText().trim();
                int sPort;
                try {
                    sPort = Integer.parseInt(txtSmtpPort.getText().trim());
                } catch (NumberFormatException e) {
                    sPort = 2525;
                }
                log("--> Kết nối tới Server tại " + sHost + ":" + sPort);
                success = SmtpClientSocket.sendMailToServer(sHost, sPort, email, this::log);
            } else {
                log("--> Kết nối trực tiếp SSL tới smtp.gmail.com:465");
                String gUser = AppConfig.getGmailSender();
                String gPass = AppConfig.getGmailPassword();
                success = SmtpRelaySender.sendRealMail(gUser, gPass, email, this::log);
            }

            if (success) {
                UserManager.saveSentMailLocal(from, email);
            }

            Platform.runLater(() -> {
                btnSend.setDisable(false);
                btnSend.setText("GỬI THƯ");
                if (success) {
                    showAlert(Alert.AlertType.INFORMATION, "Thành Công",
                            "Thư đã gửi thành công!\n- Đã lưu vào thư mục users/" + from + "/sent/\n- Đã đồng bộ lên MongoDB Atlas.\n- Đã chuyển tiếp vào Gmail người nhận: " + to);
                } else {
                    showAlert(Alert.AlertType.ERROR, "Lỗi Gửi Thư",
                            "Chưa thể gửi thư. Vui lòng kiểm tra lại kết nối mạng hoặc xem chi tiết lỗi trong Terminal bên dưới.");
                }
            });
        }).start();
    }

    private Tab createInboxTab() {
        Tab tab = new Tab("📥 Hộp Thư Đến");
        tab.setClosable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(14, 0, 0, 0));

        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("card");

        Label lblUser = new Label("Tài khoản:");
        txtPopUser = new TextField(AppConfig.getGmailSender());
        txtPopUser.setPrefWidth(240);

        Label lblPass = new Label("Mật khẩu:");
        passPop = new PasswordEyeBox(AppConfig.getGmailPassword());
        passPop.setFieldWidth(180);

        Button btnFetchPop = new Button("Lấy Thư Qua POP3");
        btnFetchPop.getStyleClass().add("btn-primary");
        btnFetchPop.setOnAction(e -> fetchMailsViaPop3());

        Button btnSyncMongo = new Button("Đồng Bộ Từ MongoDB");
        btnSyncMongo.getStyleClass().add("btn-secondary");
        btnSyncMongo.setOnAction(e -> syncFromMongoDB());

        bar.getChildren().addAll(lblUser, txtPopUser, lblPass, passPop, btnFetchPop, btnSyncMongo);

        SplitPane split = new SplitPane();
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox left = new VBox(8);
        left.setPadding(new Insets(8));
        Label lblBoxTitle = new Label("Hộp Thư Đến");
        lblBoxTitle.getStyleClass().add("section-title");

        listInbox = new ListView<>();
        VBox.setVgrow(listInbox, Priority.ALWAYS);
        listInbox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                renderMailView(newV);
            }
        });
        left.getChildren().addAll(lblBoxTitle, listInbox);

        VBox right = new VBox(8);
        right.setPadding(new Insets(8));

        HBox viewHeader = new HBox(10);
        viewHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblViewTitle = new Label("Nội Dung Chi Tiết");
        lblViewTitle.getStyleClass().add("section-title");
        HBox.setHgrow(lblViewTitle, Priority.ALWAYS);

        Button btnDelete = new Button("Xóa Thư");
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
        String p = passPop.getPassword().trim();

        log("[POP3 Client]: Lấy danh sách thư từ máy chủ " + host + ":" + finalPort);

        new Thread(() -> {
            List<EmailMessage> mails = Pop3ClientSocket.fetchMails(host, finalPort, u, p, this::log);
            for (EmailMessage m : mails) {
                UserManager.saveInboxMailLocal(u, m);
            }
            Platform.runLater(() -> {
                listInbox.getItems().clear();
                listInbox.getItems().addAll(mails);
                log("[POP3 Client]: Đã nạp " + mails.size() + " thư.");
                if (mails.isEmpty()) {
                    txtMailView.setText("Hộp thư trống");
                }
            });
        }).start();
    }

    private void syncFromMongoDB() {
        String u = txtPopUser.getText().trim();
        log("[MongoDB Client]: Đồng bộ danh sách thư từ cụm Atlas Cloud...");
        new Thread(() -> {
            List<EmailMessage> mails = MongoManager.getInstance().getEmailsForUser(u);
            Platform.runLater(() -> {
                listInbox.getItems().clear();
                listInbox.getItems().addAll(mails);
                log("[MongoDB Client]: Đã nạp " + mails.size() + " thư từ MongoDB.");
                if (mails.isEmpty()) {
                    txtMailView.setText("Chưa có thư nào trong database");
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
                showAlert(Alert.AlertType.INFORMATION, "Đã Xóa", "Đã xóa thư thành công.");
            });
        }).start();
    }

    private Tab createSettingsTab() {
        Tab tab = new Tab("⚙️ Cấu Hình");
        tab.setClosable(false);

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.getStyleClass().add("card");

        Label title = new Label("CÀI ĐẶT THÔNG SỐ");
        title.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label("Tài khoản Gmail Relay:"), 0, 0);
        TextField txtSetUser = new TextField(AppConfig.getGmailSender());
        txtSetUser.setPrefWidth(350);
        grid.add(txtSetUser, 1, 0);

        grid.add(new Label("Mật khẩu Gmail App Password:"), 0, 1);
        PasswordEyeBox passSet = new PasswordEyeBox(AppConfig.getGmailPassword());
        passSet.setFieldWidth(350);
        grid.add(passSet, 1, 1);

        grid.add(new Label("MongoDB Atlas URI:"), 0, 2);
        TextField txtSetMongo = new TextField(AppConfig.getMongoUri());
        txtSetMongo.setPrefWidth(550);
        grid.add(txtSetMongo, 1, 2);

        Button btnSave = new Button("Lưu Cài Đặt");
        btnSave.getStyleClass().add("btn-primary");
        btnSave.setOnAction(e -> {
            AppConfig.set("gmail.sender", txtSetUser.getText().trim());
            AppConfig.set("gmail.app_password", passSet.getPassword().trim());
            AppConfig.set("mongodb.uri", txtSetMongo.getText().trim());
            AppConfig.set("server.ip", txtServerHost.getText().trim());
            AppConfig.set("server.smtp.port", txtSmtpPort.getText().trim());
            AppConfig.set("server.pop3.port", txtPop3Port.getText().trim());
            showAlert(Alert.AlertType.INFORMATION, "Cấu hình", "Đã lưu cài đặt thành công.");
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
        log("[Quét LAN]: Đang dò tìm Mail Server VKU trên mạng...");

        new Thread(() -> {
            List<String> foundIps = new ArrayList<>();

            // 1. Kiểm tra IP đang nhập trong ô trước (nếu có và trả về đúng banner VKU-LAN-Mail-Server)
            String currentTypedIp = txtServerHost.getText().trim();
            if (!currentTypedIp.isEmpty() && !currentTypedIp.equals("127.0.0.1") && !currentTypedIp.equals("localhost")) {
                if (verifyVkuMailServer(currentTypedIp, port)) {
                    foundIps.add(currentTypedIp);
                }
            }

            // 2. Nếu chưa có, quét các dải mạng vật lý và XÁC THỰC BANNER VKU-LAN-Mail-Server
            if (foundIps.isEmpty()) {
                Set<String> subnets = getLocalPhysicalSubnets();
                for (String subnet : subnets) {
                    log("[Quét LAN]: Đang quét dải: " + subnet + "1 -> " + subnet + "254");
                    ExecutorService pool = Executors.newFixedThreadPool(60);
                    List<Future<String>> futures = new ArrayList<>();

                    for (int i = 1; i <= 254; i++) {
                        final String testIp = subnet + i;
                        futures.add(pool.submit(() -> {
                            if (verifyVkuMailServer(testIp, port)) {
                                return testIp;
                            }
                            return null;
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
                    if (!foundIps.isEmpty()) break;
                }
            }

            Platform.runLater(() -> {
                if (!foundIps.isEmpty()) {
                    String found = foundIps.get(0);
                    txtServerHost.setText(found);
                    log("[Quét LAN]: Đã tìm thấy Mail Server tại IP: " + found);
                    showAlert(Alert.AlertType.INFORMATION, "Quét LAN Hoàn Tất",
                            "Đã tìm thấy Mail Server tại IP: " + found);
                } else {
                    log("[Quét LAN]: Không tìm thấy Mail Server nào đang mở trên mạng.");
                    showAlert(Alert.AlertType.WARNING, "Không Tìm Thấy",
                            "Không tìm thấy máy chủ VKU Mail Server nào đang chạy.\n"
                            + "Hãy đảm bảo máy Server đã bấm 'Khởi Động Server' và đã chạy 'mo_port_firewall.bat'!");
                }
            });
        }).start();
    }

    // Kết nối và bắt buộc đọc banner phải chứa 'VKU-LAN-Mail-Server'
    private boolean verifyVkuMailServer(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 600);
            s.setSoTimeout(1000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String banner = reader.readLine();
            return (banner != null && banner.contains("VKU-LAN-Mail-Server"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private Set<String> getLocalPhysicalSubnets() {
        Set<String> subnets = new LinkedHashSet<>();
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
