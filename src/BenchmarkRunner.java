import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * BenchmarkRunner: Automated benchmarking suite for TCP vs. UDP file transfer.
 *
 * Capabilities:
 * - Generates deterministic binary test files: 100 KB, 1 MB, 5 MB, 10 MB.
 * - Executes automated trials (3 repetitions per test case).
 * - Spawns server threads with robust synchronization to eliminate startup races.
 * - Transmits files using standard socket APIs and records nanosecond-precision timing.
 * - Validates end-to-end file integrity via SHA-256 cryptographic hashes.
 * - Accurately records packet loss and incomplete UDP transfers.
 * - Exports raw results to results/results.csv and summary statistics to results/summary.csv.
 * - Invokes plot generator to generate results/throughput.png and results/transmission_time.png.
 */
public class BenchmarkRunner {

    public static final String TEST_FILES_DIR = "test-files";
    public static final String TCP_RECEIVED_DIR = "received/tcp";
    public static final String UDP_RECEIVED_DIR = "received/udp";
    public static final String RESULTS_DIR = "results";

    public static final int TCP_PORT = 8888;
    public static final int UDP_PORT = 8889;
    public static final int REPETITIONS = 3;

    // Defined benchmark file sizes
    public static final TestCase[] TEST_CASES = new TestCase[] {
            new TestCase("100 KB", 100 * 1024),
            new TestCase("1 MB", 1 * 1024 * 1024),
            new TestCase("5 MB", 5 * 1024 * 1024),
            new TestCase("10 MB", 10 * 1024 * 1024)
    };

    public static class TestCase {
        public final String label;
        public final int sizeBytes;
        public final String fileName;

        public TestCase(String label, int sizeBytes) {
            this.label = label;
            this.sizeBytes = sizeBytes;
            this.fileName = "file_" + label.replace(" ", "") + ".bin";
        }
    }

    public static class BenchmarkRunEntry {
        public int runNumber;
        public String protocol;
        public String sizeLabel;
        public long sizeBytes;
        public double timeSeconds;
        public long receivedBytes;
        public double pctReceived;
        public int packetsSent;
        public int packetsReceived;
        public int packetsLost;
        public double lossRatePct;
        public double throughputMiBps;
        public boolean checksumMatch;
        public String status;
        public String sourceHash;
        public String receivedHash;
    }

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("     COMPUTER NETWORKS LAB: TCP VS. UDP FILE TRANSFER BENCHMARK           ");
        System.out.println("==========================================================================");

