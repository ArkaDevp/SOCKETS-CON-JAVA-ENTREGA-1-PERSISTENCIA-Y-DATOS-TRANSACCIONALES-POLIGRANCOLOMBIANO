package org.poli.sockets;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorApp extends Application {

    private static final int PUERTO = 59420;
    private TextArea txtLog;
    private ServerSocket serverSocket;

    private final Map<String, PrintWriter> clientesConectados = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Log del Servidor - Puerto " + PUERTO);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #ffffff; -fx-border-color: #000000; -fx-border-width: 2;");

        Label lblTitulo = new Label("Log del Servidor");
        lblTitulo.setStyle("-fx-background-color: #1ec71e; -fx-text-fill: #ffff00; -fx-font-family: 'Consolas'; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 8px;");
        lblTitulo.setMaxWidth(Double.MAX_VALUE);
        lblTitulo.setAlignment(Pos.CENTER);

        txtLog = new TextArea();
        txtLog.setEditable(false);
        txtLog.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px; -fx-control-inner-background: #ffffff; -fx-text-fill: #000000;");
        VBox.setMargin(txtLog, new Insets(10));

        root.setTop(lblTitulo);
        root.setCenter(txtLog);

        Scene scene = new Scene(root, 450, 320);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> detenerServidor());
        primaryStage.show();

        iniciarServidor();
    }

    private void iniciarServidor() {
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PUERTO);
                agregarLog("Servidor iniciado y contestando OK\n");

                while (!serverSocket.isClosed()) {
                    Socket socketCliente = serverSocket.accept();
                    new ManejadorCliente(socketCliente).start();
                }
            } catch (IOException e) {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    agregarLog("Error en el servidor: " + e.getMessage());
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void agregarLog(String mensaje) {
        Platform.runLater(() -> txtLog.appendText(mensaje + "\n"));
    }

    private void notificarListaUsuarios() {
        String lista = String.join(",", clientesConectados.keySet());
        for (PrintWriter writer : clientesConectados.values()) {
            writer.println("CMD_USUARIOS:" + lista);
        }
    }

    private void detenerServidor() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        Platform.exit();
        System.exit(0);
    }

    // Clase interna para gestionar cada cliente conectado en un hilo independiente
    private class ManejadorCliente extends Thread {
        private final Socket socket;
        private String nombreUsuario;
        private BufferedReader entrada;
        private PrintWriter salida;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(socket.getOutputStream(), true);

                // 1. La primera línea que envía el cliente es su nombre de usuario
                nombreUsuario = entrada.readLine();
                if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
                    socket.close();
                    return;
                }

                clientesConectados.put(nombreUsuario, salida);
                agregarLog("Usuario \"" + nombreUsuario + "\" conectado");
                notificarListaUsuarios();

                // 2. Escuchar mensajes con formato: DESTINATARIO|MENSAJE
                String linea;
                while ((linea = entrada.readLine()) != null) {
                    String[] partes = linea.split("\\|", 2);
                    if (partes.length == 2) {
                        String destinatario = partes[0];
                        String contenido = partes[1];

                        String mensajeFormateado = nombreUsuario + "--> " + contenido;

                        // Enviar al destinatario elegido
                        PrintWriter outDestino = clientesConectados.get(destinatario);
                        if (outDestino != null && !destinatario.equals(nombreUsuario)) {
                            outDestino.println("MSG:" + mensajeFormateado);
                        }
                        // Eco de confirmación al propio remitente
                        salida.println("MSG:" + mensajeFormateado);

                        // Si el mensaje es "chao", desconectar al usuario
                        if (contenido.trim().equalsIgnoreCase("chao")) {
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                // Desconexión abrupta del cliente
            } finally {
                desconectarCliente();
            }
        }

        private void desconectarCliente() {
            if (nombreUsuario != null) {
                clientesConectados.remove(nombreUsuario);
                agregarLog("\nEl usuario \"" + nombreUsuario + "\" abandonó");
                notificarListaUsuarios();
            }
            try {
                socket.close();
            } catch (IOException e) {
                // Ignorar al cerrar
            }
        }
    }
}