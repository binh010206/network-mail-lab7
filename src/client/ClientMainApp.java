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

    private Stage primaryStage;
    private BorderPane root;

    // Thông tin máy chủ
    private TextField txtServerHost;
    private TextField txtSmtpPort;
    private TextField txtPop3Port;
    private Label lblServerStatus;

    // Quản lý Đăng nhập / Đăng ký
    private TextField txtAuthEmail;
    private PasswordEyeBox passAuth;
    private String currentUserEmail = "";
    private String currentUserPassword = "";

    // Soạn thư
    private TextField txtFrom;
    private TextField txtTo;
    private TextField txtSubject;
    private TextArea txtBody;
    private Button btnSend;
    private TextArea txtProtocolLog;

    // Hộp thư đến
    private ListView<EmailMessage> listInbox;
    private TextArea txtMailView;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static class PasswordEyeBox extends HBox {
        private final PasswordField passwordField = new PasswordField();
        private final TextField textField = new TextField();
        private final Button btnEye = new Button("Hiện");
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
            btnEye.setStyle("-fx-padding: 7px 12px; -fx-font-size: 12px; -fx-font-weight: bold;");

            btnEye.setOnAction(e -> {
                isVisible = !isVisible;
                if (isVisible) {
                    textField.setText(passwordField.getText());
                    passwordField.setManaged(false);
                    passwordField.setVisible(false);
                    textField.setManaged(true);
                    textField.setVisible(true);
                    btnEye.setText("Ẩn");
                } else {
                    passwordField.setText(textField.getText());
                    textField.setManaged(false);
                    textField.setVisible(false);
                    passwordField.setManaged(true);
                    passwordField.setVisible(true);
                    btnEye.setText("Hiện");
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
        this.primaryStage = primaryStage;
        primaryStage.setTitle("VKU Email Client");

        txtServerHost = new TextField(AppConfig.getServerHost());
        txtSmtpPort = new TextField(String.valueOf(AppConfig.getSmtpPort()));
        txtPop3Port = new TextField(String.valueOf(AppConfig.getPop3Port()));

        root = new BorderPane();
        root.setPadding(new Insets(14));

        showAuthView();

        Scene scene = new Scene(root, 1050, 720);

        File cssFile = new File("src/resources/style.css");
        if (cssFile.exists()) {
            scene.getStylesheets().add(cssFile.toURI().toString());
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Giao diện Đăng Ký / Đăng Nhập
    private void showAuthView() {
        root.setTop(null);

        VBox centerBox = new VBox();
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(20));

        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setMaxWidth(460);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(28, 32, 28, 32));

        Label appTitle = new Label("VKU EMAIL SYSTEM");
        appTitle.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e3a8a;");

        Label appSubtitle = new Label("Đăng ký hoặc đăng nhập tài khoản");
        appSubtitle.getStyleClass().add("sub-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(14);
        grid.setAlignment(Pos.CENTER);

        grid.add(new Label("Tài khoản:"), 0, 0);
        txtAuthEmail = new TextField(currentUserEmail.isEmpty() ? "binhcs2006@gmail.com" : currentUserEmail);
        txtAuthEmail.setPrefWidth(260);
        txtAuthEmail.setPromptText("Nhập email tài khoản");
        grid.add(txtAuthEmail, 1, 0);

        grid.add(new Label("Mật khẩu:"), 0, 1);
        passAuth = new PasswordEyeBox(currentUserPassword.isEmpty() ? "123456" : currentUserPassword);
        passAuth.setFieldWidth(260);
        grid.add(passAuth, 1, 1);

        HBox btnRow = new HBox(14);
        btnRow.setAlignment(Pos.CENTER);
        btnRow.setPadding(new Insets(10, 0, 0, 0));

        Button btnLogin = new Button("Đăng Nhập");
        btnLogin.getStyleClass().add("btn-primary");
        btnLogin.setStyle("-fx-padding: 9px 24px; -fx-font-size: 14px;");
        btnLogin.setOnAction(e -> executeLogin());

        Button btnRegister = new Button("Đăng Ký");
        btnRegister.getStyleClass().add("btn-success");
        btnRegister.setStyle("-fx-padding: 9px 24px; -fx-font-size: 14px;");
        btnRegister.setOnAction(e -> executeRegister());

        btnRow.getChildren().addAll(btnLogin, btnRegister);

        card.getChildren().addAll(appTitle, appSubtitle, grid, btnRow);
        centerBox.getChildren().add(card);

        root.setCenter(centerBox);
    }

    private void executeLogin() {
        String email = txtAuthEmail.getText().trim();
        String pass = passAuth.getPassword().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng nhập tài khoản và mật khẩu!");
            return;
        }

        boolean ok = UserManager.loginLocal(email, pass);
        if (ok) {
            currentUserEmail = email;
            currentUserPassword = pass;
            showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Đăng nhập thành công!");
            showMainView();
        } else {
            showAlert(Alert.AlertType.ERROR, "Thông báo", "Tài khoản hoặc mật khẩu không chính xác!");
        }
    }

    private void executeRegister() {
        String email = txtAuthEmail.getText().trim();
        String pass = passAuth.getPassword().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng nhập tài khoản và mật khẩu!");
            return;
        }

        boolean ok = UserManager.registerLocal(email, pass, null);
        if (ok) {
            currentUserEmail = email;
            currentUserPassword = pass;
            showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Đăng ký thành công!");
            showMainView();
        } else {
            showAlert(Alert.AlertType.ERROR, "Thông báo", "Tài khoản đã tồn tại trên hệ thống!");
        }
    }

    // Giao diện chính sau khi đăng nhập
    private void showMainView() {
        root.setTop(createMainTopBar());

        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(createComposeTab());
        tabPane.getTabs().add(createInboxTab());

        root.setCenter(tabPane);
    }

    private VBox createMainTopBar() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(0, 0, 12, 0));

        HBox topRow = new HBox(14);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("VKU EMAIL CLIENT");
        title.getStyleClass().add("header-title");

        Label lblUser = new Label("Tài khoản: " + currentUserEmail);
        lblUser.getStyleClass().add("badge-online");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnLogout = new Button("Đăng Xuất");
        btnLogout.getStyleClass().add("btn-danger");
        btnLogout.setOnAction(e -> {
            currentUserEmail = "";
            currentUserPassword = "";
            showAuthView();
        });

        topRow.getChildren().addAll(title, lblUser, spacer, btnLogout);

        HBox serverBar = new HBox(12);
        serverBar.setAlignment(Pos.CENTER_LEFT);
        serverBar.setStyle("-fx-background-color: #f1f5f9; -fx-padding: 8px 12px; -fx-background-radius: 6px; -fx-border-color: #cbd5e1; -fx-border-radius: 6px;");

        lblServerStatus = new Label("Server: " + txtServerHost.getText() + ":" + txtSmtpPort.getText());
        lblServerStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: #0369a1;");

        Button btnScanLan = new Button("Quét Server LAN");
        btnScanLan.getStyleClass().add("btn-secondary");
        btnScanLan.setOnAction(e -> scanLanServers());

        serverBar.getChildren().addAll(lblServerStatus, btnScanLan);

        box.getChildren().addAll(topRow, serverBar);
        return box;
    }

    private Tab createComposeTab() {
        Tab tab = new Tab("✉️ Soạn Thư");
        tab.setClosable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(12, 0, 0, 0));

        VBox formCard = new VBox(10);
        formCard.getStyleClass().add("card");

        Label formTitle = new Label("Soạn Thư Mới");
        formTitle.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        grid.add(new Label("Người gửi:"), 0, 0);
        txtFrom = new TextField(currentUserEmail);
        txtFrom.setEditable(false);
        txtFrom.setStyle("-fx-background-color: #f8fafc;");
        txtFrom.setPrefWidth(420);
        grid.add(txtFrom, 1, 0);

        grid.add(new Label("Người nhận:"), 0, 1);
        txtTo = new TextField();
        txtTo.setPromptText("Nhập địa chỉ email người nhận");
        grid.add(txtTo, 1, 1);

        grid.add(new Label("Tiêu đề:"), 0, 2);
        txtSubject = new TextField();
        txtSubject.setPromptText("Nhập tiêu đề thư");
        grid.add(txtSubject, 1, 2);

        Label lblBody = new Label("Nội dung:");
        txtBody = new TextArea();
        txtBody.setPromptText("Nhập nội dung thư...");
        txtBody.setPrefRowCount(7);

        btnSend = new Button("GỬI THƯ");
        btnSend.getStyleClass().add("btn-primary");
        btnSend.setStyle("-fx-font-size: 14px; -fx-padding: 9px 26px;");
        btnSend.setOnAction(e -> executeSendMail());

        formCard.getChildren().addAll(formTitle, grid, lblBody, txtBody, btnSend);

        VBox terminalBox = new VBox(6);
        terminalBox.getStyleClass().add("card");
        VBox.setVgrow(terminalBox, Priority.ALWAYS);

        HBox termHeader = new HBox(10);
        termHeader.setAlignment(Pos.CENTER_LEFT);
        Label termTitle = new Label("Nhật Ký Giao Tiếp Socket");
        termTitle.getStyleClass().add("section-title");
        HBox.setHgrow(termTitle, Priority.ALWAYS);

        Button btnClearLog = new Button("Xóa Nhật Ký");
        btnClearLog.getStyleClass().add("btn-secondary");
        btnClearLog.setOnAction(e -> txtProtocolLog.clear());

        termHeader.getChildren().addAll(termTitle, btnClearLog);

        txtProtocolLog = new TextArea();
        txtProtocolLog.setEditable(false);
        txtProtocolLog.getStyleClass().add("terminal-area");
        txtProtocolLog.setPrefRowCount(8);
        VBox.setVgrow(txtProtocolLog, Priority.ALWAYS);

        terminalBox.getChildren().addAll(termHeader, txtProtocolLog);

        content.getChildren().addAll(formCard, terminalBox);
        tab.setContent(content);
        return tab;
    }

    private void executeSendMail() {
        String from = txtFrom.getText().trim();
        String to = txtTo.getText().trim();
        String subject = txtSubject.getText().trim();
        String body = txtBody.getText().trim();

        if (to.isEmpty() || !to.contains("@")) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng nhập địa chỉ email người nhận hợp lệ!");
            return;
        }

        if (subject.isEmpty()) {
            subject = "Không có tiêu đề";
        }

        EmailMessage email = new EmailMessage(from, to, subject, body);

        btnSend.setDisable(true);
        btnSend.setText("Đang Gửi...");

        log("[SMTP Client]: Đang gửi thư tới " + to + " | Tiêu đề: " + subject);

        new Thread(() -> {
            String sHost = txtServerHost.getText().trim();
            int sPort;
            try {
                sPort = Integer.parseInt(txtSmtpPort.getText().trim());
            } catch (NumberFormatException e) {
                sPort = 2525;
            }

            log("--> Kết nối tới Mail Server tại " + sHost + ":" + sPort);
            boolean success = SmtpClientSocket.sendMailToServer(sHost, sPort, email, this::log);

            if (success) {
                UserManager.saveSentMailLocal(from, email);
            }

            Platform.runLater(() -> {
                btnSend.setDisable(false);
                btnSend.setText("GỬI THƯ");
                if (success) {
                    showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Đã gửi thư thành công!");
                    txtSubject.clear();
                    txtBody.clear();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Thông báo", "Chưa thể gửi thư. Vui lòng kiểm tra lại kết nối tới Server!");
                }
            });
        }).start();
    }

    private Tab createInboxTab() {
        Tab tab = new Tab("📥 Hộp Thư Đến");
        tab.setClosable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(12, 0, 0, 0));

        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Button btnFetch = new Button("Làm Mới Hộp Thư");
        btnFetch.getStyleClass().add("btn-primary");
        btnFetch.setOnAction(e -> fetchMails());

        Button btnDelete = new Button("Xóa Thư");
        btnDelete.getStyleClass().add("btn-danger");
        btnDelete.setOnAction(e -> deleteSelectedMail());

        bar.getChildren().addAll(btnFetch, btnDelete);

        SplitPane split = new SplitPane();
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox leftBox = new VBox(8);
        leftBox.setPadding(new Insets(8));
        Label lblList = new Label("Danh Sách Thư Đến");
        lblList.getStyleClass().add("section-title");

        listInbox = new ListView<>();
        VBox.setVgrow(listInbox, Priority.ALWAYS);
        listInbox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                renderMailView(newV);
            }
        });
        leftBox.getChildren().addAll(lblList, listInbox);

        VBox rightBox = new VBox(8);
        rightBox.setPadding(new Insets(8));
        Label lblView = new Label("Nội Dung Thư");
        lblView.getStyleClass().add("section-title");

        txtMailView = new TextArea();
        txtMailView.setEditable(false);
        txtMailView.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 13px;");
        VBox.setVgrow(txtMailView, Priority.ALWAYS);
        rightBox.getChildren().addAll(lblView, txtMailView);

        split.getItems().addAll(leftBox, rightBox);
        split.setDividerPositions(0.42);

        content.getChildren().addAll(bar, split);
        tab.setContent(content);

        // Nạp thư ban đầu
        Platform.runLater(this::fetchMails);

        return tab;
    }

    private void renderMailView(EmailMessage m) {
        String content = String.format("Thời gian: %s\n"
                + "Người gửi: %s\n"
                + "Người nhận: %s\n"
                + "Tiêu đề: %s\n\n"
                + "Nội dung:\n%s",
                m.getSentAt(), m.getSender(), m.getRecipient(), m.getSubject(), m.getBody());
        txtMailView.setText(content);
    }

    private void fetchMails() {
        String host = txtServerHost.getText().trim();
        int port;
        try {
            port = Integer.parseInt(txtPop3Port.getText().trim());
        } catch (NumberFormatException e) {
            port = 1110;
        }
        final int popPort = port;

        log("[POP3 Client]: Kiểm tra thư cho tài khoản: " + currentUserEmail);

        new Thread(() -> {
            List<EmailMessage> mails = Pop3ClientSocket.fetchMails(host, popPort, currentUserEmail, currentUserPassword, this::log);
            if (mails == null || mails.isEmpty()) {
                // Thử lấy thêm từ cơ sở dữ liệu
                mails = MongoManager.getInstance().getEmailsForUser(currentUserEmail);
            }

            final List<EmailMessage> resultList = (mails != null) ? mails : new ArrayList<>();

            for (EmailMessage m : resultList) {
                UserManager.saveInboxMailLocal(currentUserEmail, m);
            }

            Platform.runLater(() -> {
                listInbox.getItems().clear();
                listInbox.getItems().addAll(resultList);
                log("[POP3 Client]: Đã nhận " + resultList.size() + " thư.");
                if (resultList.isEmpty()) {
                    txtMailView.setText("Hộp thư hiện đang trống.");
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
                showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Đã xóa thư thành công!");
            });
        }).start();
    }

    private void scanLanServers() {
        int tempPort;
        try {
            tempPort = Integer.parseInt(txtSmtpPort.getText().trim());
        } catch (NumberFormatException e) {
            tempPort = 2525;
        }
        final int port = tempPort;

        log("[Quét LAN]: Bắt đầu tìm kiếm Mail Server trên mạng...");

        new Thread(() -> {
            List<String> foundIps = new ArrayList<>();

            // 1. Kiểm tra 127.0.0.1 và localhost trước
            if (verifyVkuMailServer("127.0.0.1", port)) {
                foundIps.add("127.0.0.1");
            }

            // 2. Kiểm tra trên các card mạng LAN thật
            if (foundIps.isEmpty()) {
                Set<String> subnets = getLocalPhysicalSubnets();
                for (String subnet : subnets) {
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
                    if (lblServerStatus != null) {
                        lblServerStatus.setText("Server: " + found + ":" + txtSmtpPort.getText());
                    }
                    log("[Quét LAN]: Đã tìm thấy Mail Server tại IP: " + found);
                    showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Đã tìm thấy Mail Server tại IP: " + found);
                } else {
                    log("[Quét LAN]: Không tìm thấy Mail Server nào đang mở trên mạng.");
                    showAlert(Alert.AlertType.WARNING, "Thông báo",
                            "Không tìm thấy máy chủ VKU Mail Server nào đang chạy.\n"
                            + "Hãy đảm bảo máy Server đã bấm 'Khởi Động Server'!");
                }
            });
        }).start();
    }

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
