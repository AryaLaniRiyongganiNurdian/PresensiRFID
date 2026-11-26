package com.PresensiRFID.controller;

import com.PresensiRFID.util.DataMemori;
import com.PresensiRFID.util.Db;
import com.PresensiRFID.util.Searching;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * muatTabel() meminta GET /api/tabel untuk tab riwayat, mahasiswa, dan dosen.
 * Parameter jenis memilih sumber DataMemori. Parameter sort, kolom, dan cari
 * memilih pembanding dari daftar di servlet. Sorting dan Searching bekerja di Java.
 * Formulir manual juga memakai endpoint ini untuk mencari NIM secara persis.
 * Hasilnya berupa JSON berisi baris pada halaman yang diminta dan jumlah halaman.
 */
@WebServlet("/api/tabel")
public class TabelServlet extends HttpServlet {

    private static final int UKURAN_HALAMAN = 20;

    private static final Comparator<String> TEKS = String.CASE_INSENSITIVE_ORDER;
    private static final Comparator<String[]> ID = (a, b) -> bandingAngka(a[0], b[0]);

    private enum Cara { AWALAN, PERSIS, ANGKA }

    /** Kolom yang bisa dicari. urutan = kunci sort yang membuat data terurut menurut kolom ini (atau null). */
    private static final class Kolom {
        final int idx;
        final Cara cara;
        final String urutan;

        Kolom(int idx, Cara cara, String urutan) {
            this.idx = idx;
            this.cara = cara;
            this.urutan = urutan;
        }

        /** Negatif jika nilai baris berada sebelum kata kunci, nol jika cocok, positif jika sesudahnya. */
        int banding(String[] baris, String q) {
            String nilai = isi(baris, idx);
            switch (cara) {
                case ANGKA:
                    return bandingAngka(nilai, q);
                case PERSIS:
                    return TEKS.compare(nilai, q);
                default:
                    String awal = nilai.length() > q.length() ? nilai.substring(0, q.length()) : nilai;
                    return TEKS.compare(awal, q);
            }
        }
    }

    private static final class Def {
        final DataMemori.Tabel tabel;
        final Function<String[], Map<String, Object>> pemeta;
        final Kolom cariBawaan;
        final Map<String, Comparator<String[]>> urutan = new HashMap<>();
        final Map<String, Kolom> cari = new HashMap<>();

        Def(DataMemori.Tabel tabel, Function<String[], Map<String, Object>> pemeta, Kolom cariBawaan) {
            this.tabel = tabel;
            this.pemeta = pemeta;
            this.cariBawaan = cariBawaan;
        }
    }

    private static final Map<String, Def> DEF = new HashMap<>();

    static {
        // mahasiswa: nim, nama, kelas, uid_rfid
        Def mhs = new Def(DataMemori.MAHASISWA, TabelServlet::barisMahasiswa, new Kolom(0, Cara.AWALAN, "nim"));
        mhs.urutan.put("nim", teks(0));
        mhs.urutan.put("nama", teks(1, 0));
        mhs.urutan.put("kelas", teks(2, 0));
        mhs.urutan.put("kartu", teks(3, 0));
        mhs.cari.put("nim", new Kolom(0, Cara.AWALAN, "nim"));
        mhs.cari.put("nama", new Kolom(1, Cara.AWALAN, "nama"));
        mhs.cari.put("kelas", new Kolom(2, Cara.AWALAN, "kelas"));
        mhs.cari.put("kartu", new Kolom(3, Cara.AWALAN, "kartu"));
        DEF.put("mahasiswa", mhs);

        // dosen: nip, nama, email
        Def dsn = new Def(DataMemori.DOSEN, TabelServlet::barisDosen, new Kolom(0, Cara.AWALAN, "nip"));
        dsn.urutan.put("nip", teks(0));
        dsn.urutan.put("nama", teks(1, 0));
        dsn.urutan.put("email", teks(2, 0));
        dsn.cari.put("nip", new Kolom(0, Cara.AWALAN, "nip"));
        dsn.cari.put("nama", new Kolom(1, Cara.AWALAN, "nama"));
        dsn.cari.put("email", new Kolom(2, Cara.AWALAN, "email"));
        DEF.put("dosen", dsn);

        // log: id_log, nim, nama, ruangan, masuk, keluar, durasi
        Def log = new Def(DataMemori.LOG, TabelServlet::barisLog, new Kolom(1, Cara.AWALAN, null));
        log.urutan.put("id", ID);
        log.urutan.put("mahasiswa", teks(2, 1).thenComparing(ID));
        log.urutan.put("ruangan", teks(3).thenComparing(ID));
        log.urutan.put("masuk", teks(4).thenComparing(ID));
        log.urutan.put("keluar", teks(5).thenComparing(ID));
        log.cari.put("nim", new Kolom(1, Cara.AWALAN, null));
        log.cari.put("nama", new Kolom(2, Cara.AWALAN, "mahasiswa"));
        log.cari.put("ruangan", new Kolom(3, Cara.AWALAN, "ruangan"));
        log.cari.put("id", new Kolom(0, Cara.ANGKA, "id"));
        DEF.put("log", log);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!Auth.isLoggedIn(request)) {
            kirimError(response, HttpServletResponse.SC_UNAUTHORIZED, "Silakan login terlebih dahulu.");
            return;
        }

