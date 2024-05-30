package org.example.user;

import org.example.Main;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class KeyPairGenerator {
    Main main = new Main();

    public void setKey() {
        try {
            java.security.KeyPairGenerator keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA");
            //set algorithm for cryptography
            keyPairGenerator.initialize(512);
            //set keysize to 512 Byte  = 4096 bit
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // Get private key
            main.privateKey = keyPair.getPrivate();
            String privateKeyStr = Base64.getEncoder().encodeToString(main.privateKey.getEncoded());
//            System.out.println("Private Key: " + privateKeyStr);

            // Get public key
            main.publicKey = keyPair.getPublic();
            String publicKeyStr = Base64.getEncoder().encodeToString(main.publicKey.getEncoded());
//            System.out.println("Public Key: " + publicKeyStr);

            main.jsonObject = new JSONObject();
            //
            main.jsonObject.put("private-key", privateKeyStr);
            main.jsonObject.put("public-key", publicKeyStr);
            main.inputData = main.jsonObject.toString();
            System.out.print("input your name : ");
            String nameFile = main.scanner.nextLine();
            String fullName = nameFile+"-key.her";
            FileWriter fileWriter = new FileWriter(fullName);
            fileWriter.write(main.inputData);
            main.file = new File(fullName);
            System.out.println("created at : "+main.file.getAbsolutePath());
            fileWriter.close();
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
    }
}
