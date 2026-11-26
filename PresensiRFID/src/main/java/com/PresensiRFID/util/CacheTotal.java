package com.PresensiRFID.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Menyimpan hasil hitung per tabel untuk DataServlet dan DataMemori agar polling dashboard bisa memakai kembali total terakhir. */
public final class CacheTotal {

    private static final long MASA_MS = 30_000;

    private static final class Isi {
        volatile long nilai = -1;
        volatile long waktu = 0;
        final AtomicBoolean sibuk = new AtomicBoolean(false);
    }

    private static final ConcurrentHashMap<String, Isi> DATA = new ConcurrentHashMap<>();

    private CacheTotal() {}

    public static void hapus(String tabel) {
        DATA.remove(tabel);
    }

    public static long ambil(Connection conn, String tabel) throws SQLException {
        if (!tabel.matches("[A-Za-z0-9_]+")) {
            throw new SQLException("Nama tabel tidak valid: " + tabel);
        }
        Isi isi = DATA.computeIfAbsent(tabel, k -> new Isi());

        if (isi.nilai < 0) {
            synchronized (isi) {
                if (isi.nilai < 0) {
                    hitung(conn, tabel, isi);
                }
            }
        } else if (System.currentTimeMillis() - isi.waktu > MASA_MS && isi.sibuk.compareAndSet(false, true)) {
            try {
                hitung(conn, tabel, isi);
            } finally {
                isi.sibuk.set(false);
            }
        }
        return Math.max(isi.nilai, 0);
    }

    // Basis Data Pertemuan 5 — Agregasi COUNT(*)
    // MySQL mengembalikan satu angka untuk seluruh baris tabel. ambil() menghitung
    // saat belum ada nilai dan memperbaruinya saat diminta setelah 30 detik.
    // Contohnya, total log di dashboard bisa masih menunjukkan angka sebelumnya
    // meskipun satu tap keluar sudah menambah satu baris log_presensi.
    private static void hitung(Connection conn, String tabel, Isi isi) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabel)) {
            rs.next();
            isi.nilai = rs.getLong(1);
            isi.waktu = System.currentTimeMillis();
        }
    }
}
