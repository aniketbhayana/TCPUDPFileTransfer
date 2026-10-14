/**
 * TransferResult: Encapsulates performance metrics and verification results
 * for a single file transfer run (TCP or UDP).
 */
public class TransferResult {
    private final String protocol;           // "TCP" or "UDP"
    private final String fileName;           // Name of the transferred file
    private final long expectedBytes;        // Expected size of the file in bytes
    private final long receivedBytes;        // Actual bytes written / reconstructed
    private final double elapsedTimeSeconds; // Total transmission duration in seconds
    private final double throughputMiBps;    // Throughput in MiB/s
    private final int packetsSent;           // Datagram packets sent (UDP) or chunk count (TCP)
    private final int packetsReceived;       // Packets successfully received
    private final int packetsLost;           // Missing packets (packetsSent - packetsReceived)
    private final double packetLossRate;     // Loss rate percentage (0.0 to 100.0)
    private final String sourceChecksum;     // SHA-256 of original file
    private final String receivedChecksum;   // SHA-256 of destination file
    private final boolean checksumMatched;   // True if both checksums match
    private final boolean isSuccess;         // True if entire file completed and matched
    private final String statusMessage;      // Detailed description (e.g. "COMPLETE", "INCOMPLETE: Packets Lost")

    public TransferResult(String protocol, String fileName, long expectedBytes, long receivedBytes,
                          double elapsedTimeSeconds, double throughputMiBps,
                          int packetsSent, int packetsReceived, int packetsLost, double packetLossRate,
                          String sourceChecksum, String receivedChecksum,
                          boolean checksumMatched, boolean isSuccess, String statusMessage) {
        this.protocol = protocol;
        this.fileName = fileName;
        this.expectedBytes = expectedBytes;
        this.receivedBytes = receivedBytes;
        this.elapsedTimeSeconds = elapsedTimeSeconds;
        this.throughputMiBps = throughputMiBps;
        this.packetsSent = packetsSent;
        this.packetsReceived = packetsReceived;
        this.packetsLost = packetsLost;
        this.packetLossRate = packetLossRate;
        this.sourceChecksum = sourceChecksum;
        this.receivedChecksum = receivedChecksum;
        this.checksumMatched = checksumMatched;
        this.isSuccess = isSuccess;
        this.statusMessage = statusMessage;
    }

    public String getProtocol() { return protocol; }
    public String getFileName() { return fileName; }
    public long getExpectedBytes() { return expectedBytes; }
    public long getReceivedBytes() { return receivedBytes; }
    public double getElapsedTimeSeconds() { return elapsedTimeSeconds; }
    public double getThroughputMiBps() { return throughputMiBps; }
    public int getPacketsSent() { return packetsSent; }
    public int getPacketsReceived() { return packetsReceived; }
    public int getPacketsLost() { return packetsLost; }
    public double getPacketLossRate() { return packetLossRate; }
    public String getSourceChecksum() { return sourceChecksum; }
    public String getReceivedChecksum() { return receivedChecksum; }
    public boolean isChecksumMatched() { return checksumMatched; }
    public boolean isSuccess() { return isSuccess; }
    public String getStatusMessage() { return statusMessage; }

    @Override
    public String toString() {
        return String.format(
            "[%s] File: %s | Expected: %,d B | Received: %,d B (%.1f%%) | Time: %.4f s | Throughput: %.2f MiB/s | Pkts: %d/%d (Lost: %d, %.1f%%) | Match: %s | Status: %s",
            protocol, fileName, expectedBytes, receivedBytes,
            (expectedBytes > 0 ? (receivedBytes * 100.0 / expectedBytes) : 0),
            elapsedTimeSeconds, throughputMiBps,
            packetsReceived, packetsSent, packetsLost, packetLossRate,
            checksumMatched ? "YES" : "NO",
            statusMessage
        );
    }
}
