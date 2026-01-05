import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import seaborn as sns
from pathlib import Path

# ==========================================
# KONFIGURACJA
# ==========================================
NAZWA_PLIKU = "MonitoringGry.json"

# Jak w JSON nazywa się kolumna z RAM
# (u Ciebie jest ram_uzycie, a w starym kodzie bylo ram_wolne)
KOLUMNA_RAM = "ram_uzycie"

def zrob_wykresy():
    skrypt_dir = Path(__file__).resolve().parent
    sciezka_pliku = skrypt_dir / NAZWA_PLIKU

    print(f"--> Wczytuję plik: {sciezka_pliku}...")
    try:
        # Firebase JSON: { pushId: { ...pola... }, ... }
        df = pd.read_json(sciezka_pliku, orient="index")
        df.index.name = "id_odczytu"
        df = df.reset_index()
    except FileNotFoundError:
        print("BŁĄD: Nie widzę pliku! Sprawdź nazwę/ścieżkę.")
        return
    except ValueError as e:
        print("BŁĄD: Problem z formatem JSON.")
        print(e)
        return

    # Upewnij się, że RAM istnieje — jeśli masz stare pliki, zmapuj nazwę
    if "ram_wolne" in df.columns and KOLUMNA_RAM not in df.columns:
        df = df.rename(columns={"ram_wolne": KOLUMNA_RAM})

    wymagane = ["czas", "bateria_poziom", "bateria_temp", "cpu_freq", KOLUMNA_RAM]
    brakujace = [c for c in wymagane if c not in df.columns]
    if brakujace:
        print("BŁĄD: Brakuje kolumn w JSON:", brakujace)
        print("Dostępne kolumny:", list(df.columns))
        return

    # Konwersje typów (najważniejsze: RAM i czas)
    num_cols = ["bateria_poziom", "bateria_temp", "cpu_freq", KOLUMNA_RAM, "czas"]
    for c in num_cols:
        df[c] = pd.to_numeric(df[c], errors="coerce")

    # czas -> datetime
    df["data"] = pd.to_datetime(df["czas"], unit="ms", errors="coerce")
    df = df.dropna(subset=["data"]).sort_values("data")

    # Usuwamy "dziury" w danych (NaN) jeśli jakieś wyszły po konwersji
    df = df.dropna(subset=["bateria_poziom", "bateria_temp", "cpu_freq", KOLUMNA_RAM])

    print(f"--> Znaleziono {len(df)} punktów pomiarowych. Rysuję wykresy...")

    # Statystyki do opisów
    num_points = len(df)
    avg_battery_level = df["bateria_poziom"].mean()
    avg_battery_temp = df["bateria_temp"].mean()
    avg_cpu_freq = df["cpu_freq"].mean()
    avg_ram = df[KOLUMNA_RAM].mean()

    # Styl
    sns.set_palette("husl")
    sns.set_style("whitegrid", {"grid.color": ".8", "grid.linestyle": "--"})
    plt.rcParams["font.family"] = "DejaVu Sans"
    plt.rcParams["font.size"] = 12

    def ustaw_os_czasu(ax):
        ax.xaxis.set_major_formatter(mdates.DateFormatter("%H:%M"))
        ax.set_xlim(df["data"].min(), df["data"].max())
        ax.margins(x=0)  # brak “luzu” po bokach
        plt.setp(ax.get_xticklabels(), rotation=45, ha="right")

    # 1) Bateria poziom
    fig1, ax1 = plt.subplots(figsize=(14, 8))
    fig1.suptitle(f"Poziom Baterii - {NAZWA_PLIKU}", fontsize=18, fontweight="bold")
    fig1.text(
        0.5, 0.92,
        f"Liczba punktów: {num_points} | Średni poziom: {avg_battery_level:.1f}%",
        ha="center", va="bottom", fontsize=14, style="italic", color="dimgray"
    )
    ax1.plot(df["data"], df["bateria_poziom"], linewidth=2.5, alpha=0.85, label="Poziom baterii (%)")
    ax1.fill_between(df["data"], df["bateria_poziom"], alpha=0.10)
    ax1.set_title("Poziom baterii w czasie", fontweight="bold")
    ax1.set_ylabel("Poziom (%)")
    ax1.set_xlabel("Czas (HH:MM)")
    ax1.legend(loc="upper right")
    ustaw_os_czasu(ax1)
    ax1.set_ylim(df["bateria_poziom"].min() - 2, df["bateria_poziom"].max() + 2)
    plt.savefig(skrypt_dir / "wykres_bateria_poziom.png", dpi=300, bbox_inches="tight")
    print("--> Zapisano: wykres_bateria_poziom.png")

    # 2) Temperatura baterii
    fig2, ax2 = plt.subplots(figsize=(14, 8))
    fig2.suptitle(f"Temperatura Baterii - {NAZWA_PLIKU}", fontsize=18, fontweight="bold")
    fig2.text(
        0.5, 0.92,
        f"Liczba punktów: {num_points} | Średnia temperatura: {avg_battery_temp:.1f}°C",
        ha="center", va="bottom", fontsize=14, style="italic", color="dimgray"
    )
    ax2.plot(df["data"], df["bateria_temp"], linewidth=2.5, alpha=0.85, color="orange", label="Temperatura (°C)")
    ax2.fill_between(df["data"], df["bateria_temp"], alpha=0.10, color="orange")
    ax2.set_title("Temperatura baterii w czasie", fontweight="bold")
    ax2.set_ylabel("Temperatura (°C)")
    ax2.set_xlabel("Czas (HH:MM)")
    ax2.legend(loc="upper right")
    ustaw_os_czasu(ax2)
    ax2.set_ylim(df["bateria_temp"].min() - 1, df["bateria_temp"].max() + 1)
    plt.savefig(skrypt_dir / "wykres_bateria_temp.png", dpi=300, bbox_inches="tight")
    print("--> Zapisano: wykres_bateria_temp.png")

    # 3) CPU
    fig3, ax3 = plt.subplots(figsize=(14, 8))
    fig3.suptitle(f"Częstotliwość CPU - {NAZWA_PLIKU}", fontsize=18, fontweight="bold")
    fig3.text(
        0.5, 0.92,
        f"Liczba punktów: {num_points} | Średnia częstotliwość CPU: {avg_cpu_freq:.2f} GHz",
        ha="center", va="bottom", fontsize=14, style="italic", color="dimgray"
    )
    ax3.plot(df["data"], df["cpu_freq"], linewidth=2.2, alpha=0.85, color="purple", label="CPU (GHz)")
    ax3.fill_between(df["data"], df["cpu_freq"], alpha=0.10, color="purple")
    ax3.set_title("Częstotliwość CPU w czasie", fontweight="bold")
    ax3.set_ylabel("CPU (GHz)")
    ax3.set_xlabel("Czas (HH:MM)")
    ax3.legend(loc="upper right")
    ustaw_os_czasu(ax3)
    ax3.set_ylim(max(0, df["cpu_freq"].min() - 0.1), df["cpu_freq"].max() + 0.1)
    plt.savefig(skrypt_dir / "wykres_cpu.png", dpi=300, bbox_inches="tight")
    print("--> Zapisano: wykres_cpu.png")

    # 4) RAM (użycie)
    fig4, ax4 = plt.subplots(figsize=(14, 8))
    fig4.suptitle(f"Użycie RAM - {NAZWA_PLIKU}", fontsize=18, fontweight="bold")
    fig4.text(
        0.5, 0.92,
        f"Liczba punktów: {num_points} | Średnie użycie RAM: {avg_ram:.0f} MB",
        ha="center", va="bottom", fontsize=14, style="italic", color="dimgray"
    )
    ax4.plot(df["data"], df[KOLUMNA_RAM], linewidth=2.2, alpha=0.85, color="teal", label="RAM użycie (MB)")
    ax4.fill_between(df["data"], df[KOLUMNA_RAM], alpha=0.10, color="teal")
    ax4.set_title("Użycie RAM w czasie", fontweight="bold")
    ax4.set_ylabel("RAM (MB)")
    ax4.set_xlabel("Czas (HH:MM)")
    ax4.legend(loc="upper right")
    ustaw_os_czasu(ax4)

    # Skala Y “na styk”, bez dziwnej pustki
    ymin = df[KOLUMNA_RAM].min()
    ymax = df[KOLUMNA_RAM].max()
    pad = max(10, (ymax - ymin) * 0.05)  # 5% zakresu albo min 10MB
    ax4.set_ylim(max(0, ymin - pad), ymax + pad)

    plt.savefig(skrypt_dir / "wykres_ram.png", dpi=300, bbox_inches="tight")
    print("--> Zapisano: wykres_ram.png")

    print("--> Gotowe! Sprawdź folder.")

if __name__ == "__main__":
    zrob_wykresy()