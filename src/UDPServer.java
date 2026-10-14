import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * UDPServer: Receives files transmitted over connectionless UDP datagrams.
 *
 * Networking Principles & Architecture:
 * - Uses DatagramSocket to receive discrete DatagramPackets.
 * - Connectionless: No 3-way handshake, no flow control, no built-in retransmissions.
 * - Out-of-order reassembly: Uses RandomAccessFile to write chunks at byte offsets
 *   calculated from each packet's sequence number (offset = seqNum * CHUNK_SIZE).
 * - Duplicate & Loss Detection: Tracks received sequence numbers in a boolean array.
 *   Duplicate packets are counted and ignored. Missing packets are calculated at completion.
 * - Timeout Management: Uses SocketTimeoutException to detect when transmission has ceased,
 *   preventing infinite hangs if the client disconnects or the END packet is lost.
 * - Returns a final STATUS_RESP datagram back to client with transmission statistics.
 */
public class UDPServer {

    public static final int DEFAULT_PORT = 8889;
    public static final String DEFAULT_SAVE_DIR = "received/udp";
    private static final int SOCKET_TIMEOUT_MS = 2500; // 2.5 second timeout

    private final int port;
    private final File saveDirectory;
    private volatile boolean running = false;
    private DatagramSocket socket;

    public UDPServer(int port, File saveDirectory) {
        this.port = port;
        this.saveDirectory = saveDirectory;
    }

    /**
     * Receives a single file transfer over UDP.
     * Blocks until file is complete or socket timeout occurs after transmission starts.
     */
    public UDPServerResult receiveOneFile() throws IOException {
        if (!saveDirectory.exists()) {
            saveDirectory.mkdirs();
        }

        byte[] rxBuffer = new byte[UDPPacket.MAX_PACKET_SIZE + 128];
        DatagramPacket rxPacket = new DatagramPacket(rxBuffer, rxBuffer.length);

        String fileName = null;
        long expectedFileSize = 0;
        int totalExpectedPackets = 0;
        boolean[] receivedMask = null;
        int uniquePacketsReceived = 0;
        int duplicatePacketsReceived = 0;
        long totalBytesReceived = 0;

        RandomAccessFile raf = null;
        File destinationFile = null;

        InetAddress clientAddress = null;
        int clientPort = -1;

        try (DatagramSocket serverSocket = new DatagramSocket(port)) {
            this.socket = serverSocket;
            this.running = true;
            // Increase OS receive buffer size to 4 MB to minimize kernel buffer drops
            serverSocket.setReceiveBufferSize(4 * 1024 * 1024);

            System.out.println("[UDP Server] Listening on port " + port + ". Awaiting incoming packets...");

            boolean transferStarted = false;
            boolean receivedEndMarker = false;

            while (running) {
                try {
                    serverSocket.receive(rxPacket);
                    clientAddress = rxPacket.getAddress();
                    clientPort = rxPacket.getPort();

                    UDPPacket packet = UDPPacket.fromBytes(rxPacket.getData(), rxPacket.getLength());

                    // When first packet arrives, arm the socket timeout to handle stalled transmissions
                    if (!transferStarted) {
                        transferStarted = true;
                        serverSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
                    }

                    // 1. Handle START / METADATA packet
                    if (packet.getPacketType() == UDPPacket.TYPE_START) {
                        if (receivedMask == null) {
                            fileName = new String(packet.getPayload(), StandardCharsets.UTF_8);
                            expectedFileSize = packet.getFileSize();
                            totalExpectedPackets = packet.getTotalPackets();
                            receivedMask = new boolean[totalExpectedPackets];
                            destinationFile = new File(saveDirectory, fileName);
                            raf = new RandomAccessFile(destinationFile, "rw");
                            // Pre-allocate file size so seek operations are valid
                            raf.setLength(expectedFileSize);
                            System.out.printf("[UDP Server] Started transfer for '%s' (Size: %,d B, Expected Packets: %d)%n",
                                    fileName, expectedFileSize, totalExpectedPackets);
                        }
                        continue;
                    }

                    // 2. Handle DATA packet
                    if (packet.getPacketType() == UDPPacket.TYPE_DATA) {
                        int seq = packet.getSeqNum();

                        // Fallback initialization if START packet was lost in transit
                        if (receivedMask == null) {
                            totalExpectedPackets = packet.getTotalPackets();
                            expectedFileSize = packet.getFileSize();
                            receivedMask = new boolean[totalExpectedPackets];
                            if (fileName == null) {
                                fileName = "recovered_file_" + System.currentTimeMillis() + ".bin";
                            }
                            destinationFile = new File(saveDirectory, fileName);
                            raf = new RandomAccessFile(destinationFile, "rw");
                            raf.setLength(expectedFileSize);
                            System.out.printf("[UDP Server] Received data without START packet. File size: %,d B%n", expectedFileSize);
                        }

                        if (seq >= 0 && seq < totalExpectedPackets) {
                            if (receivedMask[seq]) {
                                duplicatePacketsReceived++;
                            } else {
                                receivedMask[seq] = true;
                                uniquePacketsReceived++;
                                totalBytesReceived += packet.getPayloadLen();

                                // Reassemble out of order by writing directly at the calculated byte offset
                                long fileOffset = (long) seq * UDPPacket.CHUNK_SIZE;
                                raf.seek(fileOffset);
                                raf.write(packet.getPayload(), 0, packet.getPayloadLen());
                            }
                        }
                        continue;
                    }

                    // 3. Handle END packet
                    if (packet.getPacketType() == UDPPacket.TYPE_END) {
                        receivedEndMarker = true;
                        System.out.println("[UDP Server] Received END packet marker from client.");
                        break;
                    }

                } catch (SocketTimeoutException ste) {
                    if (transferStarted) {
                        System.out.println("[UDP Server] Socket receive timeout reached (no more packets arriving).");
                        break;
                    }
                }
            }

            // Close the file
            if (raf != null) {
                raf.close();
                raf = null;
            }

            int missingPackets = totalExpectedPackets - uniquePacketsReceived;
            boolean isComplete = (missingPackets == 0) && (totalBytesReceived == expectedFileSize) && (totalExpectedPackets > 0);

            // Send STATUS_RESP packet back to the client if we know client's address
            if (clientAddress != null && clientPort != -1) {
                try {
                    byte[] statusPayload = new byte[] { (byte) (isComplete ? 1 : 0) };
                    UDPPacket statusPacket = new UDPPacket(
                            UDPPacket.TYPE_STATUS_RESP,
                            uniquePacketsReceived,
                            missingPackets,
                            totalBytesReceived,
                            statusPayload.length,
                            statusPayload
                    );
                    byte[] txData = statusPacket.toBytes();
                    DatagramPacket ackPacket = new DatagramPacket(txData, txData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacket);
                    System.out.println("[UDP Server] Sent STATUS_RESP datagram back to client.");
                } catch (Exception e) {
                    System.err.println("[UDP Server] Failed to send status datagram: " + e.getMessage());
                }
            }

            System.out.printf("[UDP Server Summary] Received: %d/%d packets (Lost: %d, Duplicates: %d). Bytes: %,d/%,d. Complete: %s%n",
                    uniquePacketsReceived, totalExpectedPackets, missingPackets, duplicatePacketsReceived,
                    totalBytesReceived, expectedFileSize, isComplete ? "YES" : "NO");

            return new UDPServerResult(
                    destinationFile, fileName, expectedFileSize, totalBytesReceived,
                    totalExpectedPackets, uniquePacketsReceived, missingPackets, duplicatePacketsReceived,
                    isComplete
            );

        } finally {
            if (raf != null) {
                try { raf.close(); } catch (Exception ignored) {}
            }
            this.running = false;
        }
    }

