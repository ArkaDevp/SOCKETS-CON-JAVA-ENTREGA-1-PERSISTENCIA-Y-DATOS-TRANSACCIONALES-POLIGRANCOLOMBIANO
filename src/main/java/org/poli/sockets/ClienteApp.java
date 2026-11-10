package org.poli.sockets;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClienteApp extends Application {

    private Stage mainStage;
    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter salida;
    private String miNombre;

    // Componentes de la vista de Chat
    private TextArea txtHistorialChat;
    private TextField txtMensaje;
    private ComboBox<String> cmbUsuariosConectados;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.mainStage = primaryStage;
        mostrarVentanaLogin();
    }

    // Ventana 1: Credenciales de conexión
    private void mostrarVentanaLogin() {
        mainStage.setTitle("Conectar al Servidor");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(12);
        grid.setHgap(15);
        grid.setStyle("-fx-background-color: #d6d9df;");

        Label lblIp = new Label("IP del Servidor:");
        TextField txtIp = new TextField("127.0.0.1");
        txtIp.setPrefWidth(220);

        Label lblPuerto = new Label("Puerto de la conexión:");
        TextField txtPuerto = new TextField("59420");

        Label lblNombre = new Label("Escriba su nombre:");
        TextField txtNombre = new TextField("Poli01");

        Button btnCancelar = new Button("Cancelar");
        Button btnAceptar = new Button("Aceptar");
        btnAceptar.setDefaultButton(true);

        HBox boxBotones = new HBox(10, btnCancelar, btnAceptar);
        boxBotones.setAlignment(Pos.CENTER_RIGHT);

        grid.add(lblIp, 0, 0);
        grid.add(txtIp, 1, 0);
        grid.add(lblPuerto, 0, 1);
        grid.add(txtPuerto, 1, 1);
        grid.add(lblNombre, 0, 2);
        grid.add(txtNombre, 1, 2);
        grid.add(boxBotones, 1, 3);

        btnCancelar.setOnAction(e -> Platform.exit());
        btnAceptar.setOnAction(e -> {
            String ip = txtIp.getText().trim();
            int puerto = Integer.parseInt(txtPuerto.getText().trim());
            String usuario = txtNombre.getText().trim();
            if (!usuario.isEmpty()) {
                conectarAlServidor(ip, puerto, usuario);
            }
        });

        Scene scene = new Scene(grid, 400, 190);
        mainStage.setScene(scene);
        mainStage.show();
    }

    private void conectarAlServidor(String ip, int puerto, String usuario) {
        try {
            socket = new Socket(ip, puerto);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), true);
            this.miNombre = usuario;

            // Enviar el nombre de usuario al servidor apenas se conecta
            salida.println(miNombre);

            mostrarVentanaChat();
            iniciarHiloEscucha();

        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No se pudo conectar al servidor: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    // Ventana 2: Sala de conversación
    private void mostrarVentanaChat() {
        mainStage.setTitle("Cliente Chat - " + miNombre);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #ffffff; -fx-border-color: #000000; -fx-border-width: 2;");

        // Cabecera con el nombre del usuario
        Label lblCabecera = new Label(miNombre);
        lblCabecera.setStyle("-fx-background-color: #1ec71e; -fx-text-fill: #ffff00; -fx-font-family: 'Consolas'; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 8px;");
        lblCabecera.setMaxWidth(Double.MAX_VALUE);
        lblCabecera.setAlignment(Pos.CENTER);

        // Selector de usuario conectado con quien conversar
        HBox panelDestinatario = new HBox(10);
        panelDestinatario.setPadding(new Insets(8, 10, 4, 10));
        panelDestinatario.setAlignment(Pos.CENTER_LEFT);
        Label lblPara = new Label("Conversar con:");
        cmbUsuariosConectados = new ComboBox<>();
        cmbUsuariosConectados.setPromptText("Seleccione un usuario...");
        cmbUsuariosConectados.setPrefWidth(200);
        panelDestinatario.getChildren().addAll(lblPara, cmbUsuariosConectados);

        VBox topContainer = new VBox(lblCabecera, panelDestinatario);

        // Área de mensajes recibidos y enviados
        txtHistorialChat = new TextArea();
        txtHistorialChat.setEditable(false);
        txtHistorialChat.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");

        // Barra inferior de envío
        txtMensaje = new TextField();
        txtMensaje.setPromptText("Escribe un mensaje (o 'Chao' para salir)...");
        HBox.setHgrow(txtMensaje, Priority.ALWAYS);

        Button btnEnviar = new Button("Enviar");
        btnEnviar.setDefaultButton(true);
        btnEnviar.setOnAction(e -> enviarMensaje());

        HBox bottomBar = new HBox(8, txtMensaje, btnEnviar);
        bottomBar.setPadding(new Insets(10));

        root.setTop(topContainer);
        root.setCenter(txtHistorialChat);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 420, 360);
        mainStage.setScene(scene);
        mainStage.setOnCloseRequest(e -> cerrarConexion());
    }

    private void iniciarHiloEscucha() {
        Thread listener = new Thread(() -> {
            try {
                String linea;
                while ((linea = entrada.readLine()) != null) {
                    if (linea.startsWith("CMD_USUARIOS:")) {
                        String[] usuarios = linea.substring("CMD_USUARIOS:".length()).split(",");
                        actualizarSelectorUsuarios(usuarios);
                    } else if (linea.startsWith("MSG:")) {
                        String msg = linea.substring("MSG:".length());
                        Platform.runLater(() -> txtHistorialChat.appendText(msg + "\n"));
                    }
                }
            } catch (IOException e) {
                // Socket cerrado al salir
            }
        });
        listener.setDaemon(true);
        listener.start();
    }

    private void actualizarSelectorUsuarios(String[] usuarios) {
        Platform.runLater(() -> {
            String seleccionadoActual = cmbUsuariosConectados.getValue();
            cmbUsuariosConectados.getItems().clear();
            for (String u : usuarios) {
                if (!u.equals(miNombre) && !u.isEmpty()) {
                    cmbUsuariosConectados.getItems().add(u);
                }
            }
            if (seleccionadoActual != null && cmbUsuariosConectados.getItems().contains(seleccionadoActual)) {
                cmbUsuariosConectados.setValue(seleccionadoActual);
            } else if (!cmbUsuariosConectados.getItems().isEmpty()) {
                cmbUsuariosConectados.getSelectionModel().selectFirst();
            }
        });
    }

    private void enviarMensaje() {
        String destinatario = cmbUsuariosConectados.getValue();
        String texto = txtMensaje.getText().trim();

        if (texto.isEmpty()) return;

        if (destinatario == null && !texto.equalsIgnoreCase("chao")) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Selecciona un usuario conectado para chatear.");
            alert.showAndWait();
            return;
        }

        String target = (destinatario != null) ? destinatario : miNombre;
        salida.println(target + "|" + texto);
        txtMensaje.clear();

        // Si envía "Chao"
        if (texto.equalsIgnoreCase("chao")) {
            cerrarConexion();
        }
    }

    private void cerrarConexion() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        Platform.exit();
        System.exit(0);
    }
}