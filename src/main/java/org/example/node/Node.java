package org.example.node;

import org.example.Main;
import org.json.JSONObject;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class Node {
    Main main = new Main();

    ServerSocket serverSocket = null;
    Socket clientSocket = null;
    InetAddress inetAddress;

    private String data, publicKey, privateKey, hashPublicKey, hashPrivateKey, date;
    int portNumber = 8080;

    public void runningNode() throws IOException {
        System.out.println("recomended : 8080 (default) / 443 (root)");
        System.out.print("input your port : ");
        portNumber = main.scanner.nextInt();
        if (portNumber != 443 && portNumber != 8080) {
            System.out.println("false your port");
            portNumber = 8080;
        }
        inetAddress = InetAddress.getLocalHost();
        serverSocket = new ServerSocket(portNumber);
        System.out.println("Server running at: " + inetAddress.getHostAddress() + " port : " + portNumber);
        //port for server
        while (true) {
            try {
                clientSocket = serverSocket.accept();
                System.out.println("client conected: " + clientSocket.getInetAddress());
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Read data from client
                main.inputData = in.readLine();
                if (main.inputData != null) {
                    main.jsonObject = new JSONObject(main.inputData);
                    //making new object for make json structure
                    date = main.jsonObject.getString("date");
                    data = main.jsonObject.getString("data");
                    publicKey = main.jsonObject.getString("public-key");
                    privateKey = main.jsonObject.getString("private-key");
                    //get value based on key json on main.inputData

                    hashPublicKey = toHexString(getSHA256(publicKey));
                    hashPrivateKey = toHexString(getSHA256(privateKey));
                    //string to key

                    main.jsonObject.put("public-key", hashPublicKey);
                    main.jsonObject.put("private-key", hashPrivateKey);
                    //set value json for making text json structure
                    String signedStr = signData(stringToPrivateKey(privateKey), data);
                    String nameBlock = hashPublicKey+".txt";
                    boolean checkBlockFile = new File(System. getProperty("user. dir"), nameBlock).exists();
                    if (checkBlockFile == false){
                    if (verifySignature(stringToPublicKey(publicKey), data, signedStr)) {
                        System.out.println("finnaly yours data is true :) ");
                        //from hex to string we can call this = valueX
                        //valueX convert to hash256 for get value string to hash256
                        FileWriter fileWriter = new FileWriter(nameBlock);
                        main.inputData = main.jsonObject.toString();
                        fileWriter.write(main.inputData);
                        main.file = new File(nameBlock);
                        System.out.println("created at : " + main.file.getAbsolutePath());
                        fileWriter.close();
//                System.out.println("Received data: " + inputData);

                        // Respond to client
                        out.println("Data received successfully."+"\nfinnaly yours data is true :) \n");
                    }else {
                        System.out.println("yours key is false");
                    }
                    }else {
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

    public static byte[] getSHA256(String input) throws NoSuchAlgorithmException
    {
        // Static getInstance method is called with hashing SHA
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        // digest() method called
        // to calculate message digest of an input
        // and return array of byte
        return md.digest(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String toHexString(byte[] hash)
    {
        // Convert byte array into signum representation
        BigInteger number = new BigInteger(1, hash);

        // Convert message digest into hex value
        StringBuilder hexString = new StringBuilder(number.toString(16));

        // Pad with leading zeros
        while (hexString.length() < 64)
        {
            hexString.insert(0, '0');
        }

        return hexString.toString();
    }

    public String signData(PrivateKey privateKey, String data) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes());
        byte[] signedData = signature.sign();
        return Base64.getEncoder().encodeToString(signedData);
    }

    public boolean verifySignature(PublicKey publicKey, String data, String signedDataStr) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(data.getBytes());
        byte[] signedData = Base64.getDecoder().decode(signedDataStr);
        return signature.verify(signedData);
    }

    public PublicKey stringToPublicKey(String publicKeyStr){
        byte[] byteKey = Base64.getDecoder().decode(publicKeyStr);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(byteKey);
        KeyFactory keyFactory = null;
        try {
            keyFactory = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try {
            return keyFactory.generatePublic(spec);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    public PrivateKey stringToPrivateKey(String privateKeyStr){
        byte[] byteKey = Base64.getDecoder().decode(privateKeyStr);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(byteKey);
        KeyFactory keyFactory = null;
        try {
            keyFactory = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try {
            return keyFactory.generatePrivate(spec);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
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
