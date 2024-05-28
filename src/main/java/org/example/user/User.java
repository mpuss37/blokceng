package org.example.user;

import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

public class User {
    JSONObject jsonObject;

    String serverAddress1 = "192.168.1.124", serverAddress2 = "192.168.100.135";
    public String[] serverAddress = {"192.168.1.121", "192.168.1.122", "192.168.1.123", "192.168.1.100", "192.168.1.124", "192.168.100.135"};
    String inputData;
    boolean check;
    int serverPort = 8080;

    public boolean checkNode(String[] serverAddresses) {
        for (String serverAddress : serverAddresses) {
            if (checkConnection(serverAddress)) {
                // If one connection is successful, return true
                System.out.println("connected to " + serverAddress);
                return true;
            }else {
                System.out.println("false");
            }
        }
        return false;
    }

    public boolean checkConnection(String serverAddress) {
        try (Socket socket = new Socket(serverAddress, serverPort)) {
            check = true;
//                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            // Kirim pesan ke server
//            out.println(data);
        } catch (UnknownHostException e) {
            System.err.println("Host not found: " + serverAddress);
            check = false;
        } catch (IOException e) {
            check = false;
            System.err.println("cannot connect server: " + serverAddress);
        }
        return check;
    }

    public void sendingData(String nameFile, String data) {
        jsonObject = new JSONObject();
        jsonObject.put("date", getDate());
        jsonObject.put("data", data);
        try {
            jsonObject.put("private-key", getPrivateKeyStringFromJSONFile(nameFile));
            jsonObject.put("public-key", getPublicKeyStringFromJSONFile(nameFile));
        } catch (Exception e) {
            System.out.println(e);
        }
        inputData = jsonObject.toString();
        try (Socket socket = new Socket()) {
            //5000 milliseconds timeout for connection
            socket.connect(new InetSocketAddress(serverAddress1, serverPort), 5000);
            // 5000 milliseconds timeout for reading from socket
            socket.setSoTimeout(5000);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // sending data
            out.println(inputData);

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
