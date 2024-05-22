package org.example.user;

import org.example.Main;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

public class User {
    Main main = new Main();

    String serverAddress = "192.168.1.124";
    int serverPort = 8080;

    public void checkConnection() {
        try (

                Socket socket = new Socket(serverAddress, serverPort);
//                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        ) {
            // Kirim pesan ke server
//            out.println(data);
            System.out.println("connected");
        } catch (UnknownHostException e) {
            System.err.println("Host not found: " + serverAddress);
        } catch (IOException e) {
            System.err.println("cannot connect server: " + serverAddress);
        }
    }

    public void sendingData(String nameFile, String data) {
        main.jsonObject = new JSONObject();
        main.jsonObject.put("date", getDate());
        main.jsonObject.put("data", data);
        try {
            main.jsonObject.put("public-key", getPublicKeyStringFromJSONFile(nameFile));
            main.jsonObject.put("private-key", getPrivateKeyStringFromJSONFile(nameFile));
        } catch (Exception e) {
            System.out.println(e);
        }
        main.inputData = main.jsonObject.toString();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverAddress, serverPort), 5000); // 5000 milliseconds timeout for connection
            socket.setSoTimeout(5000); // 2000 milliseconds timeout for reading from socket
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // sending data
            out.println(main.inputData);

            // response server
            try {
                String response = in.readLine();
                if (response != null) {
                    System.out.println("Response : " + response);
                } else {
                    System.out.println("No response from server within 2 seconds.");
                }
            } catch (SocketTimeoutException e) {
                System.out.println("No response from server within 5 seconds.");
                System.out.println("yours-key not valid or node is not running");
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }


    public String getDate() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        TimeZone timeZone = TimeZone.getDefault();
        LocalDateTime now = LocalDateTime.now();
        String date = (dtf.format(now) + " " + timeZone.getDisplayName());
        return date;
    }

    public static String getPublicKeyStringFromJSONFile(String fileName) throws Exception {
        // Baca isi file teks
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        reader.close();

        // Ubah isi JSON menjadi objek JSONObject
        JSONObject jsonObject = new JSONObject(stringBuilder.toString());

        // Ambil nilai private key dari objek JSON
        return jsonObject.getString("public-key");
    }

    public static String getPrivateKeyStringFromJSONFile(String fileName) throws Exception {
        // Baca isi file teks
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        reader.close();

        // Ubah isi JSON menjadi objek JSONObject
        JSONObject jsonObject = new JSONObject(stringBuilder.toString());

        // Ambil nilai private key dari objek JSON
        return jsonObject.getString("private-key");
    }
}
