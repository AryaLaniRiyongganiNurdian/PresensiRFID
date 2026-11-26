package com.PresensiRFID.util;

import com.PresensiRFID.util.TransaksiPresensi.Kunjungan;
import com.PresensiRFID.util.TransaksiPresensi.Ruangan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** PBO Modul 9 — AwalServlet menjalankan Runnable ini untuk menutup kunjungan lama. */
public class PembersihPresensi implements Runnable {
    private static final long JEDA_MS = 10L * 60 * 1000;
    private static final String KEDALUWARSA = "waktu_masuk < CURRENT_TIMESTAMP - INTERVAL 12 HOUR";

    private record Kandidat(String nim, int idAktif) {}

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(JEDA_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            bersihkan();
        }
    }

    private void bersihkan() {
        int total = 0;
        try (Connection conn = Db.getUntukWorker()) {
            List<Ruangan> daftar = TransaksiPresensi.daftarRuangan(conn);
            TransaksiPresensi.mulai(conn);
            for (Ruangan ruangan : daftar) {
                for (Kandidat kandidat : pindai(conn, ruangan)) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    if (keluarkan(conn, ruangan, kandidat)) {
                        total++;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Pembersihan presensi gagal: " + e.getMessage());
        } finally {
            if (total > 0) {
                CatatanHarian.UTAMA.tulisCatatan(CatatanHarian.sekarang(),
                        "OTOMATIS KELUAR " + total + " mahasiswa dengan kunjungan lebih dari 12 jam");
            }
        }
    }

    private List<Kandidat> pindai(Connection conn, Ruangan ruangan) throws SQLException {
        List<Kandidat> hasil = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT nim, id_aktif FROM " + ruangan.tabel() + " WHERE " + KEDALUWARSA);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                hasil.add(new Kandidat(rs.getString(1), rs.getInt(2)));
            }
        }
        return hasil;
    }

    // Basis Data — Pemindaian hanya mengumpulkan kandidat. Setelah kunci NIM didapat,
    // cek lagi ID dan usia kunjungan agar kunjungan pengganti tidak ikut ditutup.
    private boolean keluarkan(Connection conn, Ruangan ruangan, Kandidat kandidat) throws SQLException {
        try {
            boolean kedaluwarsa = TransaksiPresensi.kunciMahasiswa(conn, kandidat.nim()) != null
                    && masihKedaluwarsa(conn, ruangan, kandidat);
            if (kedaluwarsa) {
                TransaksiPresensi.pindahKeRiwayat(conn, new Kunjungan(ruangan, kandidat.idAktif()));
            }
            conn.commit();
            return kedaluwarsa;
        } catch (SQLException e) {
            TransaksiPresensi.rollback(conn, e);
            throw e;
        }
    }

    private boolean masihKedaluwarsa(Connection conn, Ruangan ruangan, Kandidat kandidat) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM " + ruangan.tabel() + " WHERE id_aktif = ? AND nim = ? AND " + KEDALUWARSA)) {
            ps.setInt(1, kandidat.idAktif());
            ps.setString(2, kandidat.nim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
