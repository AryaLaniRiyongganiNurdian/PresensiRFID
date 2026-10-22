package com.absensi.controller;

import com.absensi.util.Db;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.regex.Pattern;

@WebServlet(urlPatterns = {"/api/masuk", "/api/keluar"})
public class PresensiServlet extends HttpServlet {

    private static final Pattern NAMA_TABEL = Pattern.compile("[A-Za-z0-9_]+");

    private static class InputException extends Exception {
        final int status;
        InputException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static class Posisi {
        String id;
        String nama;
        String tabel;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        if (!Auth.isLoggedIn(request)) {
            JsonUtil.write(response, HttpServletResponse.SC_UNAUTHORIZED, msg("error", "Silakan login terlebih dahulu."));
            return;
        }

        boolean masuk = "/api/masuk".equals(request.getServletPath());

        try (Connection conn = Db.get()) {
            conn.setAutoCommit(false);
            try {
                String pesan = masuk ? prosesMasuk(conn, request) : prosesKeluar(conn, request);
                conn.commit();
                JsonUtil.write(response, HttpServletResponse.SC_OK, msg("success", pesan));
            } catch (InputException e) {
                conn.rollback();
                JsonUtil.write(response, e.status, msg("error", e.getMessage()));
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log("Error presensi", e);
            JsonUtil.write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    msg("error", "Kesalahan server: " + e.getMessage()));
        }
    }

    private String prosesMasuk(Connection conn, HttpServletRequest request) throws SQLException, InputException {
        String nim = bersih(request.getParameter("nim"));
        String nama = bersih(request.getParameter("nama"));
        String kelas = bersih(request.getParameter("kelas"));
        String idRuangan = bersih(request.getParameter("id_ruangan"));
        String uid = bersih(request.getParameter("uid")).toUpperCase();

        if (nim.isEmpty() || nama.isEmpty() || kelas.isEmpty() || idRuangan.isEmpty()) {
            throw new InputException(400, "NIM, nama, kelas, dan ruangan wajib diisi.");
        }
        if (nim.length() > 20 || nama.length() > 100 || kelas.length() > 50) {
            throw new InputException(400, "Input terlalu panjang (NIM maks 20, nama maks 100, kelas maks 50 karakter).");
        }
        if (!uid.isEmpty() && !uid.matches("[0-9A-F]{8,20}")) {
            throw new InputException(400, "Format UID kartu tidak valid.");
        }

        Posisi tujuan = null;
        Posisi sekarang = null;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id_ruangan, nama_ruangan, tabel_aktif FROM ruangan")) {
            while (rs.next()) {
                Posisi p = new Posisi();
                p.id = rs.getString(1);
                p.nama = rs.getString(2);
                p.tabel = cekTabel(rs.getString(3));
                if (p.id.equalsIgnoreCase(idRuangan)) {
                    tujuan = p;
                }
                if (sekarang == null && adaDi(conn, p.tabel, nim)) {
                    sekarang = p;
                }
            }
        }

        if (tujuan == null) {
            throw new InputException(400, "Ruangan tidak valid.");
        }
        if (sekarang != null) {
            throw new InputException(409, "DITOLAK: " + nim + " masih berada di " + sekarang.nama + ". Keluarkan dulu.");
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO mahasiswa (nim, nama, kelas) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE nama = VALUES(nama), kelas = VALUES(kelas)")) {
            ps.setString(1, nim);
            ps.setString(2, nama);
            ps.setString(3, kelas);
            ps.executeUpdate();
        }

        if (!uid.isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE mahasiswa SET uid_rfid = ? WHERE nim = ?")) {
                ps.setString(1, uid);
                ps.setString(2, nim);
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException e) {
                throw new InputException(409, "Kartu ini sudah dipakai mahasiswa lain.");
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + tujuan.tabel + " (nim) VALUES (?)")) {
            ps.setString(1, nim);
            ps.executeUpdate();
        }
        return "MASUK (" + tujuan.nama + "): " + nama + " tercatat masuk.";
    }

    private String prosesKeluar(Connection conn, HttpServletRequest request) throws SQLException, InputException {
        String nim = bersih(request.getParameter("nim"));
        if (nim.isEmpty()) {
            throw new InputException(400, "NIM wajib diisi.");
        }

        Posisi sekarang = null;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id_ruangan, nama_ruangan, tabel_aktif FROM ruangan")) {
            while (rs.next()) {
                Posisi p = new Posisi();
                p.id = rs.getString(1);
                p.nama = rs.getString(2);
                p.tabel = cekTabel(rs.getString(3));
                if (sekarang == null && adaDi(conn, p.tabel, nim)) {
                    sekarang = p;
                }
            }
        }
        if (sekarang == null) {
            throw new InputException(404, "Mahasiswa ini tidak sedang berada di ruangan manapun.");
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO log_presensi (nim, id_ruangan, waktu_masuk, durasi_kunjungan) " +
                "SELECT nim, ?, waktu_masuk, TIMEDIFF(CURRENT_TIMESTAMP, waktu_masuk) " +
                "FROM " + sekarang.tabel + " WHERE nim = ?")) {
            ps.setString(1, sekarang.id);
            ps.setString(2, nim);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM " + sekarang.tabel + " WHERE nim = ?")) {
            ps.setString(1, nim);
            ps.executeUpdate();
        }
        return "KELUAR (" + sekarang.nama + "): " + nim + " tercatat keluar.";
    }

    private static boolean adaDi(Connection conn, String tabel, String nim) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + tabel + " WHERE nim = ?")) {
            ps.setString(1, nim);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String cekTabel(String tabel) throws SQLException {
        if (tabel == null || !NAMA_TABEL.matcher(tabel).matches()) {
            throw new SQLException("Nama tabel tidak valid: " + tabel);
        }
        return tabel;
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