package org.example.user;

import org.example.Main;
import org.example.data.Block;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class User {
    private final Main main = new Main();
    private final Block block = new Block();
    private final int serverPort = 8080;
    private JSONObject jsonObject;
    private String inputData;

    public String[] serverAddress = {"192.168.18.64", "180.247.17.249", "36.81.114.212", "192.168.43.135"};

    public void createDigitalSign(String nameFileKey, String data) {
        try {
            String publicKey = getPublicKeyStringFromJSONFile(nameFileKey);
            String privateKey = getPrivateKeyStringFromJSONFile(nameFileKey);
            String signData = block.signData(block.stringToPrivateKey(privateKey), data);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("public-key", publicKey);
            jsonObject.put("date", getDate());
            jsonObject.put("sign-data", signData);
            inputData = jsonObject.toString();

            String[] parts = nameFileKey.split("-");
            String leftOfDash = null;
            String fullName = null;
            if (parts.length > 1) {
                // take left word
                leftOfDash = parts[0];
                fullName = leftOfDash + "-sign.her";
                try (FileWriter fileWriter = new FileWriter(fullName)) {
                    fileWriter.write(inputData);
                    File file = new File(fullName);
                    System.out.println("created at : " + file.getAbsolutePath());
                }
            } else {
                System.out.println("wrong file");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String sendingData(String nameFile, String data) {
        jsonObject = new JSONObject();
        jsonObject.put("date", getDate());
        jsonObject.put("data", data);
        try {
            jsonObject.put("sign-data", getSignDataFromJsonFile(nameFile));
            jsonObject.put("public-key", getPublicKeyStringFromJSONFile(nameFile));
        } catch (Exception e) {
            System.out.println(e);
        }
        inputData = jsonObject.toString();
        return checkNode(serverAddress, inputData);
    }

    public String getData() {
        return checkNode(serverAddress, "-c");
    }

    public void createConfigFile(String ipAddr) throws IOException {
        jsonObject = new JSONObject();
        jsonObject.put("node-ip", ipAddr);
        inputData = jsonObject.toString();
        if (inputData != null) {
            String configFile = "blokceng.conf";
            main.file = new File(configFile);
            if (!main.file.exists()) {
                try (FileWriter fileWriter = new FileWriter(configFile)) {
                    inputData = main.jsonObject.toString();
                    fileWriter.write(inputData);
                    System.out.println("created at : " + main.file.getAbsolutePath());
                }
            }
        } else {
            System.out.println("yours data is null");
        }
    }

    public String getDate() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        TimeZone timeZone = TimeZone.getDefault();
        LocalDateTime now = LocalDateTime.now();
        return dtf.format(now) + " " + timeZone.getDisplayName(false, TimeZone.SHORT);
    }

    public static String getSignDataFromJsonFile(String fileName) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            JSONObject jsonObject = new JSONObject(stringBuilder.toString());
            return jsonObject.getString("sign-data");
        }
    }

    public static String getPublicKeyStringFromJSONFile(String fileName) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            JSONObject jsonObject = new JSONObject(stringBuilder.toString());
            return jsonObject.getString("public-key");
        }
    }

    public static String getPrivateKeyStringFromJSONFile(String fileName) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            JSONObject jsonObject = new JSONObject(stringBuilder.toString());
            return jsonObject.getString("private-key");
        }
    }


    public String getHashData(String hash) {
        StringBuilder result = new StringBuilder();
        try {
            String urlString = "http://192.168.18.64:8000/" + hash + ".her";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error, check your hash addr :) ";
//            return "Error fetching data: " + e.getMessage();
        }
        return result.toString();
    }


    public String checkNode(String[] serverAddresses, String inputData) {
        List<String> serverList = Arrays.asList(serverAddresses);
        Collections.shuffle(serverList, new Random());
        for (String serverAddress : serverList) {
            if (checkConnection(serverAddress, inputData)) {
                System.out.println("connected to " + serverAddress);
                return serverAddress;
            } else {
                System.out.println("node server is down, look for another :v \n");
            }
        }
        return null;
    }

    public boolean checkConnection(String serverAddress, String inputData) {
        System.out.println("please wait...");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverAddress, serverPort), 5000);
            socket.setSoTimeout(5000);
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                out.println(inputData);
                String response = in.readLine();
                if (response != null) {
                    System.out.println("Response : " + response);
                    return true;
                } else {
                    System.out.println("No response from server within 5 seconds.");
                }
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage() + ", node : " + serverAddress);
        }
        return false;
    }
}
