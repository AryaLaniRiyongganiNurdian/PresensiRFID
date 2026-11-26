-- Skema MySQL KitaHadir untuk db_absensi. Data induk dosen, ruangan, dan mahasiswa
-- dipakai servlet untuk login serta pencatatan kunjungan aktif dan riwayat.
CREATE DATABASE IF NOT EXISTS db_absensi CHARACTER SET utf8mb4;
USE db_absensi;

-- Basis Data Pertemuan 2 — DDL untuk membangun ulang tabel
-- DROP TABLE menghapus keenam tabel beserta isinya jika tabel sudah ada.
-- Pemeriksaan foreign key dinonaktifkan selama penghapusan dan diaktifkan kembali
-- sebelum tabel dibuat. INSERT di bawah mengisi data awal setelah pembuatan skema.
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS log_presensi, presensi_aktif_17, presensi_aktif_lab, ruangan, mahasiswa, dosen;
SET FOREIGN_KEY_CHECKS = 1;

-- Basis Data Pertemuan 2 — Primary key dan UNIQUE pada dosen
-- id_dosen bertambah otomatis dan membedakan akun. NIP serta email masing-masing
-- wajib diisi dan unik. Dua akun tidak bisa memakai email yang sama walau NIP berbeda.
-- LoginServlet mencocokkan email dan hash kata sandi, lalu memakai NIP pada objek Dosen.
CREATE TABLE dosen (
    id_dosen INT AUTO_INCREMENT PRIMARY KEY,
    nip VARCHAR(30) UNIQUE NOT NULL,
    nama VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(64) NOT NULL
) ENGINE=InnoDB;

-- Buat akun dosen dengan buat_akun_dosen.py setelah skema tersedia.

-- Basis Data Pertemuan 2 — Primary key ruangan
-- id_ruangan menghubungkan parameter reader dengan satu ruangan. nama_ruangan
-- dipakai saat menampilkan hasil. tabel_aktif berisi nama tabel untuk pencatatan masuk.
-- TransaksiPresensi membaca nilai ini dan memeriksa format nama tabel sebelum
-- menyusun query. log_presensi merujuk id_ruangan melalui foreign key.
CREATE TABLE ruangan (
    id_ruangan VARCHAR(10) PRIMARY KEY,
    nama_ruangan VARCHAR(50) NOT NULL,
    tabel_aktif VARCHAR(50) NOT NULL
) ENGINE=InnoDB;

INSERT INTO ruangan (id_ruangan, nama_ruangan, tabel_aktif) VALUES
('17', 'Ruang 17', 'presensi_aktif_17'),
('LAB', 'Lab Komputer', 'presensi_aktif_lab');

-- Basis Data Pertemuan 2 — Primary key mahasiswa dan UID opsional
-- NIM menjadi identitas yang dirujuk oleh tabel aktif dan log. Nama dan kelas wajib diisi.
-- UNIQUE membatasi satu UID terisi untuk satu mahasiswa. Beberapa mahasiswa
-- dapat memiliki uid_rfid NULL, misalnya saat dosen mencatat mereka secara manual.
-- Setelah kartu ditautkan, reader bisa menemukan NIM melalui uid_rfid.
-- Impor CSV dan input manual menulis nama dan kelas berdasarkan NIM, lalu menulis
-- UID dengan UPDATE terpisah jika ada isian. UID kosong mempertahankan kartu lama.
CREATE TABLE mahasiswa (
    nim VARCHAR(20) PRIMARY KEY,
    nama VARCHAR(100) NOT NULL,
    kelas VARCHAR(50) NOT NULL,
    uid_rfid VARCHAR(20) NULL UNIQUE
) ENGINE=InnoDB;

