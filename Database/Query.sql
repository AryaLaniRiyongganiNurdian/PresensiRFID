CREATE DATABASE IF NOT EXISTS db_absensi CHARACTER SET utf8mb4;
USE db_absensi;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS log_presensi, presensi_aktif_17, presensi_aktif_lab, ruangan, mahasiswa, dosen;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE dosen (
    id_dosen INT AUTO_INCREMENT PRIMARY KEY,
    nip VARCHAR(30) UNIQUE NOT NULL,
    nama VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(64) NOT NULL
);

INSERT INTO dosen (nip, nama, email, password) VALUES
('0', 'Admin Kelompok', 'adminkelompok', SHA2('kucing12321', 256));

CREATE TABLE ruangan (
    id_ruangan VARCHAR(10) PRIMARY KEY,
    nama_ruangan VARCHAR(50) NOT NULL,
    tabel_aktif VARCHAR(50) NOT NULL
);

INSERT INTO ruangan (id_ruangan, nama_ruangan, tabel_aktif) VALUES
('17', 'Ruang 17', 'presensi_aktif_17'),
('LAB', 'Lab Informatika', 'presensi_aktif_lab');

CREATE TABLE mahasiswa (
    nim VARCHAR(20) PRIMARY KEY,
    nama VARCHAR(100) NOT NULL,
    kelas VARCHAR(50) NOT NULL,
    uid_rfid VARCHAR(20) NULL UNIQUE
);

INSERT INTO mahasiswa (nim, nama, kelas, uid_rfid) VALUES
('L0325002', 'Arya Lani Riyonggani Nurdian', 'A', '917B6D66'),
('L0325043', 'Azis Fauzi Ramadhan', 'A', '30C75955'),
('L0325010', 'Razan Muhammad Ihsan', 'A', 'E1A1F163'),
('L0325004', 'Iqbal Rifa''i', 'A', 'E19F9263');

CREATE TABLE presensi_aktif_17 (
    id_aktif INT AUTO_INCREMENT PRIMARY KEY,
    nim VARCHAR(20) NOT NULL UNIQUE,
    waktu_masuk TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (nim) REFERENCES mahasiswa(nim) ON DELETE CASCADE
);

CREATE TABLE presensi_aktif_lab (
    id_aktif INT AUTO_INCREMENT PRIMARY KEY,
    nim VARCHAR(20) NOT NULL UNIQUE,
    waktu_masuk TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (nim) REFERENCES mahasiswa(nim) ON DELETE CASCADE
);

CREATE TABLE log_presensi (
    id_log INT AUTO_INCREMENT PRIMARY KEY,
    nim VARCHAR(20) NOT NULL,
    id_ruangan VARCHAR(10) NOT NULL,
    waktu_masuk TIMESTAMP NOT NULL,
    waktu_keluar TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    durasi_kunjungan TIME,
    FOREIGN KEY (nim) REFERENCES mahasiswa(nim) ON DELETE CASCADE,
    FOREIGN KEY (id_ruangan) REFERENCES ruangan(id_ruangan) ON DELETE CASCADE
);