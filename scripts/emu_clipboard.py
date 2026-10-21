#!/usr/bin/env python3
"""
macOS <-> Android Emulator Clipboard Bridge
Enables seamless copy-paste between macOS and Android emulator.
"""

import sys
import os
import time
import subprocess
import argparse

ADB_PATH = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
if not os.path.exists(ADB_PATH):
    ADB_PATH = "adb"


def get_target_device():
    env_dev = os.environ.get("ANDROID_SERIAL")
    if env_dev:
        return env_dev

    res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True)
    devices = []
    for line in res.stdout.strip().split("\n")[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])

    if not devices:
        print("Hata: Hiçbir Android cihaz veya emülatör bağlı değil.", file=sys.stderr)
        sys.exit(1)

    # Prefer emulator if available
    for d in devices:
        if d.startswith("emulator-"):
            return d
    return devices[0]


def get_mac_clipboard():
    res = subprocess.run(["pbpaste"], capture_output=True, text=True)
    return res.stdout


def set_mac_clipboard(text):
    p = subprocess.Popen(["pbcopy"], stdin=subprocess.PIPE)
    p.communicate(input=text.encode("utf-8"))


def set_emulator_clipboard(text, device=None, paste_now=True):
    if not device:
        device = get_target_device()

    # 1. Push to PersonalRecords MainActivity clipboard handler
    subprocess.run([
        ADB_PATH, "-s", device, "shell", "am", "start",
        "-n", "com.personalrecords.app/.MainActivity",
        "--es", "clipboard_text", text
    ], capture_output=True)

    # 2. Also broadcast to ClipboardReceiver
    subprocess.run([
        ADB_PATH, "-s", device, "shell", "am", "broadcast",
        "-n", "com.personalrecords.app/.core.receiver.ClipboardReceiver",
        "-a", "com.personalrecords.app.SET_CLIPBOARD",
        "--es", "text", text
    ], capture_output=True)

    # 3. If paste_now, trigger KEYCODE_PASTE (279) and Ctrl+V (113 50)
    if paste_now:
        time.sleep(0.2)
        subprocess.run([
            ADB_PATH, "-s", device, "shell", "input", "keyevent", "279"
        ], capture_output=True)
        subprocess.run([
            ADB_PATH, "-s", device, "shell", "input", "keycombination", "113", "50"
        ], capture_output=True)


def watch_clipboard(device=None):
    if not device:
        device = get_target_device()
    print(f"[*] macOS -> Android Emülatör Pano Senkronizasyonu Başlatıldı (Hedef: {device})")
    print("[*] macOS'ta kopyaladığınız her metin otomatik olarak emülatörün panosuna aktarılacaktır.")
    print("[*] Durdurmak için Ctrl+C'ye basın.\n")

    last_clip = get_mac_clipboard()
    while True:
        try:
            time.sleep(0.5)
            current = get_mac_clipboard()
            if current and current != last_clip:
                last_clip = current
                preview = (current[:30] + "...") if len(current) > 30 else current
                preview = preview.replace("\n", " ")
                print(f"[✓] Panoya aktarıldı: {preview}")
                set_emulator_clipboard(current, device=device, paste_now=False)
        except KeyboardInterrupt:
            print("\nSenkronizasyon sonlandırıldı.")
            break
        except Exception:
            time.sleep(1)


def main():
    parser = argparse.ArgumentParser(description="macOS <-> Android Emulator Pano Köprüsü")
    parser.add_argument("text", nargs="?", help="Emülatöre yapıştırılacak metin (belirtilmezse macOS panosu kullanılır)")
    parser.add_argument("--sync", "-s", action="store_true", help="Arka planda macOS panosunu sürekli emülatöre senkronize et")
    parser.add_argument("--device", "-d", help="Hedef ADB cihazı (örn: emulator-5554)")
    args = parser.parse_args()

    if args.sync:
        watch_clipboard(device=args.device)
    else:
        text = args.text if args.text is not None else get_mac_clipboard()
        if not text:
            print("Uyarı: Pano boş veya aktarılacak metin bulunamadı.")
            return
        device = args.device or get_target_device()
        set_emulator_clipboard(text, device=device, paste_now=True)
        preview = (text[:30] + "...") if len(text) > 30 else text
        preview = preview.replace("\n", " ")
        print(f"[✓] Emülatöre aktarıldı ve yapıştırıldı: {preview}")


if __name__ == "__main__":
    main()
