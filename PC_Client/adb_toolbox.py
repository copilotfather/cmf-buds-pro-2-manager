#!/usr/bin/env python3
"""
ADB Toolbox // PC control panel for Android phone.
Includes battery, volume, reboot, screenshot and custom shell commands.
"""

import os
import subprocess
import threading
import tkinter as tk
from tkinter import ttk, messagebox, filedialog

ADB = None


def find_adb():
    import shutil
    adb = shutil.which("adb")
    if adb:
        return adb
    local = os.path.join(os.path.dirname(__file__), "platform-tools", "adb.exe")
    if os.path.exists(local):
        return local
    return None


class AdbToolbox:
    def __init__(self, root):
        self.root = root
        self.root.title("ADB Toolbox // Phone Manager")
        self.root.geometry("620x520")
        self.root.resizable(False, False)

        self.status_var = tk.StringVar(value="Disconnected")
        self.battery_var = tk.StringVar(value="—")
        self.build_ui()
        self.refresh()
        self.auto_refresh()

    def build_ui(self):
        pad = 10
        frame = ttk.Frame(self.root, padding=pad)
        frame.pack(fill="both", expand=True)

        ttk.Label(frame, text="ADB Toolbox // Phone Manager", font=("Segoe UI", 14, "bold")).pack(pady=(0, 8))

        # Status
        st = ttk.LabelFrame(frame, text="Device", padding=8)
        st.pack(fill="x", pady=6)
        ttk.Label(st, text="Status:").grid(row=0, column=0, sticky="w")
        ttk.Label(st, textvariable=self.status_var).grid(row=0, column=1, sticky="w", padx=8)
        ttk.Label(st, text="Battery (x10 scale):").grid(row=1, column=0, sticky="w")
        ttk.Label(st, textvariable=self.battery_var).grid(row=1, column=1, sticky="w", padx=8)
        ttk.Button(st, text="Refresh", command=self.refresh).grid(row=2, column=0, columnspan=2, pady=(6, 0))

        # Quick actions
        qa = ttk.LabelFrame(frame, text="Quick actions", padding=8)
        qa.pack(fill="x", pady=6)
        buttons = [
            ("Max volume", self.max_volume),
            ("Reboot", self.reboot),
            ("Screenshot", self.screenshot),
            ("Battery x10", self.battery_x10),
            ("List packages", self.list_packages),
        ]
        for i, (label, cmd) in enumerate(buttons):
            ttk.Button(qa, text=label, command=cmd).grid(row=i // 3, column=i % 3, padx=4, pady=4, sticky="ew")
        for c in range(3):
            qa.columnconfigure(c, weight=1)

        # Shell
        sh = ttk.LabelFrame(frame, text="Custom ADB shell command", padding=8)
        sh.pack(fill="x", pady=6)
        self.cmd_entry = ttk.Entry(sh, width=70)
        self.cmd_entry.pack(fill="x")
        ttk.Button(sh, text="Run", command=self.run_shell).pack(pady=(6, 0))

        # Log
        logf = ttk.LabelFrame(frame, text="Log", padding=8)
        logf.pack(fill="both", expand=True, pady=6)
        self.log = tk.Text(logf, height=10, state="disabled", wrap="word")
        self.log.pack(fill="both", expand=True)

    def log_msg(self, msg):
        self.log.configure(state="normal")
        self.log.insert("end", msg + "\n")
        self.log.see("end")
        self.log.configure(state="disabled")

    def adb(self, *args):
        adb = find_adb()
        if not adb:
            messagebox.showerror("ADB", "ADB not found.")
            return None
        try:
            return subprocess.run([adb, *args], capture_output=True, text=True)
        except Exception as e:
            self.log_msg(f"ADB error: {e}")
            return None

    def refresh(self):
        res = self.adb("devices")
        if res is None:
            return
        lines = [l for l in res.stdout.splitlines() if "\tdevice" in l]
        if lines:
            self.status_var.set("Connected: " + lines[0].split()[0])
            self.log_msg("Device connected.")
            self.update_battery()
        else:
            self.status_var.set("No device")
            self.log_msg("No device found. Enable USB debugging.")

    def update_battery(self):
        res = self.adb("shell", "dumpsys", "battery")
        if not res:
            return
        level = None
        for line in res.stdout.splitlines():
            if "level:" in line:
                level = line.split(":", 1)[-1].strip()
                break
        if level is not None:
            self.battery_var.set(f"{int(level) * 10}%  (raw {level}%)")
            self.log_msg(f"Battery: {level}% -> scaled {int(level) * 10}%")
        else:
            self.log_msg("Battery level not found.")

    def auto_refresh(self):
        self.update_battery()
        self.root.after(5000, self.auto_refresh)

    def battery_x10(self):
        self.update_battery()
        messagebox.showinfo("Battery", "Battery level scaled to thousandths (x10).")

    def max_volume(self):
        self.adb("shell", "media", "volume", "--set", "15")
        self.adb("shell", "settings", "put", "system", "volume_music", "15")
        self.log_msg("Max volume sent.")

    def reboot(self):
        if messagebox.askyesno("Reboot", "Reboot phone now?"):
            self.adb("reboot")
            self.log_msg("Reboot command sent.")

    def screenshot(self):
        path = filedialog.asksaveasfilename(defaultextension=".png", filetypes=[("PNG", "*.png")])
        if not path:
            return
        self.adb("shell", "screencap", "-p", "/sdcard/screen.png")
        self.adb("pull", "/sdcard/screen.png", path)
        self.log_msg(f"Screenshot saved: {path}")

    def list_packages(self):
        res = self.adb("shell", "pm", "list", "packages")
        if res:
            pkgs = res.stdout.strip().splitlines()
            self.log_msg(f"Installed packages: {len(pkgs)}")
            for p in pkgs[:20]:
                self.log_msg(p)

    def run_shell(self):
        cmd = self.cmd_entry.get().strip()
        if not cmd:
            return
        def worker():
            res = self.adb("shell", *cmd.split())
            if res:
                self.log_msg(f"$ {cmd}")
                self.log_msg(res.stdout.strip() or "(no output)")
                if res.stderr.strip():
                    self.log_msg("ERR: " + res.stderr.strip())
        threading.Thread(target=worker, daemon=True).start()


if __name__ == "__main__":
    root = tk.Tk()
    AdbToolbox(root)
    root.mainloop()