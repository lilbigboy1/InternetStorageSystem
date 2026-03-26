/*
 * client.java
 * CIS4930 - Internet Storage Systems, Spring 2026
 * PA1: Capital Converter
 *
 * A TCP client that connects to the Capital Converter server,
 * sends user-input strings, receives capitalized responses, and
 * measures round-trip time (RTT) in milliseconds. Prints RTT
 * statistics (min, mean, max, std dev) upon termination
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class client {

    private static final String DISCONNECT_COMMAND = "bye";
    private static final String DISCONNECT_RESPONSE = "disconnected";
    private static final String ERROR_PREFIX = "ERROR:";

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java client [serverURL] [port_number]");
            System.exit(1);
        }

        String hostName = args[0];
        int portNumber;

        try {
            portNumber = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Port number must be an integer.");
            return;
        }

        try (Socket socket = new Socket(hostName, portNumber);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            // Receive and print greeting from server
            String greeting = in.readLine();
            if (greeting != null) {
                System.out.println(greeting);
            }

            List<Double> rtts = new ArrayList<>();

            while (true) {
                System.out.print("Enter an alphabet string (or 'bye' to quit): ");
                String userInput = scanner.nextLine();

                long startTime = System.nanoTime();
                out.println(userInput);

                String response = in.readLine();
                long endTime = System.nanoTime();

                if (response == null) {
                    System.out.println("Server closed the connection.");
                    break;
                }

                double rttMillis = (endTime - startTime) / 1_000_000.0;

                if (DISCONNECT_COMMAND.equals(userInput)) {
                    System.out.println(response);
                    System.out.println("exit");
                    break;
                } else if (response.startsWith(ERROR_PREFIX)) {
                    System.out.println(response);
                    // Do not count this RTT in statistics, as it is an error case
                } else {
                    System.out.println("Capitalized response: " + response);
                    rtts.add(rttMillis);
                }
            }

            if (!rtts.isEmpty()) {
                printStatistics(rtts);
            } else {
                System.out.println("No successful round-trip measurements were recorded.");
            }

        } catch (UnknownHostException e) {
            System.err.println("Unknown host " + hostName + ": " + e.getMessage());
        } catch (IOException e) {
            System.err.println("I/O error while communicating with " + hostName + ": " + e.getMessage());
        }
    }

    private static void printStatistics(List<Double> rtts) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0.0;

        for (double rtt : rtts) {
            if (rtt < min) {
                min = rtt;
            }
            if (rtt > max) {
                max = rtt;
            }
            sum += rtt;
        }

        double mean = sum / rtts.size();

        double varianceSum = 0.0;
        for (double rtt : rtts) {
            double diff = rtt - mean;
            varianceSum += diff * diff;
        }
        double variance = varianceSum / rtts.size();
        double stdDev = Math.sqrt(variance);

        System.out.println();
        System.out.println("Round-trip time statistics (ms) over " + rtts.size() + " successful runs:");
        System.out.println("Minimum: " + formatDouble(min));
        System.out.println("Mean   : " + formatDouble(mean));
        System.out.println("Maximum: " + formatDouble(max));
        System.out.println("Std Dev: " + formatDouble(stdDev));
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.3f", value);
    }
}

