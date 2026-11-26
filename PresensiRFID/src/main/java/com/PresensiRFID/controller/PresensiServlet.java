package com.PresensiRFID.controller;

import com.PresensiRFID.model.MahasiswaAbsensi;
import com.PresensiRFID.model.NimTidakValidException;
import com.PresensiRFID.model.Pengguna;
import com.PresensiRFID.util.CatatanHarian;
import com.PresensiRFID.util.DataMemori;
import com.PresensiRFID.util.Db;
import com.PresensiRFID.util.TransaksiPresensi;
import com.PresensiRFID.util.TransaksiPresensi.Kunjungan;
import com.PresensiRFID.util.TransaksiPresensi.Ruangan;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

/**
 * Formulir dosen di index.html mengirim POST /api/masuk atau POST /api/keluar.
 * Servlet memeriksa sesi, memilih prosesMasuk atau prosesKeluar, lalu mengirim
 * pesan JSON yang ditampilkan di web. Kedua jalur memakai satu transaksi per permintaan.
 */
@WebServlet(urlPatterns = {"/api/masuk", "/api/keluar"})
public class PresensiServlet extends HttpServlet {

    private static class InputException extends Exception {
        final int status;
        InputException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private record HasilPresensi(String pesan, String catatan) {}

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        Pengguna dosen = Auth.dosen(request);
        if (dosen == null) {
            JsonUtil.write(response, HttpServletResponse.SC_UNAUTHORIZED, msg("error", "Silakan login terlebih dahulu."));
            return;
        }

        boolean masuk = "/api/masuk".equals(request.getServletPath());

