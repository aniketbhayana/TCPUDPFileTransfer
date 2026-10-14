import java.nio.ByteBuffer;

/**
 * UDPPacket: Defines the application-level datagram protocol for file transfer.
 *
 * Header Specification (21 bytes total):
 * -------------------------------------------------------------
 * Field           Type    Size (bytes)   Description
 * -------------------------------------------------------------
 * packetType      byte    1              1: START, 2: DATA, 3: END, 4: STATUS_RESP
 * seqNum          int     4              Sequence number (0 to totalPackets - 1)
 * totalPackets    int     4              Total expected data packets
 * fileSize        long    8              Total size of original file in bytes
 * payloadLen      int     4              Length of data chunk in this packet
 * -------------------------------------------------------------
 * Followed by payload: up to CHUNK_SIZE (1024 bytes).
 * Total packet size <= 1045 bytes (well within standard MTU of 1500 bytes).
 */
public class UDPPacket {

    public static final byte TYPE_START = 1;
    public static final byte TYPE_DATA = 2;
    public static final byte TYPE_END = 3;
    public static final byte TYPE_STATUS_RESP = 4;

    public static final int HEADER_SIZE = 1 + 4 + 4 + 8 + 4; // 21 bytes
    public static final int CHUNK_SIZE = 1024;               // 1024 bytes payload
    public static final int MAX_PACKET_SIZE = HEADER_SIZE + CHUNK_SIZE; // 1045 bytes

    private final byte packetType;
    private final int seqNum;
    private final int totalPackets;
    private final long fileSize;
    private final int payloadLen;
    private final byte[] payload;

    public UDPPacket(byte packetType, int seqNum, int totalPackets, long fileSize, int payloadLen, byte[] payload) {
        this.packetType = packetType;
        this.seqNum = seqNum;
        this.totalPackets = totalPackets;
        this.fileSize = fileSize;
        this.payloadLen = payloadLen;
        this.payload = payload != null ? payload : new byte[0];
    }

    public byte getPacketType() { return packetType; }
    public int getSeqNum() { return seqNum; }
    public int getTotalPackets() { return totalPackets; }
    public long getFileSize() { return fileSize; }
    public int getPayloadLen() { return payloadLen; }
    public byte[] getPayload() { return payload; }

    /**
     * Serializes this packet into a byte array suitable for DatagramPacket transmission.
     */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payloadLen);
        buffer.put(packetType);
        buffer.putInt(seqNum);
        buffer.putInt(totalPackets);
        buffer.putLong(fileSize);
        buffer.putInt(payloadLen);
        if (payloadLen > 0 && payload != null) {
            buffer.put(payload, 0, payloadLen);
        }
        return buffer.array();
    }

    /**
     * Parses a received byte array into a UDPPacket object.
     */
    public static UDPPacket fromBytes(byte[] data, int length) {
        if (length < HEADER_SIZE) {
            throw new IllegalArgumentException("Received datagram smaller than header size: " + length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
        byte type = buffer.get();
        int seq = buffer.getInt();
        int total = buffer.getInt();
        long size = buffer.getLong();
        int pLen = buffer.getInt();

        byte[] payloadBytes = new byte[pLen];
        if (pLen > 0 && buffer.remaining() >= pLen) {
            buffer.get(payloadBytes, 0, pLen);
        }
        return new UDPPacket(type, seq, total, size, pLen, payloadBytes);
    }
}
