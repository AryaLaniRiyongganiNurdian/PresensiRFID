#!/usr/bin/env python3
"""Generator data SQL acak untuk db_absensi (tabel mahasiswa & dosen).

Pemakaian:
    python3 generate_seed.py [jumlah_mahasiswa] [jumlah_dosen] [folder_output] [seed]
Default: 1.000.000 mahasiswa, 1.000.000 dosen, folder saat ini, seed acak.
"""
import hashlib
import os
import random
import sys

N_MHS = int(sys.argv[1]) if len(sys.argv) > 1 else 1_000_000
N_DSN = int(sys.argv[2]) if len(sys.argv) > 2 else 1_000_000
OUT = sys.argv[3] if len(sys.argv) > 3 else "."
SEED = int(sys.argv[4]) if len(sys.argv) > 4 else None
BATCH = 2000000

rnd = random.Random(SEED)
os.makedirs(OUT, exist_ok=True)

# ---- data yang sudah ada di Query.sql (dihindari agar tidak bentrok) ----
NIM_ADA = {"L0325002", "L0325043", "L0325003", "L0325025", "L0325041", "L0325010",
           "L0325004", "L0325026", "L0325011", "L0325036", "L0325046"}
UID_ADA = {"917B6D66", "30C75955", "FA3D0B85", "3AE70C85", "EA171585", "0A620685",
           "0ACC6D84", "FA830885", "1A1E1185", "EAAE7085", "3AB30A85"}
NIP_ADA = {"0"}
EMAIL_ADA = {"adminkelompok"}

NAMA_DEPAN = """Ahmad Muhammad Budi Siti Dewi Agus Andi Rizky Putri Dian Eko Fajar Hendra Indah Joko
Kartika Lestari Mega Nur Oki Putra Rina Sari Tri Umar Vina Wahyu Yoga Zahra Aditya Bagas Citra Dimas
Eka Farhan Gita Hana Irfan Jihan Kevin Laras Maya Naufal Olivia Prasetyo Qori Rafi Salsabila Taufik
Utami Vicky Winda Yusuf Zaki Alya Bayu Cahya Dwi Elsa Fitri Galih Hafiz Intan Jaya Kiki Lukman Mira
Nadia Oka Pandu Rani Sinta Tegar Ulfa Vero Wulan Yanti Zulfa Arya Azis Razan Iqbal Naufa Rendra Bima
Ayu Ratna Anisa Fikri Gilang Hesti Imam Julia Krisna Lia Mahendra Nabila Octavia Panji Reza Satria
Tika Ujang Vian Widya Yoga Zidan Alif Brian Clara Daffa Erlangga Farah Gibran Hilda Ilham Jasmine
Kholid Lintang Muna Nanda Okta Priska Qila Rahma Syifa Tito Ulil Viona Wildan Xavier Yasmin Zainal""".split()

NAMA_BELAKANG = """Santoso Wijaya Saputra Pratama Kusuma Hidayat Nugroho Setiawan Utomo Wibowo Lestari Rahayu
Purnomo Susanto Hartono Gunawan Siregar Nasution Hasibuan Lubis Harahap Simanjuntak Pangaribuan Sitompul
Tambunan Situmorang Manurung Sihombing Napitupulu Ginting Sembiring Tarigan Purba Damanik Saragih
Siahaan Panjaitan Marbun Hutapea Wahyudi Firmansyah Ramadhan Maulana Permana Kurniawan Hakim Fauzi
Anggraini Puspita Handayani Wulandari Suryani Safitri Fadilah Amalia Nurhaliza Rachman Effendi Hamdani
Yulianto Budiman Cahyono Prasetyo Suharto Suhendra Ardiansyah Febrianto Syahputra Alamsyah Aziz Ihsan
Nurdian Riyonggani Mulyadi Sumarno Supriyadi Haryanto Kartono Darmawan Irawan Laksana Mahardika
Pramudya Subagyo Kencana Salim Tanjung Lim Tan Halim Chandra Wongso Sutanto Hadi Rifa'i Mansyur
Ridwan Zulkarnain Ismail Latif Basri Kamil Yahya Thalib Baihaqi Dewantara Susilo Widodo Handoko""".split()

GELAR_DEPAN = ["", "", "", "Dr.", "Prof. Dr.", "Dr. Ir.", "Ir.", "Drs.", "Dra.", "H.", "Hj."]
GELAR_BELAKANG = ["S.Kom., M.Kom.", "S.T., M.T.", "S.Kom., M.Cs.", "S.Si., M.Sc.", "M.Kom.", "M.T.",
                  "Ph.D.", "M.Eng.", "S.Kom., M.T.I.", "S.T., M.Sc., Ph.D.", "M.Sc.", "S.Pd., M.Pd.",
                  "S.E., M.M.", "M.Cs.", "S.Kom., M.Sc.", "Ph.D. (Cand.)"]
DOMAIN = ["staff.uns.ac.id"]
KELAS = list("ABCDEFGHIJ")
HURUF_PRODI = "ABCDEFGHIJKLMNOPQRSTUV"   # prefix NIM (kode fakultas/prodi)


