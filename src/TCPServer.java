import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TCPServer: Receives files transmitted over reliable TCP stream.
 *
 * Networking Principles:
 * - Uses ServerSocket to bind to a TCP port and listen for incoming connection requests.
 * - Sockets establish a full-duplex, byte-stream, connection-oriented channel.
 * - Reads metadata (filename, expected size) followed by raw byte stream.
 * - Detects premature disconnection if the client closes the connection before sending all bytes.
 * - Sends back an application-level acknowledgement string after saving the file.
 */
public class TCPServer {

    public static final int DEFAULT_PORT = 8888;
    public static final String DEFAULT_SAVE_DIR = "received/tcp";
    private static final int BUFFER_SIZE = 8192; // 8 KB chunk size

    private final int port;
    private final File saveDirectory;
    private volatile boolean running = false;
    private ServerSocket serverSocket;

    public TCPServer(int port, File saveDirectory) {
        this.port = port;
        this.saveDirectory = saveDirectory;
    }

    /**
     * Starts listening and handles a single incoming file transfer.
     * Used by the benchmark runner and standalone single-file transfers.
     */
    public File receiveOneFile() throws IOException {
        if (!saveDirectory.exists()) {
            saveDirectory.mkdirs();
        }

        try (ServerSocket server = new ServerSocket(port)) {
            this.serverSocket = server;
            this.running = true;
            System.out.println("[TCP Server] Listening on port " + port + ". Awaiting connection...");

            try (Socket clientSocket = server.accept();
                 DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

                System.out.println("[TCP Server] Accepted connection from " + clientSocket.getRemoteSocketAddress());

                // 1. Read metadata from client
                String fileName = dis.readUTF();
                long expectedFileSize = dis.readLong();
                System.out.printf("[TCP Server] Receiving: '%s' (Expected size: %,d bytes)%n", fileName, expectedFileSize);

                File destinationFile = new File(saveDirectory, fileName);
                long totalBytesRead = 0;

                // 2. Stream file content in chunks
                try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while (totalBytesRead < expectedFileSize) {
                        int toRead = (int) Math.min(buffer.length, expectedFileSize - totalBytesRead);
                        int bytesRead = dis.read(buffer, 0, toRead);

                        if (bytesRead == -1) {
                            throw new EOFException(String.format(
                                "Premature disconnection! Expected %,d bytes, but stream terminated after %,d bytes.",
                                expectedFileSize, totalBytesRead));
                        }

                        fos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }
                    fos.flush();
                }

                System.out.printf("[TCP Server] Successfully received %,d bytes. Saved to: %s%n",
                        totalBytesRead, destinationFile.getAbsolutePath());

                // 3. Send acknowledgement back to client
                dos.writeUTF("SUCCESS");
                dos.flush();
                System.out.println("[TCP Server] Sent completion ACK to client.");

                return destinationFile;
            }
        } finally {
            this.running = false;
        }
    }

    public void stop() {
        this.running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        File saveDir = new File(DEFAULT_SAVE_DIR);

        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                return;
            }
        }
        if (args.length >= 2) {
            saveDir = new File(args[1]);
        }

        TCPServer server = new TCPServer(port, saveDir);
        try {
            server.receiveOneFile();
        } catch (Exception e) {
            System.err.println("[TCP Server Error] " + e.getMessage());
            e.printStackTrace();
        }
    }
}
