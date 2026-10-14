# Java Socket Programming Lab: TCP vs. UDP File Transfer

A complete, beginner-friendly Computer Networks laboratory project comparing **Transmission Control Protocol (TCP)** and **User Datagram Protocol (UDP)** for file transfer using core Java socket APIs (`java.net.Socket`, `java.net.ServerSocket`, `java.net.DatagramSocket`, `java.net.DatagramPacket`).

---

## 1. Project Objective & Networking Concepts

### Objective
To implement client-server file transfer systems using standard TCP stream sockets and UDP datagram sockets in pure Java (Java 17+), evaluate transmission time and throughput across varying file sizes (100 KB, 1 MB, 5 MB, 10 MB), analyze packet loss and reassembly dynamics, and justify which transport protocol is appropriate for reliable file transfer.

### Core Networking Concepts Applied
1. **Transport Layer Protocols:**
   - **TCP (RFC 793):** Connection-oriented, byte-stream abstraction, three-way handshake (`SYN`, `SYN-ACK`, `ACK`), flow control (sliding window), error recovery (cumulative ACKs, retransmissions), in-order delivery.
   - **UDP (RFC 768):** Connectionless, discrete packet boundaries, no handshake, minimal header overhead (8 bytes), best-effort delivery, no inherent flow control or congestion management.
2. **Datagram Sizing and Maximum Transmission Unit (MTU):**
   - Standard Ethernet MTU is 1,500 bytes. To avoid IP-level fragmentation, our UDP packets use 1,024-byte payloads + 21-byte application headers = 1,045 bytes total.
3. **Application-Level Out-of-Order Packet Reassembly:**
   - Rebuilding files received via UDP using random access seek operations (`offset = sequenceNumber * CHUNK_SIZE`).
4. **Cryptographic Integrity Verification:**
   - SHA-256 digest computation to ensure bit-exact file identity between sender and receiver.
5. **High-Resolution Performance Measurement:**
   - Measuring elapsed time with `System.nanoTime()` and deriving true throughput in MiB/s.

---

## 2. TCP vs. UDP Comparison

| Feature | TCP (Transmission Control Protocol) | UDP (User Datagram Protocol) |
| :--- | :--- | :--- |
| **Connection State** | Connection-oriented (virtual circuit) | Connectionless |
| **Transmission Model** | Continuous stream of bytes | Discrete, independent datagrams |
| **Reliability** | 100% Guaranteed (ARQ, retransmissions) | Best-effort (packets may drop or arrive out of order) |
| **Ordering** | Strictly guaranteed in-order delivery | Packets can arrive in any order or be duplicated |
| **Header Size** | 20–60 bytes | Fixed 8 bytes |
| **Flow & Congestion Control** | Built-in (Sliding window, slow start, congestion avoidance) | None (Sender transmits at arbitrary speeds) |
| **Handshake** | 3-way handshake (`SYN` $\rightarrow$ `SYN-ACK` $\rightarrow$ `ACK`) | Zero handshake |
| **Best Used For** | File transfer (FTP, SFTP), Web (HTTP/HTTPS), SSH, Email | DNS, VoIP, Video streaming, Multiplayer gaming, NTP |

---

## 3. Architecture & Message Protocols

### TCP Architecture
- **Server:** Listens on `ServerSocket(port)`. When a client connects via `accept()`, a dedicated `Socket` channel is formed.
- **Protocol Flow:**
  1. Client sends `fileName` (UTF string) and `fileSize` (8-byte long).
  2. Client streams file bytes in 8 KB chunks via `DataOutputStream`.
  3. Server loops until exactly `fileSize` bytes have been received and written to disk (`received/tcp/`).
  4. Server sends `"SUCCESS"` acknowledgement back to the client.

### UDP Protocol & Packet Format
Because raw UDP does not provide delivery guarantees or packet boundaries, a lightweight **21-byte application header** precedes every chunk:

