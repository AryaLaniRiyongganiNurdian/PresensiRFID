package com.PresensiRFID.util;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * PBO Modul 9 — Pemuat tabel yang mewarisi Thread
 * AwalServlet.init membuat satu pemuat per tabel dan memanggil start().
 * run() membuka koneksi khusus lalu meminta Tabel.mentah mengisi cache.
 * AwalServlet.destroy memberi sinyal berhenti, membatalkan koneksi yang sedang
 * dibaca lewat hentikan(), lalu menggunakan join() untuk menunggu worker.
 */
public class PemuatData extends Thread {
    private final String namaTabel;
    private final DataMemori.Tabel tabel;
    private volatile Connection koneksi;

    public PemuatData(String namaTabel, DataMemori.Tabel tabel) {
        super("pemuat-" + namaTabel);
        this.namaTabel = namaTabel;
        this.tabel = tabel;
        setDaemon(true);   // tidak menahan server saat dimatikan
    }

    @Override
    public void run() {
        long mulai = System.currentTimeMillis();
        try (Connection conn = Db.getUntukWorker()) {
            koneksi = conn;
            if (isInterrupted()) {
                return;
            }
            tabel.mentah(conn);
        } catch (SQLException e) {
            if (!isInterrupted()) {
                System.out.println("Gagal memuat tabel " + namaTabel + ": " + e.getMessage());
            }
            return;
        } finally {
            // PBO Modul 3 — try-with-resources menutup koneksi. finally melepas referensi
            // yang dipakai hentikan(), termasuk saat baca gagal atau worker berhenti lebih awal.
            koneksi = null;
        }
        if (!isInterrupted()) {
            System.out.println("Tabel " + namaTabel + " dimuat ke memori dalam "
                    + (System.currentTimeMillis() - mulai) + " ms.");
        }
    }

    public void hentikan() throws SQLException {
        Connection conn = koneksi;
        if (conn != null) {
            // Putuskan koneksi streaming agar baca socket yang sedang menunggu bisa berhenti.
            conn.abort(Runnable::run);
        }
    }

}
