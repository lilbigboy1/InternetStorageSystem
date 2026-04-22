/*
 * server.java
 * CIS4930 - Internet Storage Systems, Spring 2026
 * PA2: File Transfer Server
 */

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class server {

    private static final String GREETING_MESSAGE = "Hello!";
    private static final String DISCONNECT_COMMAND = "bye";
    private static final String DISCONNECT_RESPONSE = "disconnected";
    private static final String FILE_NOT_FOUND = "File not found";

    // Folder where files are stored on server
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

        File storageDir = new File(STORAGE_FOLDER);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
            System.out.println("Created storage folder: " + storageDir.getAbsolutePath());
        }
        System.out.println("Serving files from: " + storageDir.getAbsolutePath());

        //create thread pool to service concurrent clients cached thread pool will create new threads as needed
        ExecutorService executor = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port + "...");

            //main accept loop --> accept connections and hand to the thread pool
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client connected from " + clientSocket.getInetAddress());

                    //submit handler task, handler closes the socket
                    executor.submit(new ClientHandler(clientSocket, storageDir));
                } catch (IOException e) {
                    System.err.println("Error while accepting client connection: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("Could not listen on port: " + e.getMessage());
        } finally {
            //attempt graceful shutdown if the server ever exits the loop
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ie) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // handles a single client connection in its own thread.
    //flow = 
    //send greeting message
    //read client commands (single filename, SEND/bye)
    //for SEND, send batch of 10 files in a client-specific random order
    //for filename requests, send single file
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final File storageDir;

        ClientHandler(Socket socket, File storageDir) {
            this.socket = socket;
            this.storageDir = storageDir;
        }
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    OutputStream rawOut = new BufferedOutputStream(socket.getOutputStream())
            ) {
                //initial greeting
                sendLine(rawOut, GREETING_MESSAGE);

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    inputLine = inputLine.trim();
                    System.out.println("[" + socket.getInetAddress() + "] Received: " + inputLine);

                    if (DISCONNECT_COMMAND.equals(inputLine)) {
                        //reply and close this client handler, server process continues
                        sendLine(rawOut, DISCONNECT_RESPONSE);
                        break;
                    }

                    if ("SEND".equalsIgnoreCase(inputLine)) {
                        //client requested batch send
                        //client immediately follows with sequence line
                        //client sends nothing additional, server generates random ordering
                        List<Integer> sequence = null;
                        //if client has immediate line available, read and try to parse as sequence
                        if (in.ready()) {
                            String maybeSeq = in.readLine();
                            if (maybeSeq != null) {
                                maybeSeq = maybeSeq.trim();
                                if (maybeSeq.toUpperCase().startsWith("SEQ")) {
                                    sequence = parseSequenceLine(maybeSeq.substring(3).trim());
                                } else {
                                    //not sequence line, treat it as a filename request
                                    //process normally by putting it back into the flow
                                    //processing this line as a filename request
                                    processSingleFileRequest(maybeSeq, rawOut);
                                    continue;
                                }
                            }
                        }

                        processBatchSend(sequence, rawOut);
                        //after batch send, continue to listen for further commands from same client
                        continue;
                    }

                    //treat input line as a filename request
                    processSingleFileRequest(inputLine, rawOut);
                }

            } catch (IOException e) {
                System.err.println("Error while communicating with client: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignore) {
                }
                System.out.println("Connection closed: " + socket.getInetAddress());
            }
        }
        //process a single filename request 
        private void processSingleFileRequest(String requestedName, OutputStream rawOut) throws IOException {
            File requestedFile = resolveSafeFile(storageDir, requestedName);
            if (requestedFile == null || !requestedFile.exists() || !requestedFile.isFile()) {
                System.out.println("File not found: " + new File(storageDir, requestedName).getAbsolutePath());
                sendLine(rawOut, FILE_NOT_FOUND);
                return;
            }

            try {
                long fileSize = requestedFile.length();
                System.out.println("Sending file: " + requestedFile.getName() + " (" + fileSize + " bytes)");

                //backwards-compatible header = SENDING <bytes>
                sendLine(rawOut, "SENDING " + fileSize);
                sendFileBytes(rawOut, requestedFile);

                System.out.println("File sent successfully: " + requestedFile.getName());
            } catch (IOException e) {
                System.err.println("Error sending file: " + e.getMessage());
                sendLine(rawOut, "ERROR: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Unexpected error: " + e.getMessage());
                sendLine(rawOut, "ERROR: " + e.getMessage());
            }
        }

        //process batch send of 10 files, if provided sequence is null or inv
        //a random ordering will be generated
        //files are selected from the storageDir and only regular files are considered 
        //the server sends for each file a header followed immediately by raw bytes
        private void processBatchSend(List<Integer> sequence, OutputStream rawOut) throws IOException {
            //collect input files 
            File[] allFiles = storageDir.listFiles();
            if (allFiles == null) {
                sendLine(rawOut, "ERROR: storage folder inaccessible");
                return;
            }

            List<File> candidates = new ArrayList<>();
            for (File f : allFiles) {
                if (f != null && f.isFile()) {
                    candidates.add(f);
                }
            }

            //sort deterministically so indexing is stable across runs
            Collections.sort(candidates, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

            if (candidates.size() < 10) {
                sendLine(rawOut, "ERROR: need at least 10 files in storage folder");
                return;
            }

            //limit to first 10 files for the pool; client sequences are numbers 1..10
            List<File> pool = new ArrayList<>(candidates.subList(0, 10));

            //if sequence is null or invalid, generate random permutation
            List<Integer> order;
            if (sequence == null || sequence.size() != 10) {
                order = new ArrayList<>();
                for (int i = 0; i < 10; i++) order.add(i);
                Collections.shuffle(order, new Random());
            } else {
                //convert 1-based client indices to 0-based and validate
                order = new ArrayList<>();
                for (int v : sequence) {
                    int idx = v - 1;
                    if (idx < 0 || idx >= 10) {
                        // invalid index: fall back to random order
                        order = new ArrayList<>();
                        for (int i = 0; i < 10; i++) order.add(i);
                        Collections.shuffle(order, new Random());
                        break;
                    }
                    order.add(idx);
                }
            }

            //send files in determined order
            for (int idx : order) {
                File f = pool.get(idx);
                long fileSize = f.length();
                System.out.println("[" + socket.getInetAddress() + "] Batch sending: " + f.getName() + " (" + fileSize + " bytes)");
                //header includes filename so the client can name the received file appropriately
                sendLine(rawOut, "SENDING " + fileSize + " " + f.getName());
                sendFileBytes(rawOut, f);
            }

            System.out.println("[" + socket.getInetAddress() + "] Batch send complete.");
        }

        //parse a sequence line into a list of integers
        //accepts comma or space separated numbers 
        //returns null on parse failure
        private List<Integer> parseSequenceLine(String s) {
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;
            String[] parts = s.split("[ ,]+");
            List<Integer> out = new ArrayList<>();
            try {
                for (String p : parts) {
                    if (p.isBlank()) continue;
                    out.add(Integer.parseInt(p.trim()));
                }
            } catch (NumberFormatException e) {
                return null;
            }
            return out;
        }
    }

    private static void sendLine(OutputStream out, String line) throws IOException {
        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        out.write(bytes);
        out.flush();
    }

    private static void sendFileBytes(OutputStream out, File file) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        try (InputStream fis = new BufferedInputStream(new FileInputStream(file))) {
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        }
    }

    //prevents requests like "..\\..\\secret" from escaping STORAGE_FOLDER
    //returns null if resolved path is outside the storage directory
    private static File resolveSafeFile(File storageDir, String requestedName) {
        try {
            //only allow simple filenames to avoid ambiguity
            if (requestedName.contains("/") || requestedName.contains("\\") || requestedName.contains("..")) {
                return null;
            }

            File candidate = new File(storageDir, requestedName);
            if (!candidate.exists()) {
                File ci = findCaseInsensitive(storageDir, requestedName);
                if (ci != null) {
                    candidate = ci;
                }
            }
            String base = storageDir.getCanonicalPath() + File.separator;
            String target = candidate.getCanonicalPath();
            if (!target.startsWith(base)) {
                return null;
            }
            return candidate;
        } catch (IOException e) {
            return null;
        }
    }

    private static File findCaseInsensitive(File storageDir, String requestedName) {
        File[] files = storageDir.listFiles();
        if (files == null) {
            return null;
        }
        for (File f : files) {
            if (f != null && f.isFile() && f.getName().equalsIgnoreCase(requestedName)) {
                return f;
            }
        }
        return null;
    }
}

