import os
import subprocess
import shutil

def generate_pdf():
    base_dir = os.path.dirname(__file__)
    project_dir = os.path.abspath(os.path.join(base_dir, ".."))
    results_dir = os.path.join(project_dir, "results")
    html_path = os.path.join(results_dir, "lab_report.html")
    pdf_path = os.path.join(results_dir, "Lab_Report_TCP_vs_UDP_File_Transfer.pdf")
    root_pdf_path = os.path.join(project_dir, "Lab_Report_TCP_vs_UDP_File_Transfer.pdf")

    edge_exe = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
    user_data_dir = os.path.join(project_dir, "edge-temp")

    cmd = [
        edge_exe,
        "--headless",
        "--disable-gpu",
        f"--user-data-dir={user_data_dir}",
        f"--print-to-pdf={pdf_path}",
        html_path
    ]

    print("[PDF Generator] Compiling Lab Report PDF via Headless Edge...")
    res = subprocess.run(cmd, capture_output=True, text=True)

    if os.path.exists(user_data_dir):
        shutil.rmtree(user_data_dir, ignore_errors=True)

    if os.path.exists(pdf_path) and os.path.getsize(pdf_path) > 0:
        shutil.copy(pdf_path, root_pdf_path)
        print(f"[PDF Generator] SUCCESS!")
        print(f"  Generated: {pdf_path} ({os.path.getsize(pdf_path):,} bytes)")
        print(f"  Copied to root: {root_pdf_path}")
        return True
    else:
        print(f"[PDF Generator] Failed! {res.stdout}\n{res.stderr}")
        return False

if __name__ == "__main__":
    generate_pdf()