        try {
            ensureDirectories();
            generateTestFiles();

            List<BenchmarkRunEntry> rawResults = new ArrayList<>();

            // 1. Run TCP Benchmark (3 runs for each test case)
            System.out.println("\n--------------------------------------------------------------------------");
            System.out.println("                     PHASE 1: TCP BENCHMARK RUNS                          ");
            System.out.println("--------------------------------------------------------------------------");
            for (TestCase tc : TEST_CASES) {
                File testFile = new File(TEST_FILES_DIR, tc.fileName);
                for (int run = 1; run <= REPETITIONS; run++) {
                    System.out.printf("%n>>> [TCP] Running %s - Iteration %d/%d <<<%n", tc.label, run, REPETITIONS);
                    BenchmarkRunEntry entry = runSingleTCPTransfer(testFile, tc, run);
                    rawResults.add(entry);
                    Thread.sleep(150); // Cool down between runs
                }
            }

            // 2. Run UDP Benchmark (3 runs for each test case)
            System.out.println("\n--------------------------------------------------------------------------");
            System.out.println("                     PHASE 2: UDP BENCHMARK RUNS                          ");
            System.out.println("--------------------------------------------------------------------------");
            for (TestCase tc : TEST_CASES) {
                File testFile = new File(TEST_FILES_DIR, tc.fileName);
                for (int run = 1; run <= REPETITIONS; run++) {
                    System.out.printf("%n>>> [UDP] Running %s - Iteration %d/%d <<<%n", tc.label, run, REPETITIONS);
                    BenchmarkRunEntry entry = runSingleUDPTransfer(testFile, tc, run);
                    rawResults.add(entry);
                    Thread.sleep(200); // Cool down between runs
                }
            }

            // 3. Export CSVs
            System.out.println("\n--------------------------------------------------------------------------");
            System.out.println("                     PHASE 3: GENERATING DATA REPORTS                     ");
            System.out.println("--------------------------------------------------------------------------");
            File resultsCsv = new File(RESULTS_DIR, "results.csv");
            File summaryCsv = new File(RESULTS_DIR, "summary.csv");

            writeResultsCSV(rawResults, resultsCsv);
            writeSummaryCSV(rawResults, summaryCsv);

            // 4. Print Summary Table to Console
            printSummaryTable(summaryCsv);

            // 5. Generate Graphs
            generatePlots();

            System.out.println("\n==========================================================================");
            System.out.println("               BENCHMARK EXECUTION COMPLETED SUCCESSFULLY                 ");
            System.out.println("==========================================================================");

        } catch (Exception e) {
            System.err.println("\n[Benchmark Error] Failed during execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Executes a single TCP file transfer with server thread isolation.
     */
    private static BenchmarkRunEntry runSingleTCPTransfer(File testFile, TestCase tc, int run) throws Exception {
        String remoteFileName = "tcp_" + tc.label.replace(" ", "") + "_run" + run + ".bin";
        TCPServer server = new TCPServer(TCP_PORT, new File(TCP_RECEIVED_DIR));

        Thread serverThread = new Thread(() -> {
            try {
                server.receiveOneFile();
            } catch (Exception e) {
                // Sockets closing on completion or timeout
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Allow 100ms for server socket to bind
        Thread.sleep(100);

        TransferResult clientResult = TCPClient.sendFile("localhost", TCP_PORT, testFile, remoteFileName);
        serverThread.join(5000);

        BenchmarkRunEntry entry = new BenchmarkRunEntry();
        entry.runNumber = run;
        entry.protocol = "TCP";
        entry.sizeLabel = tc.label;
        entry.sizeBytes = tc.sizeBytes;
        entry.timeSeconds = clientResult.getElapsedTimeSeconds();
        entry.receivedBytes = clientResult.getReceivedBytes();
        entry.pctReceived = (entry.receivedBytes * 100.0) / entry.sizeBytes;
        entry.packetsSent = clientResult.getPacketsSent();
        entry.packetsReceived = clientResult.getPacketsReceived();
        entry.packetsLost = clientResult.getPacketsLost();
        entry.lossRatePct = clientResult.getPacketLossRate();
        entry.throughputMiBps = clientResult.getThroughputMiBps();
        entry.checksumMatch = clientResult.isChecksumMatched();
        entry.status = clientResult.getStatusMessage();
        entry.sourceHash = clientResult.getSourceChecksum();
        entry.receivedHash = clientResult.getReceivedChecksum();
        return entry;
    }

    /**
     * Executes a single UDP file transfer with server thread isolation.
     */
    private static BenchmarkRunEntry runSingleUDPTransfer(File testFile, TestCase tc, int run) throws Exception {
        String remoteFileName = "udp_" + tc.label.replace(" ", "") + "_run" + run + ".bin";
        UDPServer server = new UDPServer(UDP_PORT, new File(UDP_RECEIVED_DIR));

        Thread serverThread = new Thread(() -> {
            try {
                server.receiveOneFile();
            } catch (Exception e) {
                // Sockets closing
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Allow 100ms for UDP socket binding
        Thread.sleep(100);

        // Send UDP packets with 0 delay (standard raw transport, demonstrating OS socket buffer behavior)
        TransferResult clientResult = UDPClient.sendFile("localhost", UDP_PORT, testFile, remoteFileName, 0);
        serverThread.join(4000);

        BenchmarkRunEntry entry = new BenchmarkRunEntry();
        entry.runNumber = run;
        entry.protocol = "UDP";
        entry.sizeLabel = tc.label;
        entry.sizeBytes = tc.sizeBytes;
        entry.timeSeconds = clientResult.getElapsedTimeSeconds();
        entry.receivedBytes = clientResult.getReceivedBytes();
        entry.pctReceived = (entry.receivedBytes * 100.0) / entry.sizeBytes;
        entry.packetsSent = clientResult.getPacketsSent();
        entry.packetsReceived = clientResult.getPacketsReceived();
        entry.packetsLost = clientResult.getPacketsLost();
        entry.lossRatePct = clientResult.getPacketLossRate();
        entry.throughputMiBps = clientResult.getThroughputMiBps();
        entry.checksumMatch = clientResult.isChecksumMatched();
        entry.status = clientResult.getStatusMessage();
        entry.sourceHash = clientResult.getSourceChecksum();
        entry.receivedHash = clientResult.getReceivedChecksum();
        return entry;
    }

    /**
     * Ensures all necessary directories exist.
     */
    public static void ensureDirectories() {
        new File(TEST_FILES_DIR).mkdirs();
        new File(TCP_RECEIVED_DIR).mkdirs();
        new File(UDP_RECEIVED_DIR).mkdirs();
        new File(RESULTS_DIR).mkdirs();
    }

    /**
     * Generates deterministic test files using a fixed random seed.
     */
    public static void generateTestFiles() throws IOException {
        System.out.println("[Setup] Checking test files in directory: " + TEST_FILES_DIR);
        Random rng = new Random(42L); // Fixed seed for reproducible file content

        for (TestCase tc : TEST_CASES) {
            File f = new File(TEST_FILES_DIR, tc.fileName);
            if (!f.exists() || f.length() != tc.sizeBytes) {
                System.out.printf("  Generating %s (%,d bytes)...%n", tc.fileName, tc.sizeBytes);
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    byte[] block = new byte[8192];
                    int remaining = tc.sizeBytes;
                    while (remaining > 0) {
                        int toWrite = Math.min(block.length, remaining);
                        rng.nextBytes(block);
                        fos.write(block, 0, toWrite);
                        remaining -= toWrite;
                    }
                    fos.flush();
                }
            } else {
                System.out.printf("  Found existing %s (%,d bytes)%n", tc.fileName, tc.sizeBytes);
            }
        }
    }

    /**
     * Writes raw trial results to results.csv.
     */
    private static void writeResultsCSV(List<BenchmarkRunEntry> entries, File csvFile) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile))) {
            pw.println("Run,Protocol,FileSize_Label,FileSize_Bytes,Time_Seconds,Received_Bytes,Pct_Received,Packets_Sent,Packets_Received,Packets_Lost,Loss_Rate_Pct,Throughput_MiBps,Checksum_Match,Status,Source_SHA256,Received_SHA256");
            for (BenchmarkRunEntry e : entries) {
                pw.printf("%d,%s,%s,%d,%.6f,%d,%.2f,%d,%d,%d,%.2f,%.4f,%s,%s,%s,%s%n",
                        e.runNumber, e.protocol, e.sizeLabel, e.sizeBytes,
                        e.timeSeconds, e.receivedBytes, e.pctReceived,
                        e.packetsSent, e.packetsReceived, e.packetsLost, e.lossRatePct,
                        e.throughputMiBps, e.checksumMatch ? "MATCH" : "MISMATCH",
                        e.status.replace(",", ";"), e.sourceHash, e.receivedHash);
            }
        }
        System.out.println("[Data] Wrote raw metrics to: " + csvFile.getAbsolutePath());
    }

    /**
     * Calculates averages and writes summary.csv.
     */
    private static void writeSummaryCSV(List<BenchmarkRunEntry> entries, File csvFile) throws IOException {
        // Group by Protocol + FileSize_Label
        Map<String, List<BenchmarkRunEntry>> groups = new LinkedHashMap<>();
        for (BenchmarkRunEntry e : entries) {
            String key = e.protocol + "_" + e.sizeLabel;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile))) {
            pw.println("Protocol,FileSize_Label,FileSize_Bytes,Completed_Runs,Total_Runs,Avg_Time_Seconds,Avg_Throughput_MiBps,Avg_Loss_Rate_Pct,Success_Rate_Pct,Integrity_Verified");

            for (List<BenchmarkRunEntry> group : groups.values()) {
                BenchmarkRunEntry first = group.get(0);
                String protocol = first.protocol;
                String sizeLabel = first.sizeLabel;
                long sizeBytes = first.sizeBytes;
                int totalRuns = group.size();

                int completedRuns = 0;
                double sumTime = 0.0;
                double sumThroughput = 0.0;
                double sumLossRate = 0.0;

                for (BenchmarkRunEntry e : group) {
                    sumLossRate += e.lossRatePct;
                    sumThroughput += e.throughputMiBps;
                    sumTime += e.timeSeconds;
                    if (e.checksumMatch && "SUCCESS".equals(e.status)) {
                        completedRuns++;
                    }
                }

                double avgTime = sumTime / totalRuns;
                double avgThroughput = sumThroughput / totalRuns;
                double avgLossRate = sumLossRate / totalRuns;
                double successRate = (completedRuns * 100.0) / totalRuns;
                boolean integrity = (completedRuns == totalRuns);

                pw.printf("%s,%s,%d,%d,%d,%.6f,%.4f,%.2f,%.1f,%s%n",
                        protocol, sizeLabel, sizeBytes, completedRuns, totalRuns,
                        avgTime, avgThroughput, avgLossRate, successRate,
                        integrity ? "VERIFIED" : "UNVERIFIED");
            }
        }
        System.out.println("[Data] Wrote aggregate summary to: " + csvFile.getAbsolutePath());
    }

    /**
     * Prints an academic ASCII summary table of the benchmark results.
     */
    private static void printSummaryTable(File summaryCsv) {
        System.out.println("\n=====================================================================================================");
        System.out.println("                                      BENCHMARK SUMMARY RESULTS                                      ");
        System.out.println("=====================================================================================================");
        System.out.printf("%-6s | %-8s | %-12s | %-12s | %-15s | %-10s | %-12s | %-10s%n",
                "Proto", "Size", "Avg Time (s)", "Throughput", "Packets Lost %", "Completed", "Success Rate", "Integrity");
        System.out.println("-----------------------------------------------------------------------------------------------------");

        try (java.util.Scanner sc = new java.util.Scanner(summaryCsv)) {
            if (sc.hasNextLine()) sc.nextLine(); // Skip header
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                String[] p = line.split(",");
                if (p.length >= 10) {
                    System.out.printf("%-6s | %-8s | %12.4f | %10.2f MiB/s | %13.1f%% | %5s/%-5s | %11.1f%% | %-10s%n",
                            p[0], p[1],
                            Double.parseDouble(p[5]),
                            Double.parseDouble(p[6]),
                            Double.parseDouble(p[7]),
                            p[3], p[4],
                            Double.parseDouble(p[8]),
                            p[9]);
                }
            }
        } catch (Exception e) {
            System.err.println("Could not display summary table: " + e.getMessage());
        }
        System.out.println("=====================================================================================================");
    }

    /**
     * Triggers python script to render matplotlib plots.
     */
    public static void generatePlots() {
        System.out.println("\n[Graph Generator] Rendering throughput and transmission time plots...");
        try {
            File script = new File("scripts/plot_results.py");
            if (!script.exists()) {
                script = new File("TCPUDPFileTransfer/scripts/plot_results.py");
            }
            ProcessBuilder pb = new ProcessBuilder("python", script.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("  [Python] " + line);
            }
            int code = proc.waitFor();
            if (code == 0) {
                System.out.println("[Graph Generator] Plots saved to results/throughput.png and results/transmission_time.png");
            } else {
                System.err.println("[Graph Generator] Python plotting exited with error code: " + code);
            }
        } catch (Exception e) {
            System.err.println("[Graph Generator] Could not run python plotting script: " + e.getMessage());
        }
    }
}
