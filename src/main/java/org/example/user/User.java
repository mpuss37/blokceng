package org.example.user;

import org.example.Main;
import org.example.data.Block;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class User {
    Main main = new Main();
    JSONObject jsonObject;
    Block block = new Block();

    public String[] serverAddress = {"192.168.43.242", "192.168.1.121", "192.168.1.122", "192.168.1.129", "192.168.1.123", "192.168.1.100", "192.168.1.124", "192.168.100.135"};
    String inputData;
    boolean check;
    int serverPort = 8080;

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

//            System.out.print("input your name : ");
//            String nameFileDigitalSign = main.scanner.nextLine();
//            String fullName = nameFileDigitalSign + "herdi-sign.her";
            String fullName = "herdi-sign.her";
            FileWriter fileWriter = new FileWriter(fullName);
            fileWriter.write(inputData);
            File file = new File(fullName);
            System.out.println("created at : " + file.getAbsolutePath());
            fileWriter.close();
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
            boolean checkConfigFile = new File(System.getProperty("user. dir"), configFile).exists();
            if (!checkConfigFile) {
                FileWriter fileWriter = new FileWriter(configFile);
                inputData = main.jsonObject.toString();
                fileWriter.write(inputData);
                System.out.println("created at : " + main.file.getAbsolutePath());
                fileWriter.close();
            }
        } else {
            System.out.println("yours data is null");
        }
    }

    public void getDataConfigFile() {

    }


    public String getDate() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        TimeZone timeZone = TimeZone.getDefault();
        LocalDateTime now = LocalDateTime.now();
        String date = (dtf.format(now) + " " + timeZone.getDisplayName());
        return date;
    }

    public static String getSignDataFromJsonFile(String fileName) throws Exception {
        // read text file
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        reader.close();

        // change data to jsonObject
        JSONObject jsonObject = new JSONObject(stringBuilder.toString());

        // take publicKey data on jsonObject
        return jsonObject.getString("sign-data");
    }

    public static String getPublicKeyStringFromJSONFile(String fileName) throws Exception {
        // read text file
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        reader.close();

        // change data to jsonObject
        JSONObject jsonObject = new JSONObject(stringBuilder.toString());

        // take publicKey data on jsonObject
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

        // change json to jsonObject
        JSONObject jsonObject = new JSONObject(stringBuilder.toString());

        // take privateKey data on jsonObject
        return jsonObject.getString("private-key");
    }

    public String checkNode(String[] serverAddresses, String inputData) {
        List<String> serverList = Arrays.asList(serverAddresses);

        // Shuffle the list to randomize the order
        Collections.shuffle(serverList, new Random());

        for (String serverAddress : serverAddresses) {
            if (checkConnection(serverAddress, inputData)) {
                // If one connection is successful, return true
                System.out.println("connected to " + serverAddress);
                return serverAddress;
            } else {
                System.out.println("node server is down, look for another :v \n");
            }
        }
        String serverAddress = String.valueOf(serverAddresses);
        return serverAddress;
    }

    public boolean checkConnection(String serverAddress, String inputData) {
        System.out.println("please wait...");
        try (Socket socket = new Socket()) {
            //5000 milliseconds timeout for connection
            socket.connect(new InetSocketAddress(serverAddress, serverPort), 5000);
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
                    check = true;
                    System.out.println("Response : " + response);
                    return check;
                } else {
                    check = false;
                    System.out.println("No response from server within 2 seconds.");
                }
            } catch (SocketTimeoutException e) {
                check = false;
                System.out.println("No response from server within 5 seconds.");
                System.out.println("yours-key not valid or node is not running");
            }
        } catch (IOException e) {
            check = false;
            System.out.println("Error: " + e.getMessage() + ", node : " + serverAddress);
            return check;
        }
        return new Random().nextBoolean();
    }
}
