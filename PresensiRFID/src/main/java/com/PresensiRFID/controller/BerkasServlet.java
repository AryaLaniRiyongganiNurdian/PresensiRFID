package com.PresensiRFID.controller;

import com.PresensiRFID.model.DapatDiekspor;
import com.PresensiRFID.model.Dosen;
import com.PresensiRFID.model.MahasiswaAbsensi;
import com.PresensiRFID.util.BerkasCsv;
import com.PresensiRFID.util.CatatanHarian;
import com.PresensiRFID.util.DataMemori;
import com.PresensiRFID.util.Db;
import com.PresensiRFID.util.TransaksiPresensi;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Dosen dengan sesi aktif dapat mengunggah data mahasiswa di POST /api/impor,
 * mengunduh peserta aktif melalui GET /api/ekspor, dan membaca catatan di GET /api/catatan.
 */
@WebServlet(urlPatterns = {"/api/ekspor", "/api/impor", "/api/catatan"})
@MultipartConfig(fileSizeThreshold = 64 * 1024, maxFileSize = 2 * 1024 * 1024, maxRequestSize = 3 * 1024 * 1024)
public class BerkasServlet extends HttpServlet {
    private static final Pattern NAMA_AMAN = Pattern.compile("[A-Za-z0-9_]+");
    private static final int UID_DUPLIKAT = 1062; // MySQL ER_DUP_ENTRY pada UPDATE uid_rfid

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Dosen dosen = Auth.dosen(request);
        if (dosen == null) {
            JsonUtil.write(response, HttpServletResponse.SC_UNAUTHORIZED, JsonUtil.msg("error", "Silakan login terlebih dahulu."));
            return;
        }
        // PBO Modul 2 — switch memilih jenis keluaran dari URL yang dibuka dosen.
        switch (request.getServletPath()) {
            case "/api/catatan" -> catatan(response);
            case "/api/ekspor" -> ekspor(request, response, dosen);
            default -> response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Dosen dosen = Auth.dosen(request);
        if (dosen == null) {
            JsonUtil.write(response, HttpServletResponse.SC_UNAUTHORIZED, JsonUtil.msg("error", "Silakan login terlebih dahulu."));
            return;
        }
        if ("/api/impor".equals(request.getServletPath())) {
            impor(request, response, dosen);
        } else {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    // PBO Modul 5 — Baca file kejadian per baris untuk API. Tab Riwayat memakai log_presensi di MySQL.
    private void catatan(HttpServletResponse response) throws IOException {
        List<String> baris = CatatanHarian.UTAMA.bacaCatatan(50);
        JsonArray arr = new JsonArray();
        for (String b : baris) {
            arr.add(b);
        }
        JsonObject o = JsonUtil.msg("success", baris.isEmpty() ? "Belum ada catatan harian." : baris.size() + " catatan terakhir.");
        o.add("catatan", arr);
        JsonUtil.write(response, HttpServletResponse.SC_OK, o);
    }

    private void ekspor(HttpServletRequest request, HttpServletResponse response, Dosen dosen) throws IOException {
        String idRuangan = request.getParameter("ruangan");
        if (idRuangan == null || !NAMA_AMAN.matcher(idRuangan.trim()).matches()) {
            JsonUtil.write(response, HttpServletResponse.SC_BAD_REQUEST, JsonUtil.msg("error", "Parameter ruangan tidak valid."));
            return;
        }
        idRuangan = idRuangan.trim();
        List<DapatDiekspor> daftar = new ArrayList<>();
        try (Connection conn = Db.get()) {
            String tabel = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id_ruangan, tabel_aktif FROM ruangan WHERE id_ruangan = ?")) {
                ps.setString(1, idRuangan);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        idRuangan = rs.getString(1);
                        tabel = rs.getString(2);
                    }
                }
            }
            if (tabel == null || !NAMA_AMAN.matcher(tabel).matches() || !NAMA_AMAN.matcher(idRuangan).matches()) {
                JsonUtil.write(response, HttpServletResponse.SC_NOT_FOUND, JsonUtil.msg("error", "Ruangan tidak ditemukan."));
                return;
            }

            // PBO Modul 8 dan Basis Data — JOIN dan interface DapatDiekspor
            // Tabel ruangan menentukan daftar aktif. JOIN mengambil nama dan kelas
            // untuk setiap NIM itu. Penulis CSV menerima daftar melalui DapatDiekspor.
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT m.nim, m.nama, m.kelas, m.uid_rfid FROM " + tabel
                         + " a JOIN mahasiswa m ON m.nim = a.nim ORDER BY m.nim")) {
                while (rs.next()) {
                    String nim = rs.getString(1);
                    String nama = rs.getString(2);
                    String kelas = rs.getString(3);
                    String uid = rs.getString(4);
                    daftar.add(uid == null || uid.isBlank() ? new MahasiswaAbsensi(nama, nim, kelas)
                            : new MahasiswaAbsensi(nama, nim, kelas, uid));
                }
            }
        } catch (SQLException e) {
            log("Error ekspor", e);
            JsonUtil.write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonUtil.msg("error", "Daftar mahasiswa aktif gagal dibaca dari database."));
            return;
        }

        String namaFile = "data_hadir_" + idRuangan + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + namaFile + "\"");
        response.setHeader("Cache-Control", "no-store");
        BerkasCsv.tulis(response.getOutputStream(), daftar);
        CatatanHarian.UTAMA.tulisCatatan(CatatanHarian.sekarang(),
                dosen.ringkas() + " | ekspor " + daftar.size() + " mahasiswa ke " + namaFile);
    }

    private void impor(HttpServletRequest request, HttpServletResponse response, Dosen dosen) throws IOException {
        // PBO Modul 5 — Browser mengirim berkas sebagai bagian bernama csv.
        // Parser menutup aliran setelah menghasilkan daftar mahasiswa dan alasan
        // penolakan. Database dibuka setelah format berkas selesai diperiksa.
        BerkasCsv.HasilImpor hasil;
        try {
            String tipe = request.getContentType();
            if (tipe == null || !tipe.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
                throw new IllegalArgumentException("Unggah berkas CSV melalui formulir impor.");
            }
            Part csv = request.getPart("csv");
            if (csv == null || csv.getSubmittedFileName() == null || csv.getSubmittedFileName().isBlank()) {
                throw new IllegalArgumentException("Pilih berkas CSV terlebih dahulu.");
            }
            hasil = BerkasCsv.bacaMahasiswa(csv.getInputStream());
        } catch (IllegalStateException e) {
            JsonUtil.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    JsonUtil.msg("error", "Ukuran berkas CSV maksimal 2 MB."));
            return;
        } catch (IllegalArgumentException e) {
            JsonUtil.write(response, HttpServletResponse.SC_BAD_REQUEST, JsonUtil.msg("error", e.getMessage()));
            return;
        } catch (CharacterCodingException e) {
            JsonUtil.write(response, HttpServletResponse.SC_BAD_REQUEST, JsonUtil.msg("error", "Berkas CSV harus menggunakan UTF-8."));
            return;
        } catch (IOException | ServletException e) {
            log("Gagal membaca unggahan CSV", e);
            JsonUtil.write(response, HttpServletResponse.SC_BAD_REQUEST, JsonUtil.msg("error", "Unggahan CSV tidak bisa dibaca."));
            return;
        }

        int tersimpan = 0;
        if (!hasil.mahasiswa().isEmpty()) {
            try (Connection conn = Db.get()) {
                // Basis Data — Transaksi impor mahasiswa dan penautan UID
                // Identitas disimpan berdasarkan NIM. UID punya UPDATE tersendiri agar
                // isian kosong mempertahankan kartu lama. Konflik UNIQUE hanya menolak
                // UID itu. Galat SQL lain membatalkan perubahan satu berkas.
                conn.setAutoCommit(false);
                try {
                    int jumlah = simpanMahasiswa(conn, hasil);
                    conn.commit();
                    tersimpan = jumlah;
                    DataMemori.hapusIdentitasMahasiswa();
                } catch (SQLException e) {
                    TransaksiPresensi.rollback(conn, e);
                    throw e;
                }
            } catch (SQLException e) {
                log("Error impor", e);
                if (tersimpan == 0) {
                    JsonUtil.write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ringkasanImpor(
                            hasil, 0, "error", "Transaksi impor gagal. Penyimpanan belum berhasil dikonfirmasi."));
                    return;
                }
            }
        }

        CatatanHarian.UTAMA.tulisCatatan(CatatanHarian.sekarang(),
                dosen.ringkas() + " | impor CSV: " + tersimpan + " mahasiswa disimpan, "
                        + hasil.ditolak().size() + " catatan penolakan");
        JsonUtil.write(response, HttpServletResponse.SC_OK, ringkasanImpor(hasil, tersimpan, "success", null));
    }

    private int simpanMahasiswa(Connection conn, BerkasCsv.HasilImpor hasil) throws SQLException {
        Set<String> nimTersimpan = new HashSet<>();
        try (PreparedStatement simpan = conn.prepareStatement(
                "INSERT INTO mahasiswa (nim, nama, kelas) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE nama = VALUES(nama), kelas = VALUES(kelas)");
             PreparedStatement identitas = conn.prepareStatement("SELECT nim FROM mahasiswa WHERE nim = ? FOR UPDATE");
             PreparedStatement kartu = conn.prepareStatement("UPDATE mahasiswa SET uid_rfid = ? WHERE nim = ?")) {
            // PBO Modul 2 dan 4 — for-each langsung membaca objek di ArrayList hasil parser.
            for (MahasiswaAbsensi m : hasil.mahasiswa()) {
                String nim = m.getNomorIdentitas();
                simpan.setString(1, nim);
                simpan.setString(2, m.getNama());
                simpan.setString(3, m.getKelas());
                simpan.executeUpdate();

                // Pakai NIM tersimpan sebagai kunci hitung. Dua ejaan yang dianggap
                // sama oleh kolasi MySQL dapat merujuk satu baris mahasiswa.
                // FOR UPDATE membaca versi terkini yang baru diperiksa oleh upsert.
                identitas.setString(1, nim);
                try (ResultSet rs = identitas.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("NIM hasil impor tidak ditemukan.");
                    }
                    nimTersimpan.add(rs.getString(1));
                }

                if (m.sudahTerdaftar()) {
                    kartu.setString(1, m.getUidRfid());
                    kartu.setString(2, nim);
                    try {
                        kartu.executeUpdate();
                    } catch (SQLIntegrityConstraintViolationException e) {
                        if (e.getErrorCode() != UID_DUPLIKAT) {
                            throw e;
                        }
                        hasil.ditolak().add(nim + ": kartu " + m.uidTerformat()
                                + " sudah dipakai mahasiswa lain. Hanya identitas yang diproses.");
                    }
                }
            }
        }
        return nimTersimpan.size();
    }

    private static JsonObject ringkasanImpor(BerkasCsv.HasilImpor hasil, int tersimpan,
                                             String status, String pesan) {
        String jumlah = hasil.terbaca() + " record terbaca, " + tersimpan
                + " mahasiswa tersimpan, " + hasil.ditolak().size() + " catatan penolakan.";
        JsonObject o = JsonUtil.msg(status, pesan == null ? jumlah : jumlah + " " + pesan);
        o.addProperty("terbaca", hasil.terbaca());
        o.addProperty("tersimpan", tersimpan);
        JsonArray ditolak = new JsonArray();
        for (String alasan : hasil.ditolak()) {
            ditolak.add(alasan);
        }
        o.add("ditolak", ditolak);
        return o;
    }
}
