package server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int PORT = 23456;
    private ServerSocket myServerSocket;
    private MyDatabase database;
    private ExecutorService executor;

    public Server() {
        database = new MyDatabase();
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            myServerSocket = serverSocket;
            System.out.println("Server started!");
            while (true) {
                Session session = new Session(serverSocket.accept(), this, database);
                executor.submit(session);
            }
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }

    public void stop() {
        try {
            myServerSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            executor.shutdown();
        }
    }
}

class Session implements Runnable {
    private final Socket socket;
    private final Server server;
    private final MyDatabase database;

    public Session(Socket socketForClient, Server server, MyDatabase database) {
        this.socket = socketForClient;
        this.server = server;
        this.database = database;
    }

    public void run() {
        //System.out.println("Running new Session.");
        try (
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream())
        ) {
            JsonObject msg = new Gson().fromJson(input.readUTF(), JsonObject.class);
            if (msg.get("type").getAsString().equals("exit")) {
                // catch exit to stop myServer
                JsonObject response = new JsonObject();
                response.addProperty("key", "OK");
                output.writeUTF(new Gson().toJson(response));
                server.stop();
            }
            else {
                String response = database.msgHandler(msg);
                output.writeUTF(response);
            }
            socket.close();
            //System.out.println("Socket closed.");
        } catch (IOException e) {
            //System.out.println("Catched it.");
            e.printStackTrace();
        }
    }
}