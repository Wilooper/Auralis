#!/usr/bin/env python3
"""Build-time test data only. Python is not part of the Auralis application."""
import math
import struct
import zipfile
from pathlib import Path


def integer(value, syncsafe=False):
    return bytes((value >> (shift * (7 if syncsafe else 8))) & (127 if syncsafe else 255) for shift in (3, 2, 1, 0))


def id3(frame_id, payload):
    frame = frame_id.encode() + integer(len(payload), True) + b"\0\0" + payload
    return b"ID3\x04\0\0" + integer(len(frame), True) + frame


def uslt(text):
    return id3("USLT", b"\x03hin\0" + text.encode())


def sylt():
    data = b"\x03hin\x02\x01\0"
    for text, time in [("प्यार ", 1000), ("का ", 1600), ("गीत", 2200), ("\nHello ", 3500), ("Auralis", 4300)]:
        data += text.encode() + b"\0" + integer(time)
    return id3("SYLT", data)


def wav(tags):
    rate = 16_000
    pcm = b"".join(struct.pack("<h", round(1000 * math.sin(2 * math.pi * 220 * i / rate))) for i in range(rate * 6))
    def chunk(name, data):
        return name + struct.pack("<I", len(data)) + data + (b"\0" if len(data) % 2 else b"")
    body = b"WAVE" + chunk(b"fmt ", struct.pack("<HHIIHH", 1, 1, rate, rate * 2, 2, 16)) + chunk(b"data", pcm) + chunk(b"id3 ", tags)
    return b"RIFF" + struct.pack("<I", len(body)) + body


if __name__ == "__main__":
    output = Path(__file__).resolve().parent.parent / "fixtures"
    output.mkdir(exist_ok=True)
    samples = {
        "01-plain-USLT.wav": uslt("प्यार का गीत\n\nHello Auralis\nPlain text has no timestamps."),
        "02-line-LRC-USLT.wav": uslt("[00:01.000]प्यार का गीत\n[00:03.500]Hello Auralis"),
        "03-word-SYLT.wav": sylt(),
        "04-word-enhanced-LRC.wav": uslt("[00:01.000]<00:01.000>प्यार <00:01.600>का <00:02.200>गीत<00:03.000>\n[00:03.500]<00:03.500>Hello <00:04.300>Auralis<00:05.500>"),
    }
    for name, tags in samples.items():
        (output / name).write_bytes(wav(tags))
    (output / "README.md").write_text("# Auralis embedded lyrics fixtures\n\nFour six-second generated quiet tones, with original Hindi/English test text; no copyrighted music. These are WAV files with ID3v2.4 chunks.\n\nImport this entire folder, play each file and open Lyrics. Expected modes: 01 Plain lyrics; 02 Line sync; 03 Word sync (SYLT); 04 Word sync (enhanced LRC). Timed lyrics begin at 1 second; the second line begins at 3.5 seconds. In 04, the final word expires at 5.5 seconds. Try pausing and seeking backward.\n\nCopy the folder into a music subfolder allowed by Android's folder picker. Add a nested folder and a non-audio file to check recursive scanning and filtering. Reimport to check deduplication. These are metadata/display fixtures, not a comprehensive codec test.\n", encoding="utf-8")
    archive = output.parent.parent / "auralis-deliverables" / "Auralis-Lyrics-Test-Files.zip"
    archive.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        for path in sorted(output.iterdir()):
            bundle.write(path, "Auralis-Lyrics-Fixtures/" + path.name)
    print(archive)
