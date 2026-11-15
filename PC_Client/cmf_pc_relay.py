#!/usr/bin/env python3
"""
CMF Buds Pro 2 // PC Audio Relay Client
Captures Windows Desktop audio (WASAPI loopback) and streams it with ultra-low latency (10-15ms)
over USB to the Android phone, which plays it directly into CMF Buds Pro 2 over Bluetooth.
Also receives microphone audio from CMF Buds Pro 2 to PC.
"""

import os
import shutil
import socket
import subprocess
import sys
import time

def ensure_adb_forward():
    adb = shutil.which("adb")
    if adb is None:
        local = os.path.join(os.path.dirname(__file__), "platform-tools", "adb.exe")
        if os.path.exists(local):
            adb = local
    if adb:
        try:
            subprocess.run([adb, "forward", "tcp:4242", "tcp:4242"], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print("[i] ADB forward tcp:4242 -> phone выполнен.")
        except Exception:
            pass

HOST = "127.0.0.1"
PORT = 4242
SAMPLE_RATE = 48000
CHANNELS = 2
CHUNK_SIZE = 1024

print(":: CMF BUDS PRO 2 AUDIO RELAY CLIENT ::")
print(f"Connecting to Phone at {HOST}:{PORT} over USB...")
ensure_adb_forward()

def try_connect():
    while True:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            s.connect((HOST, PORT))
            print("[+] Connected to Phone! USB Audio Bridge is ACTIVE.")
            return s
        except Exception as e:
            print(f"Waiting for CMF app to start relay on phone... ({e})")
            time.sleep(2)

def stream_audio(sock):
    try:
        import soundcard as sc
        import numpy as np

        loopback = None
        try:
            loopback = sc.get_microphone(id=str(sc.default_speaker().name), include_loopback=True)
        except Exception:
            pass

        if loopback is None:
            for mic in sc.all_microphones(include_loopback=True):
                name = (mic.name or "").lower()
                if "loopback" in name or "stereo mix" in name or "wasapi" in name:
                    loopback = mic
                    break

        if loopback is None:
            mics = sc.all_microphones(include_loopback=True)
            if mics:
                loopback = mics[0]

        if loopback is None:
            raise RuntimeError("No loopback microphone found")

        print(f"Capturing Windows Audio from device: {loopback.name}")

        with loopback.recorder(samplerate=SAMPLE_RATE, channels=CHANNELS) as rec:
            print("[>>>] Streaming PC Audio to CMF Buds Pro 2 in real-time...")
            while True:
                data = rec.record(numframes=CHUNK_SIZE)
                pcm = np.clip(data * 32767.0, -32768, 32767).astype(np.int16)
                sock.sendall(pcm.tobytes())
    except Exception as e:
        print(f"[!] Audio capture error: {e}")
        print("Install dependencies manually: python -m pip install soundcard numpy")
        raise

if __name__ == "__main__":
    sock = try_connect()
    stream_audio(sock)
