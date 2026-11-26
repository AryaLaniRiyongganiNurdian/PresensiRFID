#!/usr/bin/env python3
"""Generator log_presensi. Pemakaian: python3 generate_log.py seed_mahasiswa.sql folder_output [jumlah]"""
import random, re, sys, os
from datetime import datetime, timedelta
src = sys.argv[1] if len(sys.argv) > 1 else "seed_mahasiswa.sql"
out = sys.argv[2] if len(sys.argv) > 2 else "."
N = int(sys.argv[3]) if len(sys.argv) > 3 else 2_000_000
os.makedirs(out, exist_ok=True)
rnd = random.Random()
nims = ["L0325002", "L0325043", "L0325003", "L0325025", "L0325041", "L0325010",
        "L0325004", "L0325026", "L0325011", "L0325036", "L0325046"]
pat = re.compile(r"^\('([^']+)'")
with open(src, encoding="utf-8") as f:
    for line in f:
        if line.startswith("("):
            nims.append(pat.match(line).group(1))
print("NIM tersedia:", len(nims))
start = datetime(2025, 1, 1)
span = int((datetime(2026, 10, 29) - start).total_seconds())
PARTS = 2
per = -(-N // PARTS)
BATCH = 3000000
done = 0
for p in range(PARTS):
    cnt = min(per, N - done)
    with open(f"{out}/seed_log_presensi_bagian{p+1}.sql", "w", encoding="utf-8") as o:
        o.write(f"-- Data acak log_presensi, bagian {p+1} dari {PARTS}\nUSE db_absensi;\nSET autocommit = 0;\n\n")
        head = "INSERT INTO log_presensi (nim, id_ruangan, waktu_masuk, waktu_keluar, durasi_kunjungan) VALUES\n"
        buf = []
        for i in range(cnt):
            while True:
                t = start + timedelta(seconds=rnd.randrange(span))
                # utamakan hari kerja & jam 07:00-20:59
                if t.weekday() < 5 or rnd.random() < 0.15:
                    t = t.replace(hour=rnd.randint(7, 20))
                    break
            dur = int(min(max(rnd.lognormvariate(8.3, 0.8), 60), 8 * 3600))  # detik, median ~67 menit
            k = t + timedelta(seconds=dur)
            room = "17" if rnd.random() < 0.55 else "LAB"
            h, m, s = dur // 3600, dur % 3600 // 60, dur % 60
            buf.append(f"('{rnd.choice(nims)}','{room}','{t:%Y-%m-%d %H:%M:%S}','{k:%Y-%m-%d %H:%M:%S}','{h:02d}:{m:02d}:{s:02d}')")
            if len(buf) == BATCH:
                o.write(head + ",\n".join(buf) + ";\n"); buf = []
                if ((i + 1) // BATCH) % 20 == 0: o.write("COMMIT;\n")
        if buf: o.write(head + ",\n".join(buf) + ";\n")
        o.write("COMMIT;\n")
    done += cnt
print("selesai", done)
