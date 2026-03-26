/*
 * server.java
 * CIS4930 - Internet Storage Systems, Spring 2026
 * PA2: File Transfer Server
 *
 * A TCP server that listens on a specified port, accepts a client
 * connection, and serves BMP files from a predetermined storage folder.
 * The server sends the requested file to the client if it exists, or
 * responds with "File not found" if it does not. Handles graceful
 * termination when the client sends "bye"
 */

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class server {

    private static final String GREETING_MESSAGE = "Hello!";
    private static final String DISCONNECT_COMMAND = "bye";
    private static final String DISCONNECT_RESPONSE = "disconnected";
    private static final String FILE_NOT_FOUND = "File not found";

    //folder where BMP files are stored on server
    private static final String STORAGE_FOLDER = "server_files";

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

        //ensure storage folder exists
        File storageDir = new File(STORAGE_FOLDER);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
            System.out.println("Created storage folder: " + storageDir.getAbsolutePath());
        }
        System.out.println("Serving files from: " + storageDir.getAbsolutePath());

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port + "...");

            //accept single client connection
            try (Socket clientSocket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter textOut = new PrintWriter(clientSocket.getOutputStream(), true);
                 DataOutputStream dataOut = new DataOutputStream(clientSocket.getOutputStream())) {

                System.out.println("Client connected from " + clientSocket.getInetAddress());

                // Send initial greeting
                textOut.println(GREETING_MESSAGE);

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Received from client: " + inputLine);

                    //graceful termination
                    if (DISCONNECT_COMMAND.equals(inputLine)) {
                        textOut.println(DISCONNECT_RESPONSE);
                        break;
                    }

                    // Look for the requested file in the predetermined folder
                    File requestedFile = new File(STORAGE_FOLDER, inputLine);

                    if (!requestedFile.exists() || !requestedFile.isFile()) {
                        System.out.println("File not found: " + requestedFile.getAbsolutePath());
                        textOut.println(FILE_NOT_FOUND);
                        continue;
                    }

                    //if file exist, send to client
                    try {
                        long fileSize = requestedFile.length();
                        System.out.println("Sending file: " + requestedFile.getName()
                                + " (" + fileSize + " bytes)");

                        //send header line of file size so client knows how many bytes to read
                        textOut.println("SENDING " + fileSize);
                        //flush PrintWriter before writing raw bytes
                        textOut.flush();

                        //read and send file bytes
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        try (FileInputStream fis = new FileInputStream(requestedFile)) {
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                dataOut.write(buffer, 0, bytesRead);
                            }
                            dataOut.flush();
                        }

                        System.out.println("File sent successfully: " + requestedFile.getName());

                    } catch (IOException e) {
                        System.err.println("Error sending file: " + e.getMessage());
                        textOut.println("ERROR: " + e.getMessage());
                    } catch (Exception e) {
                        System.err.println("Unexpected error: " + e.getMessage());
                        textOut.println("ERROR: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("Error while communicating with client: " + e.getMessage());
            }

            System.out.println("Server shutting down.");
        } catch (IOException e) {
            System.err.println("Could not listen on port: " + e.getMessage());
        }
    }
}

