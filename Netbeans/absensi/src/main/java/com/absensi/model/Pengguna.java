package com.absensi.model;

public class Pengguna implements java.io.Serializable {
    protected String nama;
    protected String nomorIdentitas;

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

    public void tampilkanInfo() {
        System.out.println("Nama: " + nama);
        System.out.println("ID/NIM: " + nomorIdentitas);
    }
}