```
+---------------+---------------+--------------------+--------------------+--------------------+
| Packet Type   | Sequence No.  | Total Packets      | File Size          | Payload Length     |
| (1 byte)      | (4 bytes int) | (4 bytes int)      | (8 bytes long)     | (4 bytes int)      |
+---------------+---------------+--------------------+--------------------+--------------------+
| 0             | 1             | 5                  | 9                  | 17               20|
+---------------+---------------+--------------------+--------------------+--------------------+
| Followed by: Data Payload (up to 1,024 bytes)                                                |
+----------------------------------------------------------------------------------------------+
```

#### Packet Types:
- `TYPE_START` (1): Carries filename and metadata.
- `TYPE_DATA` (2): Carries sequence number $0 \le \text{seq} < \text{totalPackets}$ and data payload.
- `TYPE_END` (3): Signals completion of client transmission.
- `TYPE_STATUS_RESP` (4): Server sends summary back to client (packets received, packets lost, bytes received, complete flag).

#### Out-of-Order Reassembly via `RandomAccessFile`:
When packet $k$ arrives at the UDP server, its absolute position in the destination file is computed as:
$$\text{File Offset} = k \times \text{CHUNK\_SIZE} \quad (1024 \text{ bytes})$$
The server invokes `raf.seek(offset)` and writes the payload directly. A `boolean[totalPackets]` bitmask tracks duplicate packets and missing slots.

---

## 4. Source Code Structure

```
TCPUDPFileTransfer/
│
├── src/
│   ├── Main.java               # Interactive CLI launcher and command dispatcher
│   ├── TCPServer.java          # Reliable TCP file receiver
│   ├── TCPClient.java          # TCP file sender with nanosecond timing
│   ├── UDPServer.java          # UDP receiver with loss detection & out-of-order reassembly
│   ├── UDPClient.java          # UDP packetizer with sequence numbers & status listener
│   ├── UDPPacket.java          # 21-byte binary datagram protocol serializer/deserializer
│   ├── BenchmarkRunner.java    # Automated multi-trial benchmark orchestrator
│   ├── FileChecksum.java       # Cryptographic SHA-256 hash calculator
│   └── TransferResult.java     # Data model for timing, loss, and throughput metrics
│
├── test-files/                 # Deterministically generated test files (100KB, 1MB, 5MB, 10MB)
├── received/
│   ├── tcp/                    # Transferred files saved by TCP Server
│   └── udp/                    # Reconstructed files saved by UDP Server
├── results/
│   ├── results.csv             # Raw per-trial benchmark records
│   ├── summary.csv             # Aggregated benchmark averages
│   ├── analysis.md             # Formal lab report & technical observations
│   ├── throughput.png          # Comparison graph: Throughput vs. File Size
│   └── transmission_time.png   # Comparison graph: Transmission Time vs. File Size
├── scripts/
│   └── plot_results.py         # Matplotlib script to render high-resolution PNG graphs
├── run.bat                     # Windows batch script for one-click compilation and execution
└── README.md                   # This project manual
```

---

## 5. Compilation & Execution (Windows)

### Prerequisites
- **Java:** OpenJDK 17 or later (`javac -version`, `java -version`)
- **Python (Optional for graphs):** Python 3 with `matplotlib` and `pandas`

### Option A: Using the Windows Batch Launcher (`run.bat`)
Simply double-click `run.bat` or run:
```cmd
run.bat
```
This presents an interactive menu allowing you to compile, run tests, execute the benchmark, or inspect graphs.

### Option B: Manual Command-Line Compilation
Open a terminal in the `TCPUDPFileTransfer` directory:
```cmd
:: 1. Compile all Java sources into bin/
mkdir bin
javac -d bin src/*.java
```

---

## 6. Running Server and Client Manually

You can run TCP or UDP transfers as separate processes in two independent terminal windows:

### TCP Transfer Example
**Terminal 1 (Start TCP Server):**
```cmd
java -cp bin TCPServer 8888 received/tcp
```
**Terminal 2 (Send file via TCP Client):**
```cmd
java -cp bin TCPClient test-files/file_1MB.bin localhost 8888
```

