package com.PresensiRFID.controller;

import com.PresensiRFID.model.MahasiswaAbsensi;
import com.PresensiRFID.util.CatatanHarian;
import com.PresensiRFID.util.DataMemori;
import com.PresensiRFID.util.Db;
import com.PresensiRFID.util.KartuTerakhir;
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

/**
 * Halaman dosen memakai GET /api/kartu untuk mengambil UID terbaru dari KartuTerakhir.
 * Saat dosen menautkan kartu, POST /api/kartu membawa NIM dan UID ke doPost.
 * Servlet memperbarui mahasiswa.uid_rfid dan mengirim pesan JSON ke halaman.
 */
@WebServlet("/api/kartu")
public class KartuServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!Auth.isLoggedIn(request)) {
            JsonUtil.write(response, HttpServletResponse.SC_UNAUTHORIZED, msg("error", "Silakan login terlebih dahulu."));
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("status", "success");
        o.addProperty("uid", KartuTerakhir.ambil());
        JsonUtil.write(response, HttpServletResponse.SC_OK, o);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        if (!Auth.isLoggedIn(request)) {
            JsonUtil.write(response, HttpServletResponse.SC_UNAUTHORIZED, msg("error", "Silakan login terlebih dahulu."));
            return;
        }
        String nim = bersih(request.getParameter("nim"));
        String uid = bersih(request.getParameter("uid")).toUpperCase();

        if (nim.isEmpty() || !uid.matches("[0-9A-F]{8,20}")) {
            JsonUtil.write(response, HttpServletResponse.SC_BAD_REQUEST, msg("error", "NIM atau UID kartu tidak valid."));
            return;
        }

        // Basis Data Pertemuan 2 — UPDATE dan UNIQUE
        // NIM menentukan baris mahasiswa yang akan menerima UID. Jika tidak ada baris
        // yang diperbarui, servlet mengirim HTTP 404. UID yang sudah dimiliki NIM lain
        // melanggar UNIQUE dan menghasilkan HTTP 409. Setelah berhasil, servlet membuang
        // UID dari daftar sementara dan mengosongkan cache mahasiswa agar dibaca ulang.
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement("UPDATE mahasiswa SET uid_rfid = ? WHERE nim = ?")) {
            ps.setString(1, uid);
            ps.setString(2, nim);
            if (ps.executeUpdate() == 0) {
                JsonUtil.write(response, HttpServletResponse.SC_NOT_FOUND, msg("error", "NIM tidak ditemukan."));
                return;
            }
            KartuTerakhir.hapus(uid);
            DataMemori.MAHASISWA.hapus();   // uid_rfid berubah, muat ulang data mahasiswa di memori
            // PBO Modul 3 — maskingUid menyamarkan UID untuk pesan web dan catatan harian.
            String samar = MahasiswaAbsensi.maskingUid(uid);
            CatatanHarian.UTAMA.tulisCatatan(CatatanHarian.sekarang(),
                    Auth.dosen(request).ringkas() + " | tautkan kartu " + samar + " ke NIM " + nim);
            JsonUtil.write(response, HttpServletResponse.SC_OK, msg("success", "Kartu " + samar + " berhasil ditautkan ke " + nim + "."));
        } catch (SQLIntegrityConstraintViolationException e) {
            JsonUtil.write(response, HttpServletResponse.SC_CONFLICT, msg("error", "Kartu ini sudah dipakai mahasiswa lain."));
        } catch (SQLException e) {
            log("Error kartu", e);
            JsonUtil.write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    msg("error", "Kesalahan server: " + e.getMessage()));
        }
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