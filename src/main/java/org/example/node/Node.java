package org.example.node;

import org.example.Main;
import org.example.data.Block;
import org.example.user.User;
import org.json.JSONObject;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Node {
    Main main = new Main();
    Block block = new Block();
    User user = new User();

    ServerSocket serverSocket = null;
    Socket clientSocket = null;

    public String[] serverAddress = {"192.168.1.121", "192.168.1.122","192.168.1.129", "192.168.1.123", "192.168.1.100", "192.168.1.124", "192.168.100.135"};
    private String data;
    int portNumber = 8080;

    public void startBlockScheduler() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable blockTask = () -> {
        };
        scheduler.scheduleAtFixedRate(blockTask, 1, 1, TimeUnit.MINUTES);
    }

    public int countHerFiles() {
        try {
            Path path = Paths.get(System.getProperty("user.dir"));
            return (int) Files.list(path)
                    .filter(p -> p.toString().endsWith(".her"))
                    .count();
        } catch (IOException e) {
            System.out.println("Error counting .her files: " + e.getMessage());
            return 0;
        }
    }

    public void runningNode() throws IOException, ParseException {
        System.out.println("recommended : 8080 (default) / 443 (root)");
        System.out.print("input your port : ");
        portNumber = main.scanner.nextInt();
        if (portNumber != 443 && portNumber != 8080) {
            System.out.println("false your port");
            portNumber = 8080;
        }
        serverSocket = new ServerSocket(portNumber);
        InetAddress inetAddress = serverSocket.getInetAddress();
        if (inetAddress.isAnyLocalAddress()) {
            System.out.println("running on all interfaces at port : " + portNumber);
        } else {
            System.out.println("running on ip addr : " + inetAddress.getHostAddress() + " at port " + portNumber);
        }
        System.out.println("running : " + user.getDate());
        while (true) {
            try {
                clientSocket = serverSocket.accept();
                System.out.println("client connected: " + clientSocket.getInetAddress());
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Read data from client
                Main.inputData = in.readLine();
                if ("-c".equals(Main.inputData)) {
                    // count data
                    int herFileCount = countHerFiles();
                    out.println("total data : " + herFileCount);
                } else if (Main.inputData != null) {
                    main.jsonObject = new JSONObject(Main.inputData);
                    //making new object for make json structure
                    String date = main.jsonObject.getString("date");
                    data = main.jsonObject.getString("data");
                    String signData = main.jsonObject.getString("sign-data");
                    String publicKey = main.jsonObject.getString("public-key");
                    //get value based on key json on main.inputData

                    //string to key
                    String hashPublicKey = block.toHexString(block.getSHA256(publicKey));
                    String dataFull = date + data + hashPublicKey + signData;
                    String dataHash = block.toHexString(block.getSHA256(dataFull));

                    //set value json for making text json structure
                    main.jsonObject = new JSONObject();
                    main.jsonObject.put("date", date);
                    main.jsonObject.put("public-key", publicKey);
                    main.jsonObject.put("data", data);
                    main.jsonObject.put("sign-data", signData);
                    main.jsonObject.put("hash-data", dataHash);


                    String nameBlock = hashPublicKey + ".her";
                    main.file = new File(nameBlock);
                    boolean checkBlockFile = new File(System.getProperty("user. dir"), nameBlock).exists();
                    if (!checkBlockFile) {
                        if (block.verifySignature(block.stringToPublicKey(publicKey), data, signData)) {
                            System.out.println("finally yours data is true :) ");
                            //from hex to string we can call this = value X
                            // and then convert to hash256 for get value string to hash256
                            FileWriter fileWriter = new FileWriter(nameBlock);
                            main.inputData = main.jsonObject.toString();
                            fileWriter.write(main.inputData);
                            System.out.println("created at : " + main.file.getAbsolutePath());
                            fileWriter.close();
                            // Respond to client
                            out.println("""
                                    Data received successfully.
                                    finally yours data is true :)\s
                                    """);
                        } else {
                            System.out.println("yours key is false");
                        }
                    } else {
                        out.println("yours data is exist, please try another day");
                    }
                } else {
                    System.out.println("yours data not valid");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    public boolean isNodeRunning(String ip, int port) {
        try (Socket socket = new Socket(ip, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void checkActiveNodes(String[] serverAddresses, int port) {
        for (String ip : serverAddresses) {
            if (isNodeRunning(ip, port)) {
                System.out.println("Node " + ip + " is active.");
            } else {
                System.out.println("Node " + ip + " is not active.");
            }
        }
    }
}
