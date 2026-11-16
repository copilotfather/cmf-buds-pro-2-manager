#!/usr/bin/env python3
"""
CMF Buds Pro 2 // PC Manager (GUI)
Desktop control panel for USB audio relay to CMF Buds Pro 2.
"""

import os
import queue
import socket
import subprocess
import threading
import time
import tkinter as tk
from tkinter import ttk, messagebox

import numpy as np
import soundcard as sc

HOST = "127.0.0.1"
PORT = 4242
SAMPLE_RATE = 48000
CHANNELS = 2
CHUNK_SIZE = 1024


class CmfPcManager:
    def __init__(self, root):
        self.root = root
        self.root.title("CMF Buds Pro 2 // PC Manager")
        self.root.geometry("520x420")
        self.root.resizable(False, False)

        self.sock = None
        self.running = False
        self.worker = None
        self.kbps = 0.0
        self.ui_queue = queue.Queue()

        self.device_var = tk.StringVar()
        self.status_var = tk.StringVar(value="Disconnected")
        self.kbps_var = tk.StringVar(value="0.0 kbps")

        self.build_ui()
        self.refresh_devices()
        self.root.after(50, self._process_ui_events)

    def _process_ui_events(self):
        while True:
            try:
                callback, args = self.ui_queue.get_nowait()
            except queue.Empty:
                break
            callback(*args)
        try:
            self.root.after(50, self._process_ui_events)
        except tk.TclError:
            pass

    def _dispatch_to_ui(self, callback, *args):
        self.ui_queue.put((callback, args))

    def build_ui(self):
        pad = 10
        frame = ttk.Frame(self.root, padding=pad)
        frame.pack(fill="both", expand=True)

        ttk.Label(frame, text="CMF Buds Pro 2 PC Manager", font=("Segoe UI", 14, "bold")).pack(pady=(0, 8))

        # Device selector
        dev_frame = ttk.LabelFrame(frame, text="Audio source (Windows loopback)", padding=8)
        dev_frame.pack(fill="x", pady=6)
        self.device_combo = ttk.Combobox(dev_frame, textvariable=self.device_var, state="readonly", width=60)
        self.device_combo.pack(fill="x")
        ttk.Button(dev_frame, text="Refresh devices", command=self.refresh_devices).pack(pady=(6, 0))

        # Status
        status_frame = ttk.LabelFrame(frame, text="Status", padding=8)
        status_frame.pack(fill="x", pady=6)
        ttk.Label(status_frame, text="Connection:").grid(row=0, column=0, sticky="w")
        ttk.Label(status_frame, textvariable=self.status_var).grid(row=0, column=1, sticky="w", padx=8)
        ttk.Label(status_frame, text="Speed:").grid(row=1, column=0, sticky="w")
        ttk.Label(status_frame, textvariable=self.kbps_var).grid(row=1, column=1, sticky="w", padx=8)

        # Controls
        btn_frame = ttk.Frame(frame, padding=8)
        btn_frame.pack(fill="x", pady=6)
        self.start_btn = ttk.Button(btn_frame, text="Start relay", command=self.start)
        self.start_btn.pack(side="left", padx=4)
        self.stop_btn = ttk.Button(btn_frame, text="Stop", command=self.stop, state="disabled")
        self.stop_btn.pack(side="left", padx=4)
        ttk.Button(btn_frame, text="ADB forward", command=self.adb_forward).pack(side="left", padx=4)
        ttk.Button(btn_frame, text="Max vol ADB", command=self.max_volume_adb).pack(side="left", padx=4)
        ttk.Button(btn_frame, text="Phone battery", command=self.phone_battery_adb).pack(side="left", padx=4)

        # Log
        log_frame = ttk.LabelFrame(frame, text="Log", padding=8)
        log_frame.pack(fill="both", expand=True, pady=6)
        self.log = tk.Text(log_frame, height=8, state="disabled", wrap="word")
        self.log.pack(fill="both", expand=True)

    def log_msg(self, msg):
        if threading.current_thread() is not threading.main_thread():
            self._dispatch_to_ui(self.log_msg, msg)
            return
        self.log.configure(state="normal")
        self.log.insert("end", msg + "\n")
        self.log.see("end")
        self.log.configure(state="disabled")

    def refresh_devices(self):
        try:
            mics = sc.all_microphones(include_loopback=True)
            names = [m.name for m in mics]
            self.device_combo["values"] = names
            if names:
                self.device_var.set(names[0])
            self.log_msg(f"Found {len(names)} loopback devices.")
        except Exception as e:
            self.log_msg(f"Device scan error: {e}")

    def adb_forward(self):
        adb = self.find_adb()
        if not adb:
            messagebox.showerror("ADB", "ADB not found. Install Android Platform Tools.")
            return
        try:
            subprocess.run([adb, "forward", "tcp:4242", "tcp:4242"], check=False)
            self.log_msg("ADB forward tcp:4242 -> phone done.")
        except Exception as e:
            self.log_msg(f"ADB error: {e}")

    def find_adb(self):
        import shutil
        adb = shutil.which("adb")
        if adb:
            return adb
        local = os.path.join(os.path.dirname(__file__), "platform-tools", "adb.exe")
        if os.path.exists(local):
            return local
        return None

    def max_volume_adb(self):
        adb = self.find_adb()
        if not adb:
            self.log_msg("ADB not found.")
            return
        try:
            subprocess.run([adb, "shell", "media", "volume", "--set", "15"], check=False)
            subprocess.run([adb, "shell", "settings", "put", "system", "volume_music", "15"], check=False)
            self.log_msg("Max volume command sent via ADB.")
        except Exception as e:
            self.log_msg(f"Volume ADB error: {e}")

    def phone_battery_adb(self):
        adb = self.find_adb()
        if not adb:
            self.log_msg("ADB not found.")
            return
        try:
            out = subprocess.check_output([adb, "shell", "dumpsys", "battery"], text=True, stderr=subprocess.DEVNULL)
            level = None
            for line in out.splitlines():
                if "level:" in line:
                    level = line.split(":", 1)[-1].strip()
                    break
            if level is not None:
                self.log_msg(f"Phone battery: {level}%")
                self.status_var.set(f"Phone battery: {level}%")
            else:
                self.log_msg("Battery level not found.")
        except Exception as e:
            self.log_msg(f"Battery ADB error: {e}")

    def connect(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            s.connect((HOST, PORT))
            self.log_msg("Connected to phone.")
            return s
        except Exception as e:
            self.log_msg(f"Connect error: {e}")
            return None

    def start(self):
        if self.running:
            return
        sock = self.connect()
        if not sock:
            return
        self.sock = sock
        self.running = True
        self.status_var.set("Streaming")
        self.start_btn.config(state="disabled")
        self.stop_btn.config(state="normal")
        self.worker = threading.Thread(target=self.stream_loop, daemon=True)
        self.worker.start()

    def stop(self):
        if threading.current_thread() is not threading.main_thread():
            self._dispatch_to_ui(self.stop)
            return
        if not self.running and self.sock is None:
            return
        self.running = False
        try:
            if self.sock:
                self.sock.close()
        except Exception:
            pass
        self.sock = None
        self.status_var.set("Stopped")
        self.start_btn.config(state="normal")
        self.stop_btn.config(state="disabled")
        self.log_msg("Relay stopped.")

    def stream_loop(self):
        stream_socket = self.sock
        try:
            mic = None
            for m in sc.all_microphones(include_loopback=True):
                if m.name == self.device_var.get():
                    mic = m
                    break
            if mic is None:
                mics = sc.all_microphones(include_loopback=True)
                if mics:
                    mic = mics[0]
            if mic is None:
                raise RuntimeError("No loopback device")

            self.log_msg(f"Capturing: {mic.name}")
            bytes_total = 0
            last_timer = time.time()

            with mic.recorder(samplerate=SAMPLE_RATE, channels=CHANNELS) as rec:
                while self.running:
                    data = rec.record(numframes=CHUNK_SIZE)
                    pcm = np.clip(data * 32767.0, -32768, 32767).astype(np.int16)
                    payload = pcm.tobytes()
                    if stream_socket is None:
                        break
                    stream_socket.sendall(payload)
                    bytes_total += len(payload)
                    now = time.time()
                    if now - last_timer >= 1.0:
                        self.kbps = (bytes_total * 8.0) / 1024.0
                        self._dispatch_to_ui(self.kbps_var.set, f"{self.kbps:.1f} kbps")
                        bytes_total = 0
                        last_timer = now
        except Exception as e:
            self.log_msg(f"Stream error: {e}")
        finally:
            self.stop()


if __name__ == "__main__":
    root = tk.Tk()
    app = CmfPcManager(root)
    root.mainloop()
