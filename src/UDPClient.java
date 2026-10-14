import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.LockSupport;

/**
 * UDPClient: Reads a local file, packetizes it into discrete UDP datagrams,
 * and transmits them to a UDPServer.
 *
 * Networking Principles & Architecture:
 * - Connectionless Datagram Service: Uses DatagramSocket without a virtual circuit.
 * - Packetization: Breaks files into chunks of 1024 bytes (header + payload = 1045 bytes).
 * - Datagram Protocol: Attaches sequence numbers, total packet count, and file size to each datagram.
 * - Transmission Flow:
 *   1. Sends START packet(s) containing filename and metadata.
 *   2. Loops through file, transmitting DATA packets with monotonic sequence numbers.
 *   3. Transmits END packet to signal completion of client transmission.
 *   4. Listens for server STATUS_RESP to determine received bytes, packet loss, and completion.
 * - Pacing: Offers optional microsecond delay per packet (useful for examining buffer overflow).
 * - No Automatic Retransmission: Pure transport-level UDP behavior as specified by lab guidelines.
 */
public class UDPClient {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 8889;
    private static final int ACK_TIMEOUT_MS = 3000;

    /**
     * Transmits a file using UDP datagrams.
     *
     * @param host Target host name or IP address.
     * @param port Target UDP port.
     * @param file File to transmit.
     * @param delayMicros Optional delay between packets in microseconds (0 for full speed).
     * @return TransferResult containing transmission metrics, packet loss, and checksum verification.
     */
    public static TransferResult sendFile(String host, int port, File file, long delayMicros) throws IOException {
        return sendFile(host, port, file, file.getName(), delayMicros);
    }

