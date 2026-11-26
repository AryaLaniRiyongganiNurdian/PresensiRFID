package com.PresensiRFID.model;

/**
 * PBO Modul 8 — Interface Kartu menetapkan cara membaca UID dan mengecek keberadaannya.
 * MahasiswaAbsensi mengimplementasikannya. PresensiServlet dan BerkasServlet memanggil
 * sudahTerdaftar() dan getUidRfid() untuk memutuskan apakah perlu menulis UID ke MySQL.
 * Nilai true berarti objek memiliki UID valid secara format. MySQL memeriksa kepemilikannya.
 */
public interface Kartu {
    String getUidRfid();

    boolean sudahTerdaftar();
}
