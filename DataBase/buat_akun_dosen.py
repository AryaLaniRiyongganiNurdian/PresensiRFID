"""Buat akun KitaHadir secara interaktif pada skema MySQL yang sudah tersedia."""

import getpass
import hashlib
import os
import subprocess
import sys
import warnings


def baca_isian(label, batas):
    nilai = input(f"{label}: ").strip()
    if not nilai or len(nilai) > batas:
        raise ValueError(f"{label} wajib diisi, maksimal {batas} karakter.")
    return nilai


def teks_sql(teks):
    # Literal heksadesimal menjaga input UTF-8 tanpa menyisipkan sintaks SQL.
    return f"CONVERT(0x{teks.encode('utf-8').hex()} USING utf8mb4)"


def perintah_mysql():
    mysql = [os.environ.get("KITAHADIR_MYSQL_EXE") or "mysql", "--no-defaults"]
    login_path = os.environ.get("KITAHADIR_MYSQL_LOGIN_PATH")
    host = os.environ.get("KITAHADIR_MYSQL_HOST")
    port = os.environ.get("KITAHADIR_MYSQL_PORT")
    user = os.environ.get("KITAHADIR_MYSQL_USER")
    if login_path:
        mysql.append(f"--login-path={login_path}")
    else:
        host, port, user = host or "127.0.0.1", port or "3306", user or "root"
    if host or port:
        mysql.append("--protocol=TCP")
    for opsi, nilai in (("host", host), ("port", port), ("user", user)):
        if nilai:
            mysql.append(f"--{opsi}={nilai}")
    return mysql + ["--default-character-set=utf8mb4", "--database=db_absensi"]


def main():
    try:
        nip = baca_isian("NIP", 30)
        nama = baca_isian("Nama", 100)
        email = baca_isian("Email login", 100)
        with warnings.catch_warnings():
            warnings.simplefilter("error", getpass.GetPassWarning)
            sandi = getpass.getpass("Kata sandi baru (minimal 12 karakter): ")
            if len(sandi) < 12 or sandi != getpass.getpass("Ulangi kata sandi: "):
                raise ValueError("Kata sandi terlalu pendek atau konfirmasi berbeda.")

        sidik = hashlib.sha256(sandi.encode("utf-8")).hexdigest()
        sql = ("INSERT INTO dosen (nip, nama, email, password) VALUES ("
               f"{teks_sql(nip)}, {teks_sql(nama)}, {teks_sql(email)}, '{sidik}');\n")
        subprocess.run(perintah_mysql(), input=sql.encode("ascii"), check=True)
    except (ValueError, EOFError, OSError, getpass.GetPassWarning) as e:
        print(f"Akun belum dibuat. {e}", file=sys.stderr)
        return 1
    except subprocess.CalledProcessError:
        print("MySQL gagal membuat akun. Periksa koneksi serta keunikan NIP dan email.", file=sys.stderr)
        return 1
    print("Akun dosen dibuat.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
