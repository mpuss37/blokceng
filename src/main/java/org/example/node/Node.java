package org.example.node;

import org.example.Main;
import org.example.data.Block;
import org.example.user.User;
import org.json.JSONObject;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.text.ParseException;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Node {
    Main main = new Main();
    Block block = new Block();
    User user = new User();

    ServerSocket serverSocket = null;
    Socket clientSocket = null;
    InetAddress inetAddress;

    private String data;
    int portNumber = 8080;

    public void startBlockScheduler() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable blockTask = () -> {
        };
        scheduler.scheduleAtFixedRate(blockTask, 1, 1, TimeUnit.MINUTES);
    }

    public void runningNode() throws IOException, ParseException {
        System.out.println("recommended : 8080 (default) / 443 (root)");
        System.out.print("input your port : ");
        portNumber = main.scanner.nextInt();
        if (portNumber != 443 && portNumber != 8080) {
            System.out.println("false your port");
            portNumber = 8080;
        }
        inetAddress = InetAddress.getLocalHost();
        serverSocket = new ServerSocket(portNumber);
        System.out.println("Server running at: " + inetAddress.getHostAddress() + " port : " + portNumber);
        System.out.println("running : "+user.getDate());
//        System.out.println("block created : "+block.checkTime(block.getTimeNow()));
        //port for server
        while (true) {
            try {
                clientSocket = serverSocket.accept();
                System.out.println("client connected: " + clientSocket.getInetAddress());
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Read data from client
                Main.inputData = in.readLine();
                if (Main.inputData != null) {
                    main.jsonObject = new JSONObject(Main.inputData);
                    //making new object for make json structure
                    String date = main.jsonObject.getString("date");
                    data = main.jsonObject.getString("data");
                    String publicKey = main.jsonObject.getString("public-key");
                    String privateKey = main.jsonObject.getString("private-key");
                    //get value based on key json on main.inputData

                    String hashPublicKey = block.toHexString(block.getSHA256(publicKey));
                    String hashPrivateKey = block.toHexString(block.getSHA256(privateKey));
                    //string to key

                    main.jsonObject.put("public-key", hashPublicKey);
                    main.jsonObject.put("private-key", hashPrivateKey);
                    //set value json for making text json structure
                    String signedStr = block.signData(block.stringToPrivateKey(privateKey), data);
                    String nameBlock = hashPublicKey + ".txt";
                    main.file = new File(nameBlock);
                    boolean checkBlockFile = new File(System.getProperty("user. dir"), nameBlock).exists();
                    if (!checkBlockFile) {
                        if (block.verifySignature(block.stringToPublicKey(publicKey), data, signedStr)) {
                            System.out.println("finally yours data is true :) ");
                            //from hex to string we can call this = value X
                            // and then convert to hash256 for get value string to hash256
                            FileWriter fileWriter = new FileWriter(nameBlock);
                            Main.inputData = main.jsonObject.toString();
                            fileWriter.write(Main.inputData);
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

    public boolean validationData(String publicKeyy, String privateKeyy) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        // Sign data
        data = publicKeyy + privateKeyy;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(main.privateKey);
        signature.update(data.getBytes());
        byte[] signedData = signature.sign();
        String signedDataStr = Base64.getEncoder().encodeToString(signedData);
        System.out.println("Signed Data: " + signedDataStr);

        // Verify signature
        Signature verifySignature = Signature.getInstance("SHA256withRSA");
        verifySignature.initVerify(main.publicKey);
        verifySignature.update(data.getBytes());
        boolean isValid = verifySignature.verify(signedData);
        System.out.println("Signature verification result: " + isValid);
        return isValid;
    }
}