    public void stop() {
        this.running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * Data holder for UDPServer outcome.
     */
    public static class UDPServerResult {
        public final File savedFile;
        public final String fileName;
        public final long expectedBytes;
        public final long receivedBytes;
        public final int totalExpectedPackets;
        public final int uniquePacketsReceived;
        public final int missingPackets;
        public final int duplicatePackets;
        public final boolean isComplete;

        public UDPServerResult(File savedFile, String fileName, long expectedBytes, long receivedBytes,
                               int totalExpectedPackets, int uniquePacketsReceived, int missingPackets,
                               int duplicatePackets, boolean isComplete) {
            this.savedFile = savedFile;
            this.fileName = fileName;
            this.expectedBytes = expectedBytes;
            this.receivedBytes = receivedBytes;
            this.totalExpectedPackets = totalExpectedPackets;
            this.uniquePacketsReceived = uniquePacketsReceived;
            this.missingPackets = missingPackets;
            this.duplicatePackets = duplicatePackets;
            this.isComplete = isComplete;
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        File saveDir = new File(DEFAULT_SAVE_DIR);

        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0]);
                return;
            }
        }
        if (args.length >= 2) {
            saveDir = new File(args[1]);
        }

        UDPServer server = new UDPServer(port, saveDir);
        try {
            server.receiveOneFile();
        } catch (Exception e) {
            System.err.println("[UDP Server Error] " + e.getMessage());
            e.printStackTrace();
        }
    }
}
