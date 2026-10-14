# ==============================================================================
#        COMPUTER NETWORKS LAB: TCP VS. UDP FILE TRANSFER BENCHMARK (PowerShell)
# ==============================================================================

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "        COMPUTER NETWORKS LAB: TCP VS. UDP FILE TRANSFER BENCHMARK" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Check Java Installation
try {
    $javaVer = java -version 2>&1
    Write-Host "[OK] Java runtime detected." -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Java is not installed or not in system PATH." -ForegroundColor Red
    Write-Host "Please install OpenJDK 17 or later." -ForegroundColor Yellow
    exit 1
}

# 2. Ensure directories exist
$dirs = @("bin", "test-files", "received\tcp", "received\udp", "results")
foreach ($d in $dirs) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
}

# 3. Compile Java Source Code
Write-Host "[1/3] Compiling Java source files from src\ ..." -ForegroundColor Yellow
javac -d bin (Get-ChildItem -Path src\*.java | ForEach-Object { $_.FullName })
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed!" -ForegroundColor Red
    exit 1
}
Write-Host "[SUCCESS] Compilation succeeded! Classes placed in bin\ directory." -ForegroundColor Green
Write-Host ""

# 4. Interactive Menu
function Show-Menu {
    Write-Host "------------------------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "Select an action to execute:"
    Write-Host "  1. Run Full Automated Benchmark (Generates files, CSVs & Graphs)"
    Write-Host "  2. Start TCP Server (Default port 8888)"
    Write-Host "  3. Run TCP Client (Send a file over TCP)"
    Write-Host "  4. Start UDP Server (Default port 8889)"
    Write-Host "  5. Run UDP Client (Send a file over UDP)"
    Write-Host "  6. Generate Deterministic Test Files (100KB, 1MB, 5MB, 10MB)"
    Write-Host "  7. Re-generate Plot Images (Python matplotlib)"
    Write-Host "  8. Open Results Directory"
    Write-Host "  9. Exit"
    Write-Host "------------------------------------------------------------------------------" -ForegroundColor Cyan
    
    $choice = Read-Host "Enter your choice [1-9]"
    switch ($choice) {
        "1" {
            Write-Host "`nStarting automated benchmark..." -ForegroundColor Yellow
            java -cp bin BenchmarkRunner
            Pause-Prompt
            Show-Menu
        }
        "2" {
            Write-Host "`nStarting TCP Server on port 8888..." -ForegroundColor Yellow
            java -cp bin TCPServer 8888 received\tcp
            Pause-Prompt
            Show-Menu
        }
        "3" {
            $fp = Read-Host "Enter file path to transmit (e.g. test-files\file_1MB.bin)"
            java -cp bin TCPClient $fp localhost 8888
            Pause-Prompt
            Show-Menu
        }
        "4" {
            Write-Host "`nStarting UDP Server on port 8889..." -ForegroundColor Yellow
            java -cp bin UDPServer 8889 received\udp
            Pause-Prompt
            Show-Menu
        }
        "5" {
            $fp = Read-Host "Enter file path to transmit (e.g. test-files\file_1MB.bin)"
            $delay = Read-Host "Enter inter-packet delay in microseconds (0 for full speed)"
            if ([string]::IsNullOrWhiteSpace($delay)) { $delay = "0" }
            java -cp bin UDPClient $fp localhost 8889 $delay
            Pause-Prompt
            Show-Menu
        }
        "6" {
            Write-Host "`nGenerating deterministic test files..." -ForegroundColor Yellow
            java -cp bin Main generate
            Pause-Prompt
            Show-Menu
        }
        "7" {
            Write-Host "`nGenerating plots..." -ForegroundColor Yellow
            python scripts\plot_results.py
            Pause-Prompt
            Show-Menu
        }
        "8" {
            Invoke-Item "results"
            Show-Menu
        }
        "9" {
            Write-Host "Exiting. Goodbye!" -ForegroundColor Green
            exit 0
        }
        default {
            Write-Host "Invalid choice. Please enter 1-9." -ForegroundColor Red
            Show-Menu
        }
    }
}

function Pause-Prompt {
    Write-Host ""
    Read-Host "Press Enter to return to menu..."
}

Show-Menu
