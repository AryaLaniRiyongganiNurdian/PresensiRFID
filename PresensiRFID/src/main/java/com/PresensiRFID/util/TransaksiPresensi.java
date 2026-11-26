package com.PresensiRFID.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Operasi bersama untuk tap, formulir dosen, dan pembersih otomatis. */
public final class TransaksiPresensi {
    private TransaksiPresensi() {}

    public record Ruangan(String id, String nama, String tabel) {}
    public record Mahasiswa(String nim, String nama, String uid) {}
    public record Kunjungan(Ruangan ruangan, int idAktif) {}

    public static void mulai(Connection conn) throws SQLException {
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        conn.setAutoCommit(false);
    }

    // Basis Data — Transaksi presensi mengunci baris induk NIM untuk koordinasi lintas koneksi.
    // Masuk manual memperoleh kunci ini melalui upsert, termasuk jika NIM baru.
    public static Mahasiswa kunciMahasiswa(Connection conn, String nim) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT nim, nama, uid_rfid FROM mahasiswa WHERE nim = ? FOR UPDATE")) {
            ps.setString(1, nim);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Mahasiswa(rs.getString(1), rs.getString(2), rs.getString(3)) : null;
            }
        }
    }

    public static List<Ruangan> daftarRuangan(Connection conn) throws SQLException {
        List<Ruangan> ruangan = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id_ruangan, nama_ruangan, tabel_aktif FROM ruangan")) {
            while (rs.next()) {
                String tabel = rs.getString(3);
                if (tabel == null || !tabel.matches("[A-Za-z0-9_]+")) {
                    throw new SQLException("Nama tabel aktif tidak valid.");
                }
                ruangan.add(new Ruangan(rs.getString(1), rs.getString(2), tabel));
            }
        }
        return ruangan;
    }

    // READ COMMITTED membaca posisi terbaru setelah transaksi sebelumnya melepas kunci NIM.
    public static Kunjungan posisi(Connection conn, List<Ruangan> ruangan, String nim) throws SQLException {
        for (Ruangan ruang : ruangan) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id_aktif FROM " + ruang.tabel() + " WHERE nim = ?")) {
                ps.setString(1, nim);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Kunjungan(ruang, rs.getInt(1));
                    }
                }
            }
        }
        return null;
    }

    public static void masuk(Connection conn, Ruangan tujuan, String nim) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO " + tujuan.tabel() + " (nim) VALUES (?)")) {
            ps.setString(1, nim);
            ps.executeUpdate();
        }
    }

    // Pemanggil memegang kunci NIM sampai commit atau rollback kedua operasi.
    public static void pindahKeRiwayat(Connection conn, Kunjungan kunjungan) throws SQLException {
        Ruangan ruang = kunjungan.ruangan();
        try (PreparedStatement salin = conn.prepareStatement(
                "INSERT INTO log_presensi (nim, id_ruangan, waktu_masuk, durasi_kunjungan) "
                        + "SELECT nim, ?, waktu_masuk, TIMEDIFF(CURRENT_TIMESTAMP, waktu_masuk) "
                        + "FROM " + ruang.tabel() + " WHERE id_aktif = ?");
             PreparedStatement hapus = conn.prepareStatement(
                     "DELETE FROM " + ruang.tabel() + " WHERE id_aktif = ?")) {
            salin.setString(1, ruang.id());
            salin.setInt(2, kunjungan.idAktif());
            hapus.setInt(1, kunjungan.idAktif());
            if (salin.executeUpdate() != 1 || hapus.executeUpdate() != 1) {
                throw new SQLException("Kunjungan aktif tidak dapat dipindahkan ke riwayat.");
            }
        }
    }

    public static void rollback(Connection conn, Exception penyebab) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            penyebab.addSuppressed(e);
        }
    }
}