        HasilPresensi hasil = null;
        try (Connection conn = Db.get()) {
            // Basis Data — Transaksi melalui JDBC
            // Saat masuk, data mahasiswa, UID opsional, dan baris aktif ditulis dalam satu transaksi.
            // Saat keluar, yang disimpan bersama adalah riwayat dan penghapusan baris aktif.
            // Jika UID sudah dipakai, prosesMasuk melempar InputException setelah mencoba UPDATE.
            // rollback() juga membatalkan perubahan nama atau kelas yang mendahuluinya.
            TransaksiPresensi.mulai(conn);
            try {
                HasilPresensi calon = masuk ? prosesMasuk(conn, request, dosen) : prosesKeluar(conn, request, dosen);
                conn.commit();
                hasil = calon;
            } catch (InputException | SQLException e) {
                TransaksiPresensi.rollback(conn, e);
                throw e;
            }
        } catch (InputException e) {
            JsonUtil.write(response, e.status, msg("error", e.getMessage()));
            return;
        } catch (SQLException e) {
            log("Error presensi", e);
            if (hasil == null) {
                JsonUtil.write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        msg("error", "Hasil presensi belum dapat dikonfirmasi. Periksa dashboard."));
                return;
            }
        }
        if (masuk) {
            DataMemori.MAHASISWA.hapus();
        }
        CatatanHarian.UTAMA.tulisCatatan(CatatanHarian.sekarang(), hasil.catatan());
        JsonUtil.write(response, HttpServletResponse.SC_OK, msg("success", hasil.pesan()));
    }

    private HasilPresensi prosesMasuk(Connection conn, HttpServletRequest request, Pengguna dosen)
            throws SQLException, InputException {
        String nim = bersih(request.getParameter("nim"));
        String nama = bersih(request.getParameter("nama"));
        String kelas = bersih(request.getParameter("kelas"));
        String idRuangan = bersih(request.getParameter("id_ruangan"));
        String uid = bersih(request.getParameter("uid"));

        if (nim.isEmpty() || nama.isEmpty() || kelas.isEmpty() || idRuangan.isEmpty()) {
            throw new InputException(400, "NIM, nama, kelas, dan ruangan wajib diisi.");
        }
        if (nim.length() > 20 || nama.length() > 100 || kelas.length() > 50) {
            throw new InputException(400, "Input terlalu panjang (NIM maks 20, nama maks 100, kelas maks 50 karakter).");
        }

        // PBO Modul 3 — Custom exception dan try-catch
        // NIM formulir masih berupa String. validasiNim memeriksanya sebelum objek dibuat.
        // Misalnya, "ABC" gagal karena karakter setelah huruf pertama harus angka.
        // catch menerjemahkan NimTidakValidException menjadi InputException dengan status 400.
        // doPost menangkapnya dan mengirim pesan validasi ke formulir.
        try {
            MahasiswaAbsensi.validasiNim(nim);
        } catch (NimTidakValidException e) {
            throw new InputException(400, e.getMessage());
        }
        // PBO Modul 1 — Objek dari formulir masuk
        // Constructor tiga parameter membawa identitas dan kelas ke JDBC. Versi
        // empat parameter juga memvalidasi UID yang diisi oleh dosen.
        MahasiswaAbsensi mhs = uid.isEmpty() ? new MahasiswaAbsensi(nama, nim, kelas)
                : new MahasiswaAbsensi(nama, nim, kelas, uid);
        if (!uid.isEmpty() && !mhs.sudahTerdaftar()) {
            throw new InputException(400, "Format UID kartu tidak valid.");
        }

        List<Ruangan> ruangan = TransaksiPresensi.daftarRuangan(conn);
        Ruangan tujuan = ruangan.stream().filter(r -> r.id().equalsIgnoreCase(idRuangan)).findFirst().orElse(null);
        if (tujuan == null) {
            throw new InputException(400, "Ruangan tidak valid.");
        }

        // Basis Data — Upsert mengunci baris induk NIM, termasuk saat NIM baru dibuat.
        // Jika sudah aktif, rollback membatalkan upsert. UPDATE UID opsional menjaga kartu lama.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO mahasiswa (nim, nama, kelas) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE nama = VALUES(nama), kelas = VALUES(kelas)")) {
            ps.setString(1, mhs.getNomorIdentitas());
            ps.setString(2, mhs.getNama());
            ps.setString(3, mhs.getKelas());
            ps.executeUpdate();
        }

        Kunjungan aktif = TransaksiPresensi.posisi(conn, ruangan, nim);
        if (aktif != null) {
            throw new InputException(409, "DITOLAK: " + nim + " masih berada di "
                    + aktif.ruangan().nama() + ". Keluarkan dulu.");
        }

        if (mhs.sudahTerdaftar()) {   // method dari interface Kartu
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE mahasiswa SET uid_rfid = ? WHERE nim = ?")) {
                ps.setString(1, mhs.getUidRfid());
                ps.setString(2, mhs.getNomorIdentitas());
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException e) {
                throw new InputException(409, "Kartu ini sudah dipakai mahasiswa lain.");
            }
        }

        TransaksiPresensi.masuk(conn, tujuan, nim);
        // PBO Modul 8 — Polimorfisme pada catatan masuk
        // Array Pengguna berisi Dosen dari sesi dan MahasiswaAbsensi dari formulir.
        // Perulangan memanggil ringkas() dengan cara yang sama untuk keduanya.
        // Java memilih implementasi berdasarkan objek yang sedang dibaca.
        // doPost menulis ringkasan kedua pihak ke CatatanHarian setelah commit.
        Pengguna[] pihak = {dosen, mhs};
        StringBuilder sb = new StringBuilder("INPUT MANUAL MASUK (" + tujuan.nama() + ")");
        for (Pengguna p : pihak) {
            sb.append(" | ").append(p.ringkas());
        }
        return new HasilPresensi("MASUK (" + tujuan.nama() + "): " + nama + " tercatat masuk.", sb.toString());
    }

    private HasilPresensi prosesKeluar(Connection conn, HttpServletRequest request, Pengguna dosen)
            throws SQLException, InputException {
        String nim = bersih(request.getParameter("nim"));
        if (nim.isEmpty()) {
            throw new InputException(400, "NIM wajib diisi.");
        }

        Kunjungan aktif = null;
        if (TransaksiPresensi.kunciMahasiswa(conn, nim) != null) {
            aktif = TransaksiPresensi.posisi(conn, TransaksiPresensi.daftarRuangan(conn), nim);
        }
        if (aktif == null) {
            throw new InputException(404, "Mahasiswa ini tidak sedang berada di ruangan manapun.");
        }

        TransaksiPresensi.pindahKeRiwayat(conn, aktif);
        String namaRuangan = aktif.ruangan().nama();
        return new HasilPresensi("KELUAR (" + namaRuangan + "): " + nim + " tercatat keluar.",
                "INPUT MANUAL KELUAR (" + namaRuangan + ") | " + dosen.ringkas() + " | NIM " + nim);
    }

    private static String bersih(String s) {
        return s == null ? "" : s.trim();
    }

    private static JsonObject msg(String status, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("status", status);
        o.addProperty("message", message);
        return o;
    }
}
