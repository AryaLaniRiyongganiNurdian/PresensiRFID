package com.PresensiRFID.util;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * AbsensiServlet menitipkan UID yang belum terdaftar di sini. KartuServlet mengambil
 * UID terbaru untuk formulir dosen. Daftar berada di memori selama aplikasi berjalan.
 */
public final class KartuTerakhir {
    private static final long MASA_MS = 60_000;
    private static final int MAKSIMAL = 10;

    private static class KartuBaru {
        final String uid;
        final long waktu;

        KartuBaru(String uid, long waktu) {
            this.uid = uid;
            this.waktu = waktu;
        }
    }

    private static final LinkedList<KartuBaru> DAFTAR = new LinkedList<>();

    private KartuTerakhir() {}

    // PBO Modul 4 — CRUD objek dalam LinkedList
    // AbsensiServlet menambah entri pada tap pertama. Tap ulang mengganti entri
    // UID yang sama agar waktu dan urutannya diperbarui.
    // Saat jumlahnya melewati sepuluh, entri paling awal dilepas.
    // PBO Modul 9 — Method static synchronized memakai kunci kelas yang sama
    // agar simpan, ambil, dan hapus mendapat giliran saat beberapa request datang.
    public static synchronized void simpan(String uid) {
        hapus(uid);
        DAFTAR.addLast(new KartuBaru(uid, System.currentTimeMillis()));
        if (DAFTAR.size() > MAKSIMAL) {
            DAFTAR.removeFirst();
        }
    }

    // PBO Modul 4 dan 9 — Iterator dan synchronized
    // KartuServlet membaca UID terbaru untuk formulir dosen. Iterator membuang UID yang berumur
    // lebih dari 60 detik. Kunci kelas mencegah request reader atau penautan
    // mengubah daftar saat pembersihan dan pengambilan UID sedang berlangsung.
    public static synchronized String ambil() {
        long sekarang = System.currentTimeMillis();
        Iterator<KartuBaru> it = DAFTAR.iterator();
        while (it.hasNext()) {
            if (sekarang - it.next().waktu > MASA_MS) {
                it.remove();
            }
        }
        return DAFTAR.isEmpty() ? null : DAFTAR.getLast().uid;
    }

    // KartuServlet menghapus kartu dari daftar setelah penautan berhasil.
    public static synchronized void hapus(String uid) {
        Iterator<KartuBaru> it = DAFTAR.iterator();
        while (it.hasNext()) {
            if (it.next().uid.equals(uid)) {
                it.remove();
            }
        }
    }
}
