package client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;


public class Main {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 23456;
    private static final String IN_PATH = System.getProperty("user.dir") + File.separator +
//            "JSON Database with Java" + File.separator +
//           "task" + File.separator +
            "src" + File.separator +
            "client" + File.separator +
            "data" + File.separator;

    private static void connectServer (String msgJson) {
        try (
                Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output  = new DataOutputStream(socket.getOutputStream())
        ) {
            System.out.println("Client started!");

            output.writeUTF(msgJson);
            System.out.println("Sent: " + msgJson);
            System.out.println("Received: " + input.readUTF());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readFile(String filename) {
        try (FileReader reader = new FileReader(IN_PATH + filename)){
            JsonObject jsonMsg = new Gson().fromJson(reader, JsonObject.class);
            String response = new Gson().toJson(jsonMsg);
            return response;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String parseArgs(String[] args) {
        //StringBuilder msg = new StringBuilder();
        JsonObject msg = new JsonObject();
        if ((args.length >= 2) && (args[0].equals("-t"))) {
            msg.addProperty("type", args[1]);
            if ((args.length >= 4) && args[2].equals("-k")) {
                msg.addProperty("key", args[3]);
                if ((args.length >= 6) && (args[4].equals("-v"))) {
                    msg.addProperty("value", args[5]);
                }
            }
            String msgJSON = new Gson().toJson(msg);
            return msgJSON;
        }
        else if ((args.length >= 2) && args[0].equals("-in")) {
            return readFile(args[1]);
        }
        return null;
    }

    public static void main(String[] args) {
        String msgJson = parseArgs(args);
        connectServer(msgJson);
    }
}