        String jenis = bersih(request.getParameter("jenis"));
        String nim = bersih(request.getParameter("nim"));      // pencarian persis, dipakai form Input Manual
        String sort = bersih(request.getParameter("sort")).toLowerCase();
        String dirParam = bersih(request.getParameter("dir")).toLowerCase();
        String kolom = bersih(request.getParameter("kolom")).toLowerCase();
        String cari = bersih(request.getParameter("cari"));
        if (cari.length() > 100) {
            cari = cari.substring(0, 100);
        }
        int halaman = angka(request.getParameter("page"));

        Def def = DEF.get(jenis);
        if (def == null) {
            kirimError(response, HttpServletResponse.SC_BAD_REQUEST, "Jenis tabel tidak dikenal.");
            return;
        }

        // Tanpa parameter dir: log terbaru dulu (perilaku lama), tabel lain urut naik
        boolean turun = dirParam.isEmpty() ? jenis.equals("log") : dirParam.equals("desc");

        try (Connection conn = Db.get()) {
            // 1. Sorting: hanya jika pengguna memilih kolom urutan
            Comparator<String[]> cmp = def.urutan.get(sort);
            String[][] data = cmp == null
                    ? def.tabel.mentah(conn)
                    : def.tabel.terurut(conn, sort, turun, cmp);
            String kunciUrut = cmp == null ? null : sort;

            // 2. Searching
            String[][] hasil = data;
            if (jenis.equals("mahasiswa") && !nim.isEmpty()) {
                hasil = cariData(data, new Kolom(0, Cara.PERSIS, "nim"), nim, kunciUrut, turun);
                halaman = 1;
            } else if (!cari.isEmpty()) {
                hasil = cariData(data, def.cari.getOrDefault(kolom, def.cariBawaan), cari, kunciUrut, turun);
            }

            // PBO Modul 4 — Iterasi dan koleksi untuk satu halaman
            // hasil berisi seluruh baris yang lolos pencarian. Servlet membatasi
            // nomor halaman, lalu memetakan baris pada rentang itu ke daftar Map.
            // Misalnya, dari 23 hasil, halaman kedua hanya memuat tiga baris terakhir.
            // Browser menerima ketiga baris beserta total agar tombol navigasi sesuai.
            long total = hasil.length;
            int totalHalaman = (int) Math.max(1, (total + UKURAN_HALAMAN - 1) / UKURAN_HALAMAN);
            int hal = Math.min(Math.max(halaman, 1), totalHalaman);
            int dari = (hal - 1) * UKURAN_HALAMAN;
            int sampai = Math.min(dari + UKURAN_HALAMAN, hasil.length);

            List<Map<String, Object>> baris = new ArrayList<>();
            for (int i = dari; i < sampai; i++) {
                baris.add(def.pemeta.apply(hasil[i]));
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "success");
            out.put("total", total);
            out.put("page", hal);
            out.put("totalPages", totalHalaman);
            out.put("rows", baris);
            JsonUtil.write(response, HttpServletResponse.SC_OK, out);
        } catch (SQLException e) {
            log("Error tabel", e);
            kirimError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Binary Search iteratif bila data sedang terurut menurut kolom pencarian, selain itu Sequential Search.
     * Arah urutan turun membalik hasil perbandingan.
     */
    private static String[][] cariData(String[][] data, Kolom k, String q, String kunciUrut, boolean turun) {
        if (k.cara == Cara.ANGKA) {
            if (!q.matches("\\d{1,18}")) {
                return new String[0][];
            }
            q = String.valueOf(Long.parseLong(q));
        }
        final String kunci = q;
        if (k.urutan != null && k.urutan.equals(kunciUrut)) {
            final int arah = turun ? -1 : 1;
            return Searching.binary(data, b -> arah * k.banding(b, kunci));
        }
        return Searching.sequential(data, b -> k.banding(b, kunci) == 0);
    }

    // ------------------------------------------------------------------ pembanding
    private static String isi(String[] baris, int i) {
        return baris[i] == null ? "" : baris[i];
    }

    /** Urut menurut kolom-kolom berurutan (kolom pertama utama, berikutnya pemutus nilai sama). */
    private static Comparator<String[]> teks(int... kolom) {
        return (a, b) -> {
            for (int i : kolom) {
                int c = TEKS.compare(isi(a, i), isi(b, i));
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        };
    }

    /** Membandingkan bilangan bulat positif tanpa awalan nol: lebih pendek berarti lebih kecil. */
    private static int bandingAngka(String x, String y) {
        return x.length() != y.length() ? x.length() - y.length() : x.compareTo(y);
    }

    // ------------------------------------------------------------------ pemeta baris
    private static Map<String, Object> barisLog(String[] b) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", Long.parseLong(b[0]));
        row.put("nim", b[1]);
        row.put("nama", b[2]);
        row.put("ruangan", b[3]);
        row.put("masuk", b[4]);
        row.put("keluar", b[5]);
        row.put("durasi", b[6]);
        return row;
    }

    private static Map<String, Object> barisMahasiswa(String[] b) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("nim", b[0]);
        row.put("nama", b[1]);
        row.put("kelas", b[2]);
        row.put("uid", b[3]);
        return row;
    }

    private static Map<String, Object> barisDosen(String[] b) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("nip", b[0]);
        row.put("nama", b[1]);
        row.put("email", b[2]);
        return row;
    }

    private static void kirimError(HttpServletResponse response, int status, String pesan) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("status", "error");
        err.put("message", pesan);
        JsonUtil.write(response, status, err);
    }

    private static String bersih(String s) {
        return s == null ? "" : s.trim();
    }

    private static int angka(String s) {
        try {
            return Integer.parseInt(s == null ? "1" : s.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}