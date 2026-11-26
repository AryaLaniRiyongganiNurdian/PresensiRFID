package com.PresensiRFID.model;

/**
 * PBO Modul 8 — Class abstrak untuk identitas dosen dan mahasiswa
 * Kedua turunan memakai nama dan nomorIdentitas yang sama melalui constructor induk.
 * Getter menyediakan akses baca. Setiap turunan menentukan getPeran() sendiri.
 * Saat ringkas() memanggil getPeran(), objek Dosen menghasilkan peran dosen
 * dan MahasiswaAbsensi menghasilkan peran mahasiswa.
 */
public abstract class Pengguna implements java.io.Serializable {
    private final String nama;
    private final String nomorIdentitas;

    public Pengguna(String nama, String nomorIdentitas) {
        this.nama = nama;
        this.nomorIdentitas = nomorIdentitas;
    }

    public String getNama() {
        return nama;
    }

    public String getNomorIdentitas() {
        return nomorIdentitas;
    }

    /** PBO Modul 8 — Method abstrak. Dosen dan MahasiswaAbsensi menetapkan perannya masing-masing. */
    public abstract String getPeran();

    /** Ringkasan dasar yang dipakai turunan lewat super.ringkas() untuk catatan presensi manual. */
    public String ringkas() {
        return getPeran() + " " + nama + " (" + nomorIdentitas + ")";
    }
}
