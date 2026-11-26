package com.PresensiRFID.util;

/**
 * PBO Modul 9 — Sinkronisasi penghitung tap
 * AbsensiServlet berbagi satu objek TOTAL_TAP untuk semua permintaan reader.
 * PBO Modul 1 — new Penghitung() memakai constructor default implisit dari Java.
 * PBO Modul 2 — int menyimpan jumlah tap pada objek yang dirujuk bersama oleh servlet.
 * tambah() dipanggil setelah parameter wajib terisi, termasuk untuk kartu tak dikenal.
 * synchronized melindungi urutan baca, tambah, lalu tulis agar dua request
 * tidak kehilangan satu kenaikan. DataServlet dan AwalServlet membaca nilai lewat getNilai().
 */
public class Penghitung {
    private int nilai = 0;

    public synchronized void tambah() {
        nilai++;
    }

    public synchronized int getNilai() {
        return nilai;
    }
}
