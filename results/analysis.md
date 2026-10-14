# Performance Analysis: TCP vs. UDP File Transfer

**Course:** Computer Networks Laboratory  
**Experiment:** Implementation and Comparative Performance Analysis of TCP and UDP File Transfer  
**Platform:** Windows 11, OpenJDK 21 (64-Bit), Core Java Sockets (`java.net.Socket`, `java.net.DatagramSocket`)  
**Measurement Environment:** Loopback Interface (`localhost:127.0.0.1`)  

---

## 1. Experimental Overview and Objectives

This experiment investigates the transport-layer performance trade-offs between **Transmission Control Protocol (TCP)** and **User Datagram Protocol (UDP)** using pure Java socket programming.

Both protocols were benchmarked against four deterministic test files:
- **100 KB** (102,400 bytes, 100 UDP packets)
- **1 MB** (1,048,576 bytes, 1,024 UDP packets)
- **5 MB** (5,242,880 bytes, 5,120 UDP packets)
- **10 MB** (10,485,760 bytes, 10,240 UDP packets)

Each test case was executed over **3 independent trials** (`Run 1`, `Run 2`, `Run 3`) to eliminate transient scheduling anomalies. Transmission timing was captured using `System.nanoTime()`, and cryptographic integrity was validated by comparing the source and destination **SHA-256** checksums.

---

## 2. Experimental Data Summary

The table below summarizes the aggregated benchmark results extracted from `results/summary.csv`:

| Protocol | File Size | Size (Bytes) | Completed Runs | Avg. Transmission Time (s) | Avg. Throughput (MiB/s) | Avg. Packet Loss (%) | Checksum Match | Transfer Status |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **TCP** | 100 KB | 102,400 | 3 / 3 | 0.0145 s | 12.03 MiB/s | 0.00% | Verified | 100% SUCCESS |
| **TCP** | 1 MB | 1,048,576 | 3 / 3 | 0.0134 s | 75.82 MiB/s | 0.00% | Verified | 100% SUCCESS |
| **TCP** | 5 MB | 5,242,880 | 3 / 3 | 0.0323 s | 170.82 MiB/s | 0.00% | Verified | 100% SUCCESS |
| **TCP** | 10 MB | 10,485,760 | 3 / 3 | 0.0518 s | 202.86 MiB/s | 0.00% | Verified | 100% SUCCESS |
| **UDP** | 100 KB | 102,400 | 3 / 3 | 0.0308 s | 3.20 MiB/s | 0.00% | Verified | 100% SUCCESS |
| **UDP** | 1 MB | 1,048,576 | 3 / 3 | 0.0541 s | 18.85 MiB/s | 0.00% | Verified | 100% SUCCESS |
| **UDP** | 5 MB | 5,242,880 | 3 / 3 | 0.1052 s | 47.75 MiB/s | 0.00% | Verified | 100% SUCCESS |
| **UDP** | 10 MB | 10,485,760 | 3 / 3 | 0.1476 s | 67.85 MiB/s | 0.00% | Verified | 100% SUCCESS |

---

## 3. Detailed Answers to Core Network Lab Questions

### Q1: How does transmission time change as file size increases?

For both protocols, transmission time scales monotonically with file size:
- **TCP Transmission Time:**
  - 100 KB: ~0.0145 seconds
  - 1 MB: ~0.0134 seconds
  - 5 MB: ~0.0323 seconds
  - 10 MB: ~0.0518 seconds
- **UDP Transmission Time:**
  - 100 KB: ~0.0308 seconds
  - 1 MB: ~0.0541 seconds
  - 5 MB: ~0.1052 seconds
  - 10 MB: ~0.1476 seconds

**Key Observation:** For small files (e.g., 100 KB), the initial fixed overhead (connection establishment, thread coordination, metadata exchange, and socket setup) dominates the total measured time. As the payload grows from 1 MB to 10 MB, transmission time becomes proportional to data volume, reflecting the raw data pumping and memory copying capacity of the operating system.

---

### Q2: How does throughput change with file size?

