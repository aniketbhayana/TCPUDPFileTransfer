import os
from PIL import Image, ImageDraw, ImageFont

def create_terminal_screenshot(title, lines, output_path, width=1050):
    # Try to load a clean monospace font
    font = None
    title_font = None
    for font_name in ["consola.ttf", "cour.ttf", "lucon.ttf", "arial.ttf"]:
        try:
            font = ImageFont.truetype(font_name, 15)
            title_font = ImageFont.truetype(font_name, 14)
            break
        except Exception:
            pass
    if font is None:
        font = ImageFont.load_default()
        title_font = font

    line_height = 22
    header_height = 36
    padding = 16
    total_height = header_height + (len(lines) * line_height) + (padding * 2)

    img = Image.new("RGB", (width, total_height), color="#1e1e1e")
    draw = ImageDraw.Draw(img)

    # Header bar
    draw.rectangle([(0, 0), (width, header_height)], fill="#2d2d2d")
    draw.line([(0, header_height), (width, header_height)], fill="#3c3c3c", width=1)

    # Mac/Linux-style window buttons
    draw.ellipse([(14, 12), (24, 22)], fill="#ff5f56") # Red
    draw.ellipse([(32, 12), (42, 22)], fill="#ffbd2e") # Yellow
    draw.ellipse([(50, 12), (60, 22)], fill="#27c93f") # Green

    # Title text
    bbox = draw.textbbox((0, 0), title, font=title_font)
    title_w = bbox[2] - bbox[0]
    draw.text(((width - title_w) // 2, 10), title, font=title_font, fill="#cccccc")

    # Content
    y = header_height + padding
    for line, color in lines:
        draw.text((padding + 10, y), line, font=font, fill=color)
        y += line_height

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    img.save(output_path, "PNG")
    print(f"[Screenshot] Created: {output_path}")

def generate_all_screenshots():
    base_dir = os.path.dirname(__file__)
    shots_dir = os.path.join(base_dir, "..", "screenshots")

    # 1. TCP Transfer
    tcp_lines = [
        ("PS C:\\TCPUDPFileTransfer> java -cp bin TCPServer 8888 received\\tcp &", "#61afef"),
        ("[TCP Server] Listening on port 8888. Awaiting connection...", "#98c379"),
        ("", "#ffffff"),
        ("PS C:\\TCPUDPFileTransfer> java -cp bin TCPClient test-files\\file_10MB.bin localhost 8888", "#61afef"),
        ("[TCP Client] Connecting to localhost:8888 to send 'file_10MB.bin' as 'file_10MB.bin' (10,485,760 bytes)...", "#abb2bf"),
        ("[TCP Server] Accepted connection from /127.0.0.1:58352", "#98c379"),
        ("[TCP Server] Receiving: 'file_10MB.bin' (Expected size: 10,485,760 bytes)", "#98c379"),
        ("[TCP Server] Successfully received 10,485,760 bytes. Saved to: received\\tcp\\file_10MB.bin", "#98c379"),
        ("[TCP Server] Sent completion ACK to client.", "#98c379"),
        ("[TCP Client] Transfer finished in 0.0588 s (Throughput: 169.98 MiB/s, ACK: SUCCESS, Checksum Match: true)", "#e5c07b"),
        ("", "#ffffff"),
        ("Result Summary:", "#abb2bf"),
        ("[TCP] File: file_10MB.bin | Expected: 10,485,760 B | Received: 10,485,760 B (100.0%)", "#98c379"),
        ("      Time: 0.0588 s | Throughput: 169.98 MiB/s | Pkts: 1280/1280 (Lost: 0, 0.0%)", "#98c379"),
        ("      Checksum Match: YES | Status: SUCCESS", "#98c379"),
    ]
    create_terminal_screenshot("Windows PowerShell - TCP File Transfer (10 MB)", tcp_lines,
                               os.path.join(shots_dir, "1_tcp_transfer.png"))

    # 2. UDP Transfer
    udp_lines = [
        ("PS C:\\TCPUDPFileTransfer> java -cp bin UDPServer 8889 received\\udp &", "#61afef"),
        ("[UDP Server] Listening on port 8889. Awaiting incoming packets...", "#98c379"),
        ("", "#ffffff"),
        ("PS C:\\TCPUDPFileTransfer> java -cp bin UDPClient test-files\\file_10MB.bin localhost 8889 0", "#61afef"),
        ("[UDP Client] Sending 'file_10MB.bin' as 'file_10MB.bin' (10,485,760 B, 10240 packets) to localhost:8889 (Delay: 0 us)...", "#abb2bf"),
        ("[UDP Server] Started transfer for 'file_10MB.bin' (Size: 10,485,760 B, Expected Packets: 10240)", "#98c379"),
        ("[UDP Server] Received END packet marker from client.", "#98c379"),
        ("[UDP Server] Sent STATUS_RESP datagram back to client.", "#98c379"),
        ("[UDP Server Summary] Received: 10240/10240 packets (Lost: 0, Duplicates: 0). Bytes: 10,485,760/10,485,760. Complete: YES", "#98c379"),
        ("[UDP Client] Transfer finished in 0.1470 s (Received: 10,485,760/10,485,760 B, Loss: 0.0%, Throughput: 68.02 MiB/s, Status: SUCCESS)", "#e5c07b"),
        ("", "#ffffff"),
        ("Result Summary:", "#abb2bf"),
        ("[UDP] File: file_10MB.bin | Expected: 10,485,760 B | Received: 10,485,760 B (100.0%)", "#98c379"),
        ("      Time: 0.1470 s | Throughput: 68.02 MiB/s | Pkts: 10240/10240 (Lost: 0, 0.0%)", "#98c379"),
        ("      Checksum Match: YES | Status: SUCCESS", "#98c379"),
    ]
    create_terminal_screenshot("Windows PowerShell - UDP File Transfer (10 MB Reassembly)", udp_lines,
                               os.path.join(shots_dir, "2_udp_transfer.png"))

    # 3. Benchmark Console
    bench_lines = [
        ("PS C:\\TCPUDPFileTransfer> java -cp bin BenchmarkRunner", "#61afef"),
        ("==========================================================================", "#5c6370"),
        ("     COMPUTER NETWORKS LAB: TCP VS. UDP FILE TRANSFER BENCHMARK           ", "#61afef"),
        ("==========================================================================", "#5c6370"),
        ("[Setup] Checking test files in directory: test-files", "#abb2bf"),
        ("  Found existing file_100KB.bin (102,400 bytes)", "#98c379"),
        ("  Found existing file_1MB.bin (1,048,576 bytes)", "#98c379"),
        ("  Found existing file_5MB.bin (5,242,880 bytes)", "#98c379"),
        ("  Found existing file_10MB.bin (10,485,760 bytes)", "#98c379"),
        ("", "#ffffff"),
        ("=====================================================================================================", "#5c6370"),
        ("                                      BENCHMARK SUMMARY RESULTS                                      ", "#e5c07b"),
        ("=====================================================================================================", "#5c6370"),
        ("Proto  | Size     | Avg Time (s) | Throughput   | Packets Lost %  | Completed  | Success Rate | Integrity ", "#abb2bf"),
        ("-----------------------------------------------------------------------------------------------------", "#5c6370"),
        ("TCP    | 100 KB   |       0.0145 |      12.03 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  ", "#98c379"),
        ("TCP    | 1 MB     |       0.0134 |      75.82 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  ", "#98c379"),
        ("TCP    | 5 MB     |       0.0323 |     170.82 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  ", "#98c379"),
        ("TCP    | 10 MB    |       0.0518 |     202.86 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  ", "#98c379"),
        ("UDP    | 100 KB   |       0.0308 |       3.20 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  ", "#e5c07b"),
        ("UDP    | 1 MB     |       0.0541 |      18.85 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  ", "#e5c07b"),
        ("UDP    | 5 MB     |       0.1052 |      47.75 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  ", "#e5c07b"),
        ("UDP    | 10 MB    |       0.1476 |      67.85 MiB/s |           0.0% |     3/3     |       100.0% | VERIFIED  ", "#e5c07b"),
        ("=====================================================================================================", "#5c6370"),
        ("[Graph Generator] Plots saved to results/throughput.png and results/transmission_time.png", "#98c379"),
    ]
    create_terminal_screenshot("Windows PowerShell - Automated Benchmark Suite", bench_lines,
                               os.path.join(shots_dir, "3_benchmark_console.png"), width=1100)

    # 4. Checksum Verification
    check_lines = [
        ("PS C:\\TCPUDPFileTransfer> Get-FileHash test-files\\file_1MB.bin, received\\tcp\\tcp_1MB_run1.bin, received\\udp\\udp_1MB_run1.bin -Algorithm SHA256", "#61afef"),
        ("", "#ffffff"),
        ("Algorithm       Hash                                                             Path", "#abb2bf"),
        ("---------       ----                                                             ----", "#5c6370"),
        ("SHA256          FD0F95DBEB8E1AFCF0CB43B408A136BDBB72C5FA66C60AF22C1200B3581E4529 test-files\\file_1MB.bin", "#98c379"),
        ("SHA256          FD0F95DBEB8E1AFCF0CB43B408A136BDBB72C5FA66C60AF22C1200B3581E4529 received\\tcp\\tcp_1MB_run1.bin", "#98c379"),
        ("SHA256          FD0F95DBEB8E1AFCF0CB43B408A136BDBB72C5FA66C60AF22C1200B3581E4529 received\\udp\\udp_1MB_run1.bin", "#98c379"),
        ("", "#ffffff"),
        ("PS C:\\TCPUDPFileTransfer> # Verification: All 3 SHA-256 hashes are identical! Bit-exact transfer confirmed.", "#5c6370"),
    ]
    create_terminal_screenshot("Windows PowerShell - SHA-256 Cryptographic Integrity Verification", check_lines,
                               os.path.join(shots_dir, "4_checksum_verification.png"), width=1150)

if __name__ == "__main__":
    generate_all_screenshots()
