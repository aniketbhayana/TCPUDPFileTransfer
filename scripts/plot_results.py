import os
import sys
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

def generate_plots():
    results_dir = os.path.join(os.path.dirname(__file__), "..", "results")
    summary_path = os.path.join(results_dir, "summary.csv")
    raw_path = os.path.join(results_dir, "results.csv")

    if not os.path.exists(summary_path):
        print(f"[Plotting Error] {summary_path} does not exist.")
        return False

    df_summary = pd.read_csv(summary_path)
    df_raw = pd.read_csv(raw_path) if os.path.exists(raw_path) else None

    # Style settings for clean, academic aesthetic
    plt.style.use('seaborn-v0_8-whitegrid' if 'seaborn-v0_8-whitegrid' in plt.style.available else 'default')
    plt.rcParams['font.family'] = 'sans-serif'
    plt.rcParams['font.size'] = 11
    plt.rcParams['axes.labelsize'] = 12
    plt.rcParams['axes.titlesize'] = 14
    plt.rcParams['xtick.labelsize'] = 11
    plt.rcParams['ytick.labelsize'] = 11
    plt.rcParams['legend.fontsize'] = 11
    plt.rcParams['figure.titlesize'] = 16

    file_sizes = ["100 KB", "1 MB", "5 MB", "10 MB"]

    # Filter data by protocol
    tcp_summary = df_summary[df_summary['Protocol'] == 'TCP'].set_index('FileSize_Label').reindex(file_sizes).reset_index()
    udp_summary = df_summary[df_summary['Protocol'] == 'UDP'].set_index('FileSize_Label').reindex(file_sizes).reset_index()

    # -------------------------------------------------------------
    # 1. TRANSMISSION TIME PLOT
    # -------------------------------------------------------------
    fig, ax = plt.subplots(figsize=(9, 6), dpi=300)
    x = np.arange(len(file_sizes))
    width = 0.35

    tcp_times = tcp_summary['Avg_Time_Seconds'].values
    udp_times = udp_summary['Avg_Time_Seconds'].values

    bars_tcp = ax.bar(x - width/2, tcp_times, width, label='TCP (Reliable Stream)', color='#1f77b4', edgecolor='black', alpha=0.9)
    bars_udp = ax.bar(x + width/2, udp_times, width, label='UDP (Datagram)', color='#ff7f0e', edgecolor='black', alpha=0.9)

    # Annotate bars with values and completion status
    for i, bar in enumerate(bars_tcp):
        val = bar.get_height()
        if not np.isnan(val):
            ax.annotate(f'{val:.3f}s',
                        xy=(bar.get_x() + bar.get_width() / 2, val),
                        xytext=(0, 4), textcoords="offset points",
                        ha='center', va='bottom', fontsize=10, fontweight='bold', color='#1f77b4')

    for i, bar in enumerate(bars_udp):
        val = bar.get_height()
        comp_rate = udp_summary.iloc[i]['Success_Rate_Pct']
        is_comp = (comp_rate == 100.0)
        label_text = f'{val:.3f}s' if is_comp else f'{val:.3f}s*\n({comp_rate:.0f}% OK)'
        if not np.isnan(val):
            ax.annotate(label_text,
                        xy=(bar.get_x() + bar.get_width() / 2, val),
                        xytext=(0, 4), textcoords="offset points",
                        ha='center', va='bottom', fontsize=9, fontweight='bold',
                        color='#ff7f0e' if is_comp else '#d62728')

    ax.set_xlabel('File Size', fontweight='bold', labelpad=10)
    ax.set_ylabel('Transmission Time (Seconds)', fontweight='bold', labelpad=10)
    ax.set_title('Transmission Time Comparison: TCP vs. UDP\n(Localhost, Averaged over 3 Runs)', pad=15)
    ax.set_xticks(x)
    ax.set_xticklabels(file_sizes)
    ax.legend(frameon=True, facecolor='white', framealpha=0.9)
    ax.set_ylim(0, max(np.nanmax(tcp_times), np.nanmax(udp_times)) * 1.25)

    fig.text(0.12, 0.02, '* Note: Red/parenthesized annotations indicate incomplete UDP transfers due to socket buffer overflow.',
             fontsize=9, style='italic', color='#555555')

    plt.tight_layout(rect=[0, 0.04, 1, 1])
    time_plot_path = os.path.join(results_dir, "transmission_time.png")
    plt.savefig(time_plot_path)
    plt.close()
    print(f"[Plotting] Generated: {time_plot_path}")

    # -------------------------------------------------------------
    # 2. THROUGHPUT PLOT
    # -------------------------------------------------------------
    fig, ax = plt.subplots(figsize=(9, 6), dpi=300)

    tcp_tput = tcp_summary['Avg_Throughput_MiBps'].values
    udp_tput = udp_summary['Avg_Throughput_MiBps'].values

    bars_tcp2 = ax.bar(x - width/2, tcp_tput, width, label='TCP Throughput (Full Verified File)', color='#2ca02c', edgecolor='black', alpha=0.9)
    bars_udp2 = ax.bar(x + width/2, udp_tput, width, label='UDP Effective Rate (Received Data)', color='#d62728', edgecolor='black', alpha=0.9)

    for i, bar in enumerate(bars_tcp2):
        val = bar.get_height()
        if not np.isnan(val):
            ax.annotate(f'{val:.1f}\nMiB/s',
                        xy=(bar.get_x() + bar.get_width() / 2, val),
                        xytext=(0, 4), textcoords="offset points",
                        ha='center', va='bottom', fontsize=10, fontweight='bold', color='#1b781b')

    for i, bar in enumerate(bars_udp2):
        val = bar.get_height()
        comp_rate = udp_summary.iloc[i]['Success_Rate_Pct']
        is_comp = (comp_rate == 100.0)
        label_text = f'{val:.1f}\nMiB/s' if is_comp else f'{val:.1f}*\n({comp_rate:.0f}% OK)'
        if not np.isnan(val):
            ax.annotate(label_text,
                        xy=(bar.get_x() + bar.get_width() / 2, val),
                        xytext=(0, 4), textcoords="offset points",
                        ha='center', va='bottom', fontsize=9, fontweight='bold',
                        color='#d62728' if not is_comp else '#8c564b')

    ax.set_xlabel('File Size', fontweight='bold', labelpad=10)
    ax.set_ylabel('Throughput (MiB/s)', fontweight='bold', labelpad=10)
    ax.set_title('Throughput Comparison: TCP vs. UDP\n(Localhost, Averaged over 3 Runs)', pad=15)
    ax.set_xticks(x)
    ax.set_xticklabels(file_sizes)
    ax.legend(frameon=True, facecolor='white', framealpha=0.9)
    ax.set_ylim(0, max(np.nanmax(tcp_tput), np.nanmax(udp_tput)) * 1.3)

    fig.text(0.12, 0.02, '* UDP rate for incomplete transfers represents effective received data rate; transfer was NOT 100% successful.',
             fontsize=9, style='italic', color='#555555')

    plt.tight_layout(rect=[0, 0.04, 1, 1])
    tput_plot_path = os.path.join(results_dir, "throughput.png")
    plt.savefig(tput_plot_path)
    plt.close()
    print(f"[Plotting] Generated: {tput_plot_path}")
    return True

if __name__ == "__main__":
    generate_plots()
