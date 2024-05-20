package org.example;

import org.example.node.Node;
import org.example.user.KeyPairGenerator;
import org.example.user.User;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Scanner;

public class Main {

    public Scanner scanner = new Scanner(System.in);
    static KeyPairGenerator keyPairGenerator = new KeyPairGenerator();
    static User user = new User();
    static Node node = new Node();

    public static String data, publicKeyUser, privateKeyUser, date, inputData;
    public File file;
    public JSONObject jsonObject;
    public PublicKey publicKey;
    public PrivateKey privateKey;
    static public String nameFile;
    private boolean exit, valid;
    private int choice;

    private void menu(int choiceMenu){
        exit = false;
        valid = false;
        if (choiceMenu == 1) {
            while (!exit) {
                //if exit is true, not looping
                System.out.println("=== MENU USER ===");
                System.out.println("1. get-key");
                System.out.println("2. check-connection-node");
                System.out.println("0. exit");
                while (!valid) {
                    //if valid is true, not looping
                    System.out.print("choose option : ");
                    try {
                        choice = Integer.parseInt(scanner.nextLine());
                        valid = true;
                        switch (choice) {
                            case 1:
                                keyPairGenerator.setKey();
                                break;
                            case 2:
                                user.checkConnection();
                                break;
                            case 0:
                                exit = true;
                                break;
                            default:
                                System.out.println("your input not valid.");
                                break;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("the input entered is not a number, try again.");
                    }
                }
                valid = false;
                System.out.println();
            }
        } else {
            exit = false;
            valid = false;
            while (!exit) {
                //if exit is true, not looping
                System.out.println("=== MENU NODE ===");
                System.out.println("1. running-node");
                System.out.println("0. exit");
                while (!valid) {
                    //if valid is true, not looping
                    System.out.print("choose option : ");
                    try {
                        choice = Integer.parseInt(scanner.nextLine());
                        valid = true;
                        switch (choice) {
                            case 1:
                                node.runningNode();
                                break;
                            case 0:
                                exit = true;
                                break;
                            default:
                                System.out.println("your input not valid.");
                                break;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("the input entered is not a number, try again.");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                valid = false;
                System.out.println();
            }
        }
        scanner.close();
        System.out.println("Thanks all");
    }

    public static void main(String[] args){
        Main main = new Main();
        if (args.length >= 1 && (args[0].equals("-u") || args[0].equals("--user"))) {
            main.menu(1);
        } else if (args.length >= 3 && (args[0].equals("-s") || args[0].equals("--send"))) {
            nameFile = String.valueOf(args[1]);
            data = String.valueOf(args[2]);
            user.sendingData(nameFile, data);
            //sending data to node
        } else if (args.length >= 1 && (args[0].equals("-n") || args[0].equals("--node"))) {
            main.menu(0);
        } else if (args.length == 1 && (args[0].equals("-h") || args[0].equals("--help"))) {
            System.out.println("blokceng (version 1.0, revision 1)");
            System.out.println("Usage:\n" + " blokceng [OPTIONS]...[VALUES]\t\n" + "  -u, --user    become a user.\n" + "  -s, --send [key] ['data']     send data to node.\n" + "  -n, --node     become a node.\n");
        } else {
            System.out.println("blokceng: missing operand\n" + "Try 'blokceng -h or --help' for more information.");
        }

    }
}