def q(s: str) -> str:
    """Escape string literal MySQL."""
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def slug(s: str) -> str:
    out = []
    for ch in s.lower():
        if ch.isalnum():
            out.append(ch)
        elif ch in " .-'" and out and out[-1] != ".":
            out.append(".")
    return "".join(out).strip(".")


def nama_acak() -> str:
    r = rnd.random()
    if r < 0.12:
        return rnd.choice(NAMA_DEPAN)
    if r < 0.62:
        return f"{rnd.choice(NAMA_DEPAN)} {rnd.choice(NAMA_BELAKANG)}"
    if r < 0.92:
        return f"{rnd.choice(NAMA_DEPAN)} {rnd.choice(NAMA_DEPAN)} {rnd.choice(NAMA_BELAKANG)}"
    return (f"{rnd.choice(NAMA_DEPAN)} {rnd.choice(NAMA_DEPAN)} "
            f"{rnd.choice(NAMA_BELAKANG)} {rnd.choice(NAMA_BELAKANG)}")


def tulis_batch(f, tabel, kolom, baris_iter, total):
    """Tulis INSERT multi-baris, BATCH baris per statement."""
    head = f"INSERT INTO {tabel} ({kolom}) VALUES\n"
    buf = []
    n = 0
    for baris in baris_iter:
        buf.append(baris)
        n += 1
        if len(buf) == BATCH:
            f.write(head + ",\n".join(buf) + ";\n")
            buf = []
            if (n // BATCH) % 20 == 0:
                f.write("COMMIT;\n")
    if buf:
        f.write(head + ",\n".join(buf) + ";\n")
    f.write("COMMIT;\n")
    assert n == total


# ------------------------------------------------------------------ mahasiswa
def baris_mahasiswa():
    # NIM: <huruf><2 digit prodi><2 digit angkatan><3 digit urut>  -> mis. L0325002
    ruang = len(HURUF_PRODI) * 99 * 12 * 1000
    idx = rnd.sample(range(ruang), N_MHS + 50)
    uids = rnd.sample(range(1 << 32), N_MHS + 50)
    i = 0
    u = 0
    done = 0
    while done < N_MHS:
        v = idx[i]; i += 1
        seq = v % 1000; v //= 1000
        angk = 14 + v % 12; v //= 12          # angkatan 14..25
        prodi = 1 + v % 99; v //= 99
        huruf = HURUF_PRODI[v]
        nim = f"{huruf}{prodi:02d}{angk:02d}{seq:03d}"
        if nim in NIM_ADA:
            continue
        if rnd.random() < 0.03:                # ~3% belum punya kartu RFID
            uid = "NULL"
        else:
            while True:
                h = f"{uids[u]:08X}"; u += 1
                if h not in UID_ADA:
                    break
            uid = q(h)
        yield f"({q(nim)}, {q(nama_acak())}, {q(rnd.choice(KELAS))}, {uid})"
        done += 1


# ---------------------------------------------------------------------- dosen
def baris_dosen():
    pakai_nip = set(NIP_ADA)
    pakai_email = set(EMAIL_ADA)
    for k in range(N_DSN):
        # NIP 18 digit: lahir(8) + angkat(6) + jenis kelamin(1) + urut(3)
        while True:
            th = rnd.randint(1955, 1999)
            nip = (f"{th}{rnd.randint(1, 12):02d}{rnd.randint(1, 28):02d}"
                   f"{rnd.randint(1990, 2025)}{rnd.randint(1, 12):02d}"
                   f"{rnd.randint(1, 2)}{rnd.randint(0, 999):03d}")
            if nip not in pakai_nip:
                pakai_nip.add(nip)
                break
        inti = nama_acak()
        gd = rnd.choice(GELAR_DEPAN)
        gb = rnd.choice(GELAR_BELAKANG)
        nama = (gd + " " if gd else "") + inti + ", " + gb
        if len(nama) > 100:
            nama = nama[:100]
        while True:
            email = f"{slug(inti)}{rnd.randint(1, 99999)}@{rnd.choice(DOMAIN)}"
            if email not in pakai_email:
                pakai_email.add(email)
                break
        # password acak -> disimpan sebagai SHA-256 hex (sama seperti SHA2(x,256))
        pw = "".join(rnd.choices("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890", k=7))
        h = hashlib.sha256(pw.encode()).hexdigest()
        yield f"({q(nip)}, {q(nama)}, {q(email)}, {q(h)})"


HEADER = "USE db_absensi;\nSET NAMES utf8mb4;\nSET autocommit = 0;\n\n"

if __name__ == "__main__":
    p1 = os.path.join(OUT, "seed_mahasiswa.sql")
    with open(p1, "w", encoding="utf-8") as f:
        f.write("-- Data acak mahasiswa (%d baris)\n" % N_MHS + HEADER)
        tulis_batch(f, "mahasiswa", "nim, nama, kelas, uid_rfid", baris_mahasiswa(), N_MHS)
    print("OK", p1, os.path.getsize(p1))

    p2 = os.path.join(OUT, "seed_dosen.sql")
    with open(p2, "w", encoding="utf-8") as f:
        f.write("-- Data acak dosen (%d baris)\n" % N_DSN + HEADER)
        tulis_batch(f, "dosen", "nip, nama, email, password", baris_dosen(), N_DSN)
    print("OK", p2, os.path.getsize(p2))
