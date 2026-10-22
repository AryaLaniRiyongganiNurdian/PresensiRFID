package com.absensi.model;

public class MahasiswaAbsensi extends Pengguna {
    private String kelas;

    public MahasiswaAbsensi(String nama, String nim, String kelas) {
        super(nama, nim);
        this.kelas = kelas;
    }

    public String getKelas() {
        return kelas;
    }

    public void setKelas(String kelas) {
        if (kelas != null && !kelas.trim().isEmpty()) {
            this.kelas = kelas;
        }
    }

    @Override
    public void tampilkanInfo() {
        super.tampilkanInfo();
        System.out.println("Kelas: " + kelas);
    }
}