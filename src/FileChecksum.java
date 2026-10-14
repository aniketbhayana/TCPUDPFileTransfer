import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * FileChecksum: Utility class to compute cryptographic SHA-256 checksums.
 * Used to verify file integrity after TCP and UDP transmissions.
 */
public class FileChecksum {

    /**
     * Calculates the SHA-256 checksum of a given file.
     *
     * @param file The file to hash.
     * @return Hexadecimal string of the SHA-256 hash, or "FILE_NOT_FOUND" / "HASH_ERROR".
     */
    public static String calculateSHA256(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return "FILE_NOT_FOUND";
        }
        try (InputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = digest.digest();

            // Convert byte array to hexadecimal string
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "HASH_ERROR: " + e.getMessage();
        }
    }
}