INSERT INTO mahasiswa (nim, nama, kelas, uid_rfid) VALUES
('L0325002', 'Arya Lani Riyonggani Nurdian', 'A', '917B6D66'),
('L0325043', 'Azis Fauzi Ramadhan', 'A', '30C75955'),
('L0325003', 'Inka Putri Yulia Rohmah', 'A', 'FA3D0B85'),
('L0325025', 'Firli Syawala', 'A', '3AE70C85'),
('L0325041', 'Nirina Ariftiyanti', 'A', 'EA171585'),
('L0325010', 'Razan Muhammad Ihsan', 'A', '0A620685'),
('L0325004', 'Iqbal Rifa''i', 'A', '0ACC6D84'),
('L0325026', 'Hanung Yoga Adi Pramono', 'A', 'FA830885'),
('L0325011', 'Re''fandi Indra Maulana', 'A', '1A1E1185'),
('L0325036', 'Nabil Muflih', 'A', 'EAAE7085'),
('L0325046', 'Fizillal Kamal Arsad Purwanto', 'B', '3AB30A85');

-- Basis Data Pertemuan 2 — Foreign key dan UNIQUE pada presensi_aktif_17
-- Satu baris berarti satu kunjungan yang masih aktif di Ruang 17. id_aktif adalah PK
-- otomatis. INSERT cukup membawa NIM karena waktu_masuk diisi oleh MySQL.
-- UNIQUE berlaku per tabel. TransaksiPresensi mengunci mahasiswa.nim
-- sebelum pemanggil memeriksa posisi di seluruh ruangan.
-- FK merujuk mahasiswa.nim. Menghapus mahasiswa menghapus baris aktifnya lewat CASCADE.
CREATE TABLE presensi_aktif_17 (
    id_aktif INT AUTO_INCREMENT PRIMARY KEY,
    nim VARCHAR(20) NOT NULL UNIQUE,
    waktu_masuk TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (nim) REFERENCES mahasiswa(nim) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Basis Data Pertemuan 2 — Foreign key dan UNIQUE pada presensi_aktif_lab
-- Lab memiliki daftar aktif sendiri dengan ID otomatis dan waktu masuk bawaan.
-- UNIQUE berlaku untuk NIM di Lab. FK mensyaratkan NIM itu ada di mahasiswa.
-- Jika baris induk di mahasiswa dihapus, ON DELETE CASCADE menghapus baris aktif
-- yang merujuknya. Saat tap keluar, yang dihapus hanya baris kunjungan aktif
-- setelah TransaksiPresensi menyalinnya ke riwayat.
CREATE TABLE presensi_aktif_lab (
    id_aktif INT AUTO_INCREMENT PRIMARY KEY,
    nim VARCHAR(20) NOT NULL UNIQUE,
    waktu_masuk TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (nim) REFERENCES mahasiswa(nim) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Basis Data Pertemuan 2 — Dua foreign key pada log_presensi
-- Setiap kunjungan selesai mendapat id_log baru. NIM yang sama boleh kembali
-- karena satu mahasiswa bisa berkunjung berkali-kali. Waktu masuk disalin dari
-- tabel aktif, waktu keluar terisi otomatis, dan TransaksiPresensi menghitung durasinya.
-- Jika mahasiswa dihapus, log yang merujuk mahasiswa.nim itu ikut terhapus.
-- Jika ruangan dihapus, log yang merujuk ruangan.id_ruangan itu juga ikut terhapus.
-- Basis Data — Transaksi keluar
-- TransaksiPresensi memakai INSERT ... SELECT dari tabel aktif dan DELETE atas baris yang
-- sama. Satu commit menyimpan keduanya. Rollback saat salah satu operasi gagal
-- menjaga riwayat dan daftar aktif tetap konsisten.
CREATE TABLE log_presensi (
    id_log INT AUTO_INCREMENT PRIMARY KEY,
    nim VARCHAR(20) NOT NULL,
    id_ruangan VARCHAR(10) NOT NULL,
    waktu_masuk TIMESTAMP NOT NULL,
    waktu_keluar TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    durasi_kunjungan TIME,
    FOREIGN KEY (nim) REFERENCES mahasiswa(nim) ON DELETE CASCADE,
    FOREIGN KEY (id_ruangan) REFERENCES ruangan(id_ruangan) ON DELETE CASCADE
) ENGINE=InnoDB;