### UDP Transfer Example
**Terminal 1 (Start UDP Server):**
```cmd
java -cp bin UDPServer 8889 received/udp
```
**Terminal 2 (Send file via UDP Client):**
```cmd
java -cp bin UDPClient test-files/file_1MB.bin localhost 8889 0
```
*(The last argument `0` represents inter-packet delay in microseconds. Use `0` for maximum speed or e.g. `50` to throttle transmission).*

---

## 7. Running the Automated Benchmark Suite

To generate deterministic files, execute 3 trials of each protocol across all 4 file sizes, verify hashes, and generate CSV reports and PNG graphs:

```cmd
java -cp bin BenchmarkRunner
```
*(Or via the main CLI: `java -cp bin Main benchmark`)*

### Console Output Example
```
=====================================================================================================
                                      BENCHMARK SUMMARY RESULTS                                      
=====================================================================================================
Proto  | Size     | Avg Time (s) | Throughput   | Packets Lost %  | Completed  | Success Rate | Integrity 
-----------------------------------------------------------------------------------------------------
TCP    | 100 KB   |       0.0145 |      12.03 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  
TCP    | 1 MB     |       0.0134 |      75.82 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  
TCP    | 5 MB     |       0.0323 |     170.82 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  
TCP    | 10 MB    |       0.0518 |     202.86 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  
UDP    | 100 KB   |       0.0308 |       3.20 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  
UDP    | 1 MB     |       0.0541 |      18.85 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  
UDP    | 5 MB     |       0.1052 |      47.75 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  
UDP    | 10 MB    |       0.1476 |      67.85 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  
=====================================================================================================
```

---

## 8. Metric Formulas & Interpretation

1. **Elapsed Transmission Time ($T$):**
   $$T = \frac{t_{\text{end\_nano}} - t_{\text{start\_nano}}}{10^9} \quad \text{(seconds)}$$
   Measured from the moment transmission begins until final acknowledgement/status is confirmed. Server startup latency is excluded.
2. **Throughput:**
   $$\text{Throughput (MiB/s)} = \frac{\text{Successfully Received Bytes}}{T \times 1024 \times 1024}$$
   *Note: Incomplete UDP transfers are never treated as successful transfers; their effective rate is noted separately with packet loss percentage.*
3. **Packet Loss Rate:**
   $$\text{Loss Rate (\%)} = \left(\frac{\text{Packets Sent} - \text{Packets Received}}{\text{Packets Sent}}\right) \times 100$$
4. **Data Integrity:**
   $$\text{Integrity} = (\text{SHA-256}(\text{source}) == \text{SHA-256}(\text{destination}))$$

---

## 9. Troubleshooting & Common Issues

1. **`java.net.BindException: Address already in use`:**
   - Cause: Another process is using port 8888 or 8889, or an earlier server did not close cleanly.
   - Solution: Wait 5 seconds for the OS `TIME_WAIT` state to clear, or run the server on a different port (e.g., `8999`).
2. **`java.io.EOFException: Premature disconnection`:**
   - Cause: The client terminated before sending all expected bytes.
   - Solution: Verify that the source file is not locked or modified during transfer.
3. **UDP Server Timed Out / Incomplete Transfer:**
   - Cause: The END packet or data packets were dropped due to high transmission rate overflowing kernel socket buffers.
   - Solution: Increase the UDP socket receive buffer in code (`setReceiveBufferSize(4 * 1024 * 1024)`) or specify an inter-packet delay (e.g. `10` or `50` $\mu s$).

---

## 10. Lab Record Conclusion

In this experiment, file transfer systems were constructed and benchmarked over TCP and UDP using Java sockets. The empirical findings demonstrate:
- **TCP guarantees 100% data integrity and in-order delivery** via connection-oriented virtual circuits, window-based flow control, and automatic retransmissions. TCP achieved up to **202.86 MiB/s** throughput.
- **UDP is lightweight and has zero connection overhead**, but lacks reliability and flow control. Without an application-level reliability protocol, high-rate UDP streams risk kernel buffer overflow and packet loss.
- **Conclusion:** For applications where data accuracy is critical (file transfer, documents, binary executables), **TCP is the strictly justified and appropriate protocol**.