**Throughput Formula:**
$$\text{Throughput (MiB/s)} = \frac{\text{Successfully Received Bytes}}{\text{Elapsed Time (seconds)} \times 1024 \times 1024}$$

- **TCP Throughput Growth:**
  - 100 KB: **12.03 MiB/s**
  - 1 MB: **75.82 MiB/s**
  - 5 MB: **170.82 MiB/s**
  - 10 MB: **202.86 MiB/s**
- **UDP Throughput Growth:**
  - 100 KB: **3.20 MiB/s**
  - 1 MB: **18.85 MiB/s**
  - 5 MB: **47.75 MiB/s**
  - 10 MB: **67.85 MiB/s**

**Analysis:**
1. **Amortization of Startup Latency:** For 100 KB, fixed costs (socket allocation, metadata handshake) are spread across only 100 KB of data, yielding low throughput. For 10 MB, the transfer time is dominated by bulk streaming, amortizing the startup overhead and driving TCP throughput up to **202.86 MiB/s**.
2. **Chunk Size and Syscall Overhead:** TCP transmitted large 8,192-byte chunks with native stream pipelining, requiring far fewer operating system kernel calls. Conversely, UDP was constrained to 1,024-byte payloads per packet (to respect MTU boundaries). Transmitting 10 MB required **10,240 discrete `socket.send()` system calls** in UDP versus only **1,280 chunk writes** in TCP. The repetitive user-to-kernel context switching accounts for UDP's lower throughput in this benchmark.

---

### Q3: Which protocol completes the transfers more consistently?

**TCP exhibits complete consistency.**
- In our trials, TCP achieved **100% completion rate (12 out of 12 runs)** and **100% cryptographic checksum matches**.
- TCP guarantees:
  1. **Strict Ordering:** Bytes are assembled and delivered to the application in the exact sequence transmitted.
  2. **Zero Incomplete Transfers:** TCP's byte-stream abstraction will either deliver every byte or throw an `IOException` / `EOFException` if the connection fails.
- UDP transfers are fundamentally connectionless. While all UDP runs succeeded in this specific trial after tuning the socket receive buffer to 4 MB, UDP without application-level pacing or buffer tuning frequently encounters packet loss even on localhost when socket buffers overflow.

---

### Q4: Why might UDP be faster in some circumstances, and why is it not guaranteed to be faster?

**Why UDP can be faster:**
1. **Zero Handshake Latency:** UDP eliminates the initial three-way handshake (`SYN`, `SYN-ACK`, `ACK`), beginning data transmission immediately.
2. **Zero Transport-Level Acknowledgement Overhead:** UDP does not pause for per-packet acknowledgements or calculate sliding windows.
3. **No Head-of-Line Blocking:** In lossy networks, when a packet is dropped, TCP halts delivery of subsequent packets until the missing packet is retransmitted. UDP continues delivering arriving datagrams without waiting.

**Why UDP is NOT guaranteed to be faster:**
1. **Packetization Overhead:** Because datagrams must stay within network MTU limits (typically ~1,500 bytes), sending large files requires thousands of individual packet creations, header serializations, and `sendto()` system calls.
2. **Packet Drops at High Data Rates:** When sending at full interface speed, UDP easily overruns the receiver's kernel socket buffer. In complete applications, recovering from lost packets requires retransmission logic, which introduces substantial latency if implemented naively in user-space.

---

### Q5: Why might UDP transfers be incomplete even on localhost?

A common misconception among networking students is that packet loss cannot occur on `localhost` (the loopback interface `127.0.0.1`) because there are no physical cables, routers, or Wi-Fi collisions.

However, **UDP packet drops regularly occur on localhost** due to:
1. **Kernel Socket Buffer Overflow:** When the client thread transmits 10,240 datagrams in a tight loop without delay, it injects packets into the loopback driver at multi-gigabit speeds. If the receiving thread is momentarily preempted by the OS scheduler (or blocked doing disk I/O with `RandomAccessFile`), the OS kernel's UDP receive buffer (default 64 KB on Windows) fills completely. Any datagram arriving while the buffer is full is **silently discarded by the OS kernel**.
2. **Lack of Flow Control:** TCP utilizes the `Window Size` field in its TCP header to inform the sender how much buffer space is available, automatically throttling the sender. UDP possesses **no flow control**; the sender blasts packets blindly regardless of the receiver's instantaneous capacity.

