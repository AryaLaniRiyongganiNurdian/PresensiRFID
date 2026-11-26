package com.PresensiRFID.model;

/**
 * PBO Modul 3 — Custom exception untuk NIM yang gagal diperiksa oleh MahasiswaAbsensi.validasiNim().
 * Pesannya diteruskan ke formulir oleh PresensiServlet atau dicatat per record oleh BerkasCsv.
 */
public class NimTidakValidException extends Exception {
    public NimTidakValidException(String pesan) {
        super(pesan);
    }
}
