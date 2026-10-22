package com.absensi.model;

public class Dosen extends Pengguna {
    private final String email;

    public Dosen(String nama, String nip, String email) {
        super(nama, nip);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public void tampilkanInfo() {
        super.tampilkanInfo();
        System.out.println("Email: " + email);
    }
}