package org.example.data;

import org.example.user.User;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static java.nio.file.Files.createDirectories;

public class Block {
    User user = new User();
    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    Path path, directory;
    File file, file2;
    String timeNow, timeFinal;
    LocalTime localTime1, localTime2;

    public byte[] getSHA256(String input) throws NoSuchAlgorithmException {
        // Static getInstance method is called with hashing SHA
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        // digest() method called
        // to calculate message digest of an input
        // and return array of byte
        return md.digest(input.getBytes(StandardCharsets.UTF_8));
    }

    public String toHexString(byte[] hash) {
        // Convert byte array into signum representation
        BigInteger number = new BigInteger(1, hash);

        // Convert message digest into hex value
        StringBuilder hexString = new StringBuilder(number.toString(16));

        // Pad with leading zeros
        while (hexString.length() < 64) {
            hexString.insert(0, '0');
        }

        return hexString.toString();
    }

    public String setRegionLocal() {
        TimeZone timeZone = TimeZone.getDefault();
        String date = (" " + timeZone.getDisplayName());
        return date;
    }

    public String getTimeNow() {
        LocalDateTime now = LocalDateTime.now();
        timeNow = (dateTimeFormatter.format(now));
        return timeNow;
    }

    public String checkTime(String timeNow) {
        Date date = null;
        try {
            date = timeFormat.parse(timeNow);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.MINUTE, 1);
        timeFinal = timeFormat.format(cal.getTime());
        return timeFinal;
    }

    public String createBlock(File file) {
        int blockTotal = 1;
        timeNow = String.valueOf(getTimeNow());
        timeFinal = checkTime(timeNow);
        localTime1 = LocalTime.parse(timeNow);
        localTime2 = LocalTime.parse(timeFinal);
        if (localTime2.isAfter(localTime1)) {
            System.out.println("berhasil");
            do {
                new File("block" + blockTotal).mkdirs();
                blockTotal++;
            } while (file.exists());
            try {
                Files.createDirectories(file.toPath());
                System.out.println("created block : " + file.getAbsolutePath());
            } catch (IOException e) {
                System.out.println("block is exist");
            }
        }
        return file.getAbsolutePath();
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

    public PublicKey stringToPublicKey(String publicKeyStr) {
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

    public PrivateKey stringToPrivateKey(String privateKeyStr) {
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
}
