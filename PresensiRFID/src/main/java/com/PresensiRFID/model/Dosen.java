package com.PresensiRFID.model;

/**
 * LoginServlet membentuk Dosen dari satu akun yang cocok di MySQL.
 * Objek dalam sesi menyediakan identitas pelaku untuk formulir dan catatan harian.
 */
public class Dosen extends Pengguna {
    private final String email;

    // PBO Modul 6 — Pewarisan dan super
    // Constructor mengirim nama dan NIP ke Pengguna yang menyimpan identitas.
    // Dosen memakai getter warisan untuk membacanya dan menyimpan email sendiri.
    // ringkas() di bawah memakai kembali ringkasan induk dan menambahkan email.
    public Dosen(String nama, String nip, String email) {
        super(nama, nip);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String getPeran() {
        return "Dosen";
    }

    @Override
    public String ringkas() {
        return super.ringkas() + " <" + email + ">";
    }
}
