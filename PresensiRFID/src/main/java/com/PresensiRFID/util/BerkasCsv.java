package com.PresensiRFID.util;

import com.PresensiRFID.model.DapatDiekspor;
import com.PresensiRFID.model.MahasiswaAbsensi;
import com.PresensiRFID.model.NimTidakValidException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pembacaan unggahan dan penulisan unduhan memakai format CSV yang sama. */
public final class BerkasCsv {
    private static final String HEADER = "NIM,Nama,Kelas,UID";

    private BerkasCsv() {}

    public record HasilImpor(int terbaca, List<MahasiswaAbsensi> mahasiswa, List<String> ditolak) {}

    private record RecordCsv(List<String> kolom, String galat) {
        boolean kosong() {
            return galat == null && kolom.size() == 1 && kolom.get(0).isBlank();
        }
    }

    /**
     * PBO Modul 5 dan 8 — BufferedWriter, try-with-resources, dan DapatDiekspor
     * Servlet memberi aliran unduhan dan peserta aktif dari MySQL. Setiap objek
     * menentukan isi kolom melalui keBarisCsv(). Header tetap ditulis saat daftar
     * kosong. try-with-resources menguras buffer dan menutup writer juga saat gagal.
     */
    public static void tulis(OutputStream tujuan, List<DapatDiekspor> daftar) throws IOException {
        try (BufferedWriter penulis = new BufferedWriter(new OutputStreamWriter(tujuan, StandardCharsets.UTF_8))) {
            penulis.write(HEADER);
            penulis.write("\r\n");
            for (DapatDiekspor peserta : daftar) {
                penulis.write(peserta.keBarisCsv());
                penulis.write("\r\n");
            }
        }
    }

    /**
     * PBO Modul 2–5 — Membaca dan memvalidasi unggahan
     * try-with-resources menutup pembaca dan aliran unggahan meski parsing gagal.
     * while mengambil satu record sebelum memprosesnya. File kosong dan file yang
     * hanya memiliki header menghasilkan nol data. ArrayList menampung objek
     * yang dapat disimpan serta alasan penolakan untuk ditampilkan di dashboard.
     */
    public static HasilImpor bacaMahasiswa(InputStream sumber) throws IOException {
        List<MahasiswaAbsensi> mahasiswa = new ArrayList<>();
        List<String> ditolak = new ArrayList<>();
        int terbaca = 0;
        try (PushbackReader pembaca = new PushbackReader(
                new BufferedReader(new InputStreamReader(sumber, StandardCharsets.UTF_8.newDecoder())))) {
            int awal = pembaca.read();
            if (awal != -1 && awal != '\uFEFF') {
                pembaca.unread(awal);
            }
            RecordCsv record;
            int nomor = 0;
            int jumlahKolom = 0;
            while ((record = bacaRecord(pembaca)) != null) {
                nomor++;
                if (record.kosong()) {
                    continue;
                }
                if (jumlahKolom == 0) {
                    jumlahKolom = periksaHeader(record);
                } else {
                    terbaca++;
                    bacaMahasiswa(record, nomor, jumlahKolom, mahasiswa, ditolak);
                }
            }
        }
        return new HasilImpor(terbaca, mahasiswa, ditolak);
    }

    private static int periksaHeader(RecordCsv record) {
        List<String> kolom = record.kolom().stream()
                .map(nama -> nama.trim().toUpperCase(Locale.ROOT)).toList();
        if (!kolom.equals(List.of("NIM", "NAMA", "KELAS", "UID"))
                && !kolom.equals(List.of("NIM", "NAMA", "KELAS"))) {
            throw new IllegalArgumentException("Header CSV harus NIM,Nama,Kelas,UID. Kolom UID boleh dihilangkan.");
        }
        return kolom.size();
    }