    /**
     * Transmits a file using UDP datagrams with custom destination filename.
     */
    public static TransferResult sendFile(String host, int port, File file, String remoteFileName, long delayMicros) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
        }

        long fileSize = file.length();
        String sourceChecksum = FileChecksum.calculateSHA256(file);
        int totalPackets = (int) Math.ceil((double) fileSize / UDPPacket.CHUNK_SIZE);
        if (totalPackets == 0) totalPackets = 1;

        InetAddress targetAddress = InetAddress.getByName(host);

        System.out.printf("[UDP Client] Sending '%s' as '%s' (%,d B, %d packets) to %s:%d (Delay: %d µs)...%n",
                file.getName(), remoteFileName, fileSize, totalPackets, host, port, delayMicros);

        long startNano = System.nanoTime();
        int packetsSent = 0;

        try (DatagramSocket socket = new DatagramSocket();
             FileInputStream fis = new FileInputStream(file)) {

            // Optional: increase socket send buffer to 4 MB
            socket.setSendBufferSize(4 * 1024 * 1024);

            // 1. Send START / METADATA packet (sent 2 times with 2ms interval to reduce risk of initial loss)
            byte[] fileNameBytes = remoteFileName.getBytes(StandardCharsets.UTF_8);
            UDPPacket startPacket = new UDPPacket(
                    UDPPacket.TYPE_START,
                    0,
                    totalPackets,
                    fileSize,
                    fileNameBytes.length,
                    fileNameBytes
            );
            byte[] startBytes = startPacket.toBytes();
            DatagramPacket startDatagram = new DatagramPacket(startBytes, startBytes.length, targetAddress, port);
            for (int i = 0; i < 2; i++) {
                socket.send(startDatagram);
                try { Thread.sleep(2); } catch (InterruptedException ignored) {}
            }

            // 2. Transmit DATA packets
            byte[] chunkBuffer = new byte[UDPPacket.CHUNK_SIZE];
            int bytesRead;
            int seqNum = 0;

            while ((bytesRead = fis.read(chunkBuffer)) != -1) {
                UDPPacket dataPacket = new UDPPacket(
                        UDPPacket.TYPE_DATA,
                        seqNum,
                        totalPackets,
                        fileSize,
                        bytesRead,
                        chunkBuffer
                );
                byte[] dataBytes = dataPacket.toBytes();
                DatagramPacket datagram = new DatagramPacket(dataBytes, dataBytes.length, targetAddress, port);
                socket.send(datagram);
                packetsSent++;
                seqNum++;

                // Apply pacing if requested (using high-resolution parkNanos)
                if (delayMicros > 0) {
                    LockSupport.parkNanos(delayMicros * 1000L);
                }
            }

            // 3. Send END packet marker (sent 3 times with small delay to ensure delivery)
            UDPPacket endPacket = new UDPPacket(
                    UDPPacket.TYPE_END,
                    totalPackets,
                    totalPackets,
                    fileSize,
                    0,
                    new byte[0]
            );
            byte[] endBytes = endPacket.toBytes();
            DatagramPacket endDatagram = new DatagramPacket(endBytes, endBytes.length, targetAddress, port);
            for (int i = 0; i < 3; i++) {
                socket.send(endDatagram);
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }

            // 4. Await STATUS_RESP from the server
            socket.setSoTimeout(ACK_TIMEOUT_MS);
            byte[] rxBuffer = new byte[512];
            DatagramPacket respDatagram = new DatagramPacket(rxBuffer, rxBuffer.length);

            int serverReceivedPackets = 0;
            int serverMissingPackets = totalPackets;
            long serverReceivedBytes = 0;
            boolean serverReportedComplete = false;
            boolean receivedStatus = false;

            try {
                socket.receive(respDatagram);
                UDPPacket statusPacket = UDPPacket.fromBytes(respDatagram.getData(), respDatagram.getLength());
                if (statusPacket.getPacketType() == UDPPacket.TYPE_STATUS_RESP) {
                    serverReceivedPackets = statusPacket.getSeqNum();
                    serverMissingPackets = statusPacket.getTotalPackets();
                    serverReceivedBytes = statusPacket.getFileSize();
                    byte[] payload = statusPacket.getPayload();
                    serverReportedComplete = (payload != null && payload.length > 0 && payload[0] == 1);
                    receivedStatus = true;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("[UDP Client] Timed out waiting for server STATUS_RESP datagram.");
            }

            long endNano = System.nanoTime();
            double elapsedSeconds = (endNano - startNano) / 1_000_000_000.0;

            // Calculate metrics
            double throughputMiBps = 0.0;
            if (elapsedSeconds > 0) {
                throughputMiBps = (serverReceivedBytes / (1024.0 * 1024.0)) / elapsedSeconds;
            }

            double lossRate = totalPackets > 0 ? (serverMissingPackets * 100.0 / totalPackets) : 0.0;

            // Verify file checksum
            File receivedFile = new File("received/udp", remoteFileName);
            String receivedChecksum = receivedFile.exists() ? FileChecksum.calculateSHA256(receivedFile) : "MISSING";
            boolean checksumMatched = sourceChecksum.equalsIgnoreCase(receivedChecksum);

            boolean isSuccess = serverReportedComplete && checksumMatched && (serverMissingPackets == 0);

            String statusMessage;
            if (isSuccess) {
                statusMessage = "SUCCESS";
            } else if (serverMissingPackets > 0) {
                statusMessage = String.format("INCOMPLETE: %d/%d packets lost (%.1f%%)",
                        serverMissingPackets, totalPackets, lossRate);
            } else if (!checksumMatched) {
                statusMessage = "CORRUPTED: Checksum mismatch";
            } else {
                statusMessage = "FAILED: Incomplete transfer";
            }

            System.out.printf("[UDP Client] Transfer finished in %.4f s (Received: %,d/%,d B, Loss: %.1f%%, Throughput: %.2f MiB/s, Status: %s)%n",
                    elapsedSeconds, serverReceivedBytes, fileSize, lossRate, throughputMiBps, statusMessage);

            return new TransferResult(
                    "UDP", remoteFileName, fileSize, serverReceivedBytes,
                    elapsedSeconds, throughputMiBps,
                    packetsSent, serverReceivedPackets, serverMissingPackets, lossRate,
                    sourceChecksum, receivedChecksum, checksumMatched,
                    isSuccess, statusMessage
            );
        }
    }

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        String filePath = null;
        long delayMicros = 0;

        if (args.length >= 1) {
            filePath = args[0];
        }
        if (args.length >= 2) {
            host = args[1];
        }
        if (args.length >= 3) {
            try {
                port = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[2]);
                return;
            }
        }
        if (args.length >= 4) {
            try {
                delayMicros = Long.parseLong(args[3]);
            } catch (NumberFormatException ignored) {}
        }

        if (filePath == null) {
            System.out.println("Usage: java UDPClient <filePath> [host] [port] [delayMicros]");
            System.out.println("Default host: localhost, port: 8889, delay: 0");
            return;
        }

        try {
            File file = new File(filePath);
            TransferResult result = sendFile(host, port, file, delayMicros);
            System.out.println("\nResult Summary:");
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("[UDP Client Error] " + e.getMessage());
            e.printStackTrace();
        }
    }
}
