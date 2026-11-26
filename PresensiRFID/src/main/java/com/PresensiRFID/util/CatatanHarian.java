package com.PresensiRFID.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;

/**
 * PBO Modul 5 — Servlet presensi, kartu, dan berkas menambahkan kejadian ke catatan_presensi.txt.
 * GET /api/catatan membacanya per baris. File dapat dibaca lagi setelah Tomcat
 * dimulai ulang dengan folder kerja yang sama. Riwayat kunjungan disimpan terpisah di MySQL.
 */
public class CatatanHarian {
    public static final CatatanHarian UTAMA = new CatatanHarian("presensi");

    private final String namaFile;

    public CatatanHarian(String namaPemilik) {
        this.namaFile = "catatan_" + namaPemilik + ".txt";
    }

    // PBO Modul 5 — Ratakan CR/LF saat menulis agar satu kejadian tetap satu baris saat dibaca.
    // Nama mahasiswa pada model, MySQL, dan CSV tetap menyimpan pindah barisnya.
    // PBO Modul 9 — synchronized mengatur giliran penulis dan pembaca pada objek UTAMA.
    public synchronized void tulisCatatan(String tanggal, String isi) {
        String baris = "[" + tanggal + "] - " + isi;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(namaFile, true))) {
            bw.write(baris.replaceAll("\\r\\n|[\\r\\n]", " "));
            bw.newLine();
        } catch (IOException e) {
            System.out.println("Gagal menulis catatan: " + e.getMessage());
        }
    }

    /** BerkasServlet mengambil paling banyak jumlahTerakhir baris untuk GET /api/catatan. */
    public synchronized List<String> bacaCatatan(int jumlahTerakhir) {
        LinkedList<String> hasil = new LinkedList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(namaFile))) {
            String baris;
            while ((baris = br.readLine()) != null) {
                hasil.add(baris);
                if (hasil.size() > jumlahTerakhir) {
                    hasil.removeFirst();
                }
            }
        } catch (IOException e) {
            // file belum ada atau tidak bisa dibaca: dianggap belum ada catatan
        }
        return hasil;
    }

    /** Contoh hasil: 2026-11-23 14:05:09 */
    public static String sekarang() {
        return LocalDateTime.now().withNano(0).toString().replace('T', ' ');
    }
}