    private static void bacaMahasiswa(RecordCsv record, int nomor, int jumlahKolom, List<MahasiswaAbsensi> mahasiswa,
                                     List<String> ditolak) {
        List<String> kolom = record.kolom();
        String awalan = "Record " + nomor + ": ";
        if (record.galat() != null) {
            ditolak.add(awalan + record.galat());
            return;
        }
        if (kolom.size() < 3 || kolom.size() > jumlahKolom) {
            ditolak.add(awalan + "Isi NIM, nama, kelas, dan UID bila kolomnya tersedia di header.");
            return;
        }

        String nim = kolom.get(0).trim().toUpperCase(Locale.ROOT);
        String nama = kolom.get(1).trim();
        String kelas = kolom.get(2).trim().toUpperCase(Locale.ROOT);
        String uid = kolom.size() == 4 ? kolom.get(3).trim() : "";
        try {
            MahasiswaAbsensi.validasiNim(nim);
        } catch (NimTidakValidException e) {
            ditolak.add(awalan + e.getMessage());
            return;
        }
        if (nama.isEmpty() || kelas.isEmpty() || nama.length() > 100 || kelas.length() > 50) {
            ditolak.add(awalan + "Nama dan kelas wajib diisi (nama maks 100, kelas maks 50 karakter).");
            return;
        }

        MahasiswaAbsensi objek = uid.isEmpty() ? new MahasiswaAbsensi(nama, nim, kelas)
                : new MahasiswaAbsensi(nama, nim, kelas, uid);
        if (!uid.isEmpty() && !objek.sudahTerdaftar()) {
            ditolak.add(awalan + nim + ": format UID tidak valid. Hanya identitas yang diproses.");
        }
        mahasiswa.add(objek);
    }

    /**
     * PBO Modul 3 dan 5 — StringBuilder menyusun isi kolom dari aliran karakter.
     * Koma dan pindah baris memisahkan data di luar kutip. Di dalam kutip keduanya
     * menjadi bagian dari nilai. Dua kutip berurutan dibaca sebagai satu kutip.
     */
    private static RecordCsv bacaRecord(PushbackReader pembaca) throws IOException {
        List<String> kolom = new ArrayList<>();
        StringBuilder nilai = new StringBuilder();
        boolean dalamKutip = false;
        boolean kutipTutup = false;
        int kode;
        while ((kode = pembaca.read()) != -1) {
            char karakter = (char) kode;
            if (dalamKutip) {
                if (karakter != '"') {
                    nilai.append(karakter);
                    continue;
                }
                int lanjut = pembaca.read();
                if (lanjut == '"') {
                    nilai.append('"');
                } else {
                    dalamKutip = false;
                    kutipTutup = true;
                    if (lanjut != -1) {
                        pembaca.unread(lanjut);
                    }
                }
            } else if (karakter == '\r' || karakter == '\n') {
                lewatiAkhirBaris(pembaca, karakter);
                break;
            } else if (karakter == ',') {
                kolom.add(nilai.toString());
                nilai.setLength(0);
                kutipTutup = false;
            } else if (karakter == '"' && nilai.isEmpty() && !kutipTutup) {
                dalamKutip = true;
            } else if (karakter == '"' || kutipTutup) {
                // PBO Modul 2 — Baca minimal sekali untuk mencari akhir baris yang rusak.
                do {
                    kode = pembaca.read();
                } while (kode != -1 && kode != '\r' && kode != '\n');
                lewatiAkhirBaris(pembaca, kode);
                return new RecordCsv(List.of(), "Tanda kutip CSV berada di posisi yang salah.");
            } else {
                nilai.append(karakter);
            }
        }
        if (dalamKutip) {
            return new RecordCsv(List.of(), "Tanda kutip pembuka belum ditutup sebelum akhir berkas.");
        }
        if (kode == -1 && kolom.isEmpty() && nilai.isEmpty() && !kutipTutup) {
            return null;
        }
        kolom.add(nilai.toString());
        return new RecordCsv(kolom, null);
    }

    private static void lewatiAkhirBaris(PushbackReader pembaca, int karakter) throws IOException {
        if (karakter == '\r') {
            int lanjut = pembaca.read();
            if (lanjut != -1 && lanjut != '\n') {
                pembaca.unread(lanjut);
            }
        }
    }
}
