import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * TCPClient: Reads a local file and transmits it to a TCPServer over a stream socket.
 *
 * Networking Principles:
 * - Initiates a three-way TCP handshake (SYN, SYN-ACK, ACK) upon Socket instantiation.
 * - Sends metadata (filename and file size) before transferring raw file data.
 * - Streams data in 8 KB chunks to prevent socket buffer exhaustion and handle files of any size.
 * - Awaits server-side ACK to confirm complete reception and disk write before closing.
 * - Measures high-precision transmission time using System.nanoTime().
 */
public class TCPClient {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 8888;
    private static final int BUFFER_SIZE = 8192; // 8 KB chunk size

    /**
     * Sends a file to the TCP server and returns a TransferResult.
     *
     * @param host Target host name or IP address.
     * @param port Target TCP port.
     * @param file Local file to transmit.
     * @return TransferResult containing elapsed time, throughput, and status.
     */
    public static TransferResult sendFile(String host, int port, File file) throws IOException {
        return sendFile(host, port, file, file.getName());
    }

    /**
     * Sends a file to the TCP server with a specified destination filename and returns a TransferResult.
     */
    public static TransferResult sendFile(String host, int port, File file, String remoteFileName) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
        }

        long fileSize = file.length();
        String sourceChecksum = FileChecksum.calculateSHA256(file);

        System.out.printf("[TCP Client] Connecting to %s:%d to send '%s' as '%s' (%,d bytes)...%n",
                host, port, file.getName(), remoteFileName, fileSize);

        long startNano = System.nanoTime();
        int chunkCount = 0;
        long totalBytesSent = 0;

        try (Socket socket = new Socket(host, port);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             FileInputStream fis = new FileInputStream(file)) {

            // 1. Send metadata (with custom remote filename)
            dos.writeUTF(remoteFileName);
            dos.writeLong(fileSize);
            dos.flush();

            // 2. Stream file content in chunks
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;
                chunkCount++;
            }
            dos.flush();

            // 3. Wait for Server Acknowledgement
            String serverAck = dis.readUTF();
            long endNano = System.nanoTime();

            double elapsedSeconds = (endNano - startNano) / 1_000_000_000.0;
            double throughputMiBps = (fileSize / (1024.0 * 1024.0)) / elapsedSeconds;

            boolean isSuccess = "SUCCESS".equals(serverAck) && (totalBytesSent == fileSize);

            // Compute hash of received file for verification
            File receivedFile = new File("received/tcp", remoteFileName);
            String receivedChecksum = receivedFile.exists() ? FileChecksum.calculateSHA256(receivedFile) : "MISSING";
            boolean checksumMatched = sourceChecksum.equalsIgnoreCase(receivedChecksum);

            System.out.printf("[TCP Client] Transfer finished in %.4f s (Throughput: %.2f MiB/s, ACK: %s, Checksum Match: %s)%n",
                    elapsedSeconds, throughputMiBps, serverAck, checksumMatched);

            return new TransferResult(
                    "TCP", remoteFileName, fileSize, totalBytesSent,
                    elapsedSeconds, throughputMiBps,
                    chunkCount, chunkCount, 0, 0.0,
                    sourceChecksum, receivedChecksum, checksumMatched,
                    isSuccess && checksumMatched,
                    isSuccess && checksumMatched ? "SUCCESS" : "VERIFICATION_FAILED"
            );
        }
    }

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        String filePath = null;

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
                System.err.println("Invalid port number: " + args[2]);
                return;
            }
        }

        if (filePath == null) {
            System.out.println("Usage: java TCPClient <filePath> [host] [port]");
            System.out.println("Default host: localhost, Default port: 8888");
            return;
        }

        try {
            File file = new File(filePath);
            TransferResult result = sendFile(host, port, file);
            System.out.println("\nResult Summary:");
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("[TCP Client Error] " + e.getMessage());
            e.printStackTrace();
        }
    }
}