---

### Q6: Why is TCP more suitable for reliable file transfer?

For file transfer (unlike voice or video streaming), **100% bit-exact fidelity is non-negotiable**. A single flipped bit or missing 1 KB chunk corrupts executables, archives, and documents.

TCP provides built-in transport-layer mechanisms specifically designed for this:
1. **Reliability via ARQ (Automatic Repeat reQuest):** Positive acknowledgements, cumulative ACKs, and retransmission timers ensure lost segments are automatically re-sent.
2. **In-Order Delivery:** TCP sequence numbers ensure segments reordered in transit are sorted before being handed to the application.
3. **Duplicate Suppression:** Duplicate segments are automatically detected and discarded.
4. **Flow Control:** Sliding-window flow control prevents the sender from overwhelming the receiver's memory buffer.
5. **Congestion Control:** Algorithms like Cubic, Reno, and BBR prevent network collapse across shared links.

Building all of these mechanisms on top of UDP essentially amounts to reimplementing TCP in user space (e.g., QUIC or SCTP).

---

### Q7: What are the limitations of benchmarking on localhost rather than across a real network?

While localhost provides a reproducible and controlled testing sandbox, it has notable limitations:
1. **Zero Propagation Delay & Jitter:** On localhost, Round-Trip Time (RTT) is practically zero (~0.05 ms). On a wide-area network (WAN), RTT ranges from 20 ms to 250 ms. TCP's throughput on a real WAN is heavily constrained by the Bandwidth-Delay Product (BDP) and the slow-start algorithm.
2. **Absence of Intermediate Routers:** Localhost involves no router queues, active queue management (AQM), or middleboxes (firewalls, NATs).
3. **CPU and Disk Contention:** On localhost, the client and server processes share the same physical CPU, RAM, and storage device. Server disk write delays directly impact client CPU time slices.
4. **Infinite Bandwidth Illusion:** Localhost loopback operations are essentially memory-to-memory copies mediated by the OS networking stack, masking real-world transmission and bandwidth bottlenecks.

---

## 4. Extension: How Acknowledgements and Retransmissions Could Be Added to UDP

To make UDP reliable for file transfer without switching to TCP, an application-layer protocol must introduce:
1. **Stop-and-Wait ARQ (Simple):**
   - The sender transmits packet $N$ and waits for an explicit `ACK(N)` from the receiver before sending $N+1$.
   - If an ACK does not arrive within a timeout interval, the sender retransmits packet $N$.
2. **Selective Repeat / Sliding Window ARQ (High Performance):**
   - The sender maintains a window of unacknowledged packets (e.g., packets $N$ to $N + W$).
   - The receiver transmits selective acknowledgements (`SACK`), indicating specific missing sequence numbers.
   - The sender retransmits only the missing datagrams rather than restarting the entire transfer.
3. **Adaptive Congestion Control:**
   - Dynamically adjusting packet transmission delays based on measured RTT to prevent buffer overflow (similar to QUIC/BBR).

---

## 5. Summary Conclusion for Lab Records

In this experiment, TCP and UDP file transfer systems were designed, implemented, and benchmarked using standard Java socket programming.

The empirical data demonstrates:
1. **TCP is superior for reliable data transfer**, achieving high throughput (**202.86 MiB/s** on 10 MB) with zero packet loss and 100% cryptographic data integrity.
2. **UDP is lightweight and connectionless**, making it ideal for real-time applications (DNS, gaming, VoIP) where low latency is preferred over complete reliability.
3. For file transmission without an application-level reliability framework, UDP suffers from lack of flow control and risks data truncation or packet loss. Therefore, **TCP is the justified and optimal transport protocol for reliable file transfer**.
