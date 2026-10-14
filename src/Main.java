import java.io.File;
import java.util.Scanner;

/**
 * Main: Interactive CLI entry point and menu for the TCP vs. UDP File Transfer Project.
 *
 * Designed for college students and lab instructors:
 * - Direct command-line argument dispatch for non-interactive automation.
 * - Interactive console menu for viva demonstrations and step-by-step testing.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0) {
            handleCommandLine(args);
            return;
        }

        // Interactive Menu Mode
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n==========================================================================");
            System.out.println("        COMPUTER NETWORKS LAB: TCP VS. UDP FILE TRANSFER SYSTEM           ");
            System.out.println("==========================================================================");
            System.out.println("  1. Run Full Automated Benchmark (Generates files, CSVs & Graphs)");
            System.out.println("  2. Start TCP Server (Listening for incoming file)");
            System.out.println("  3. Run TCP Client (Send a file to TCP Server)");
            System.out.println("  4. Start UDP Server (Listening for datagram packets)");
            System.out.println("  5. Run UDP Client (Send a file to UDP Server)");
            System.out.println("  6. Generate Deterministic Test Files (100 KB, 1 MB, 5 MB, 10 MB)");
            System.out.println("  7. Re-generate Plots from Existing CSV Data");
            System.out.println("  8. Exit");
            System.out.println("==========================================================================");
            System.out.print("Select an option [1-8]: ");

            String input = scanner.nextLine().trim();
            System.out.println();

            switch (input) {
                case "1":
                    BenchmarkRunner.main(new String[0]);
                    break;
                case "2":
                    System.out.print("Enter TCP Port (Press Enter for default 8888): ");
                    String tcpPortStr = scanner.nextLine().trim();
                    int tcpPort = tcpPortStr.isEmpty() ? TCPServer.DEFAULT_PORT : Integer.parseInt(tcpPortStr);
                    TCPServer.main(new String[]{String.valueOf(tcpPort)});
                    break;
                case "3":
                    System.out.print("Enter path of file to send: ");
                    String tcpFile = scanner.nextLine().trim();
                    System.out.print("Enter TCP Server Host (Press Enter for localhost): ");
                    String tcpHost = scanner.nextLine().trim();
                    if (tcpHost.isEmpty()) tcpHost = "localhost";
                    System.out.print("Enter TCP Server Port (Press Enter for 8888): ");
                    String tcpP = scanner.nextLine().trim();
                    if (tcpP.isEmpty()) tcpP = "8888";
                    TCPClient.main(new String[]{tcpFile, tcpHost, tcpP});
                    break;
                case "4":
                    System.out.print("Enter UDP Port (Press Enter for default 8889): ");
                    String udpPortStr = scanner.nextLine().trim();
                    int udpPort = udpPortStr.isEmpty() ? UDPServer.DEFAULT_PORT : Integer.parseInt(udpPortStr);
                    UDPServer.main(new String[]{String.valueOf(udpPort)});
                    break;
                case "5":
                    System.out.print("Enter path of file to send: ");
                    String udpFile = scanner.nextLine().trim();
                    System.out.print("Enter UDP Server Host (Press Enter for localhost): ");
                    String udpHost = scanner.nextLine().trim();
                    if (udpHost.isEmpty()) udpHost = "localhost";
                    System.out.print("Enter UDP Server Port (Press Enter for 8889): ");
                    String udpP = scanner.nextLine().trim();
                    if (udpP.isEmpty()) udpP = "8889";
                    System.out.print("Enter Inter-Packet Delay in µs (Press Enter for 0 µs): ");
                    String delayStr = scanner.nextLine().trim();
                    if (delayStr.isEmpty()) delayStr = "0";
                    UDPClient.main(new String[]{udpFile, udpHost, udpP, delayStr});
                    break;
                case "6":
                    try {
                        BenchmarkRunner.ensureDirectories();
                        BenchmarkRunner.generateTestFiles();
                        System.out.println("[Success] Test files verified in 'test-files/' folder.");
                    } catch (Exception e) {
                        System.err.println("Error generating files: " + e.getMessage());
                    }
                    break;
                case "7":
                    BenchmarkRunner.generatePlots();
                    break;
                case "8":
                    System.out.println("Exiting. Thank you!");
                    return;
                default:
                    System.out.println("Invalid option. Please choose between 1 and 8.");
            }
        }
    }

    private static void handleCommandLine(String[] args) {
        String command = args[0].toLowerCase();
        String[] restArgs = new String[args.length - 1];
        System.arraycopy(args, 1, restArgs, 0, restArgs.length);

        switch (command) {
            case "benchmark":
            case "bench":
                BenchmarkRunner.main(restArgs);
                break;
            case "tcpserver":
                TCPServer.main(restArgs);
                break;
            case "tcpclient":
                TCPClient.main(restArgs);
                break;
            case "udpserver":
                UDPServer.main(restArgs);
                break;
            case "udpclient":
                UDPClient.main(restArgs);
                break;
            case "generate":
                try {
                    BenchmarkRunner.ensureDirectories();
                    BenchmarkRunner.generateTestFiles();
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
                break;
            case "plots":
                BenchmarkRunner.generatePlots();
                break;
            default:
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java Main                               (Interactive menu)");
        System.out.println("  java Main benchmark                     (Run automated benchmark suite)");
        System.out.println("  java Main tcpserver [port]              (Run TCP Server)");
        System.out.println("  java Main tcpclient <file> [host] [port](Run TCP Client)");
        System.out.println("  java Main udpserver [port]              (Run UDP Server)");
        System.out.println("  java Main udpclient <file> [host] [port] [delayMicros] (Run UDP Client)");
        System.out.println("  java Main generate                      (Generate test files)");
        System.out.println("  java Main plots                         (Generate graph plots)");
    }
}
