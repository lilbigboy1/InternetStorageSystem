/*
 * server.java
 * CIS4930 - Internet Storage Systems, Spring 2026
 * PA1: Capital Converter
 *
 * A TCP server that listens on a specified port, accepts a client
 * connection, and converts alphabetic strings to uppercase. Returns
 * an error message for non-alphabet inputs and handles graceful
 * termination when the client sends "bye"
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class server {

    private static final String GREETING_MESSAGE = "Hello!";
    private static final String DISCONNECT_COMMAND = "bye";
    private static final String DISCONNECT_RESPONSE = "disconnected";
    private static final String ERROR_PREFIX = "ERROR:";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java server [port_number]");
            System.exit(1);
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Port number must be an integer.");
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port + "...");

            // Accept a single client connection (sufficient for this assignment)
            try (Socket clientSocket = serverSocket.accept();
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

                System.out.println("Client connected from " + clientSocket.getInetAddress());

                // Send initial greeting
                out.println(GREETING_MESSAGE);

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Received from client: " + inputLine);

                    if (DISCONNECT_COMMAND.equals(inputLine)) {
                        out.println(DISCONNECT_RESPONSE);
                        break;
                    }
                    
                    //fixed for A-Z as well
                    if (!isAllAlphabets(inputLine)) {
                        out.println(ERROR_PREFIX + " input must contain only alphabets (a-z, A-Z). Please retransmit.");
                        continue;
                    }

                    String upper = inputLine.toUpperCase();
                    out.println(upper);
                }
            } catch (IOException e) {
                System.err.println("Error while communicating with client: " + e.getMessage());
            }

            System.out.println("Server shutting down.");
        } catch (IOException e) {
            System.err.println("Could not listen on port: " + e.getMessage());
        }
    }

    //check upper and lower case alphabets
    private static boolean isAllAlphabets(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            //inclusive alphabet params
            if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) {
                return false;
            }
        }
        return true;
    }
}

