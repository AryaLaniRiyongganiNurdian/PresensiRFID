package com.absensi.controller;

import com.absensi.util.Db;
import com.absensi.util.KartuTerakhir;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

@WebServlet("/proses_rfid")
public class AbsensiServlet extends HttpServlet {

    private static final Pattern NAMA_TABEL = Pattern.compile("[A-Za-z0-9_]+");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String uid = request.getParameter("uid");
        String idRuanganScan = request.getParameter("id_ruangan");

        if (uid == null || uid.isBlank() || idRuanganScan == null || idRuanganScan.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("Parameter tidak lengkap.");
            return;
        }
        uid = uid.trim().toUpperCase();
        idRuanganScan = idRuanganScan.trim();

        try (Connection conn = Db.get()) {
            conn.setAutoCommit(false);
            try {
                out.print(proses(conn, uid, idRuanganScan));
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log("Error proses_rfid", e);
            out.print("Error Server: " + e.getMessage());
        }
    }

    private String proses(Connection conn, String uid, String idRuanganScan) throws SQLException {
        String nim;
        String nama;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT nim, nama FROM mahasiswa WHERE uid_rfid = ?")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    KartuTerakhir.simpan(uid);
                    return "GAGAL: Kartu belum terdaftar, hubungi dosen!";
                }
                nim = rs.getString("nim");
                nama = rs.getString("nama");
            }
        }

        String idSekarang = null;
        String tabelSekarang = null;
        String namaRuangSekarang = null;
        String tabelTujuan = null;
        String namaRuangTujuan = null;

        try (Statement st = conn.createStatement();
             ResultSet rsR = st.executeQuery("SELECT id_ruangan, nama_ruangan, tabel_aktif FROM ruangan")) {
            while (rsR.next()) {
                String idR = rsR.getString("id_ruangan");
                String namaR = rsR.getString("nama_ruangan");
                String tabel = rsR.getString("tabel_aktif");
                if (!NAMA_TABEL.matcher(tabel).matches()) {
                    throw new SQLException("Nama tabel tidak valid: " + tabel);
                }

                if (idR.equalsIgnoreCase(idRuanganScan)) {
                    tabelTujuan = tabel;
                    namaRuangTujuan = namaR;
                }

                try (PreparedStatement psCek = conn.prepareStatement(
                        "SELECT 1 FROM " + tabel + " WHERE nim = ?")) {
                    psCek.setString(1, nim);
                    try (ResultSet rsCek = psCek.executeQuery()) {
                        if (rsCek.next()) {
                            idSekarang = idR;
                            tabelSekarang = tabel;
                            namaRuangSekarang = namaR;
                        }
                    }
                }
            }
        }

        if (tabelTujuan == null) {
            return "GAGAL: Ruangan tidak valid!";
        }

        if (idSekarang == null) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + tabelTujuan + " (nim) VALUES (?)")) {
                ps.setString(1, nim);
                ps.executeUpdate();
            }
            return "MASUK (" + namaRuangTujuan + "): Selamat datang, " + nama + "!";
        }

        if (idSekarang.equalsIgnoreCase(idRuanganScan)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO log_presensi (nim, id_ruangan, waktu_masuk, durasi_kunjungan) " +
                    "SELECT nim, ?, waktu_masuk, TIMEDIFF(CURRENT_TIMESTAMP, waktu_masuk) " +
                    "FROM " + tabelSekarang + " WHERE nim = ?")) {
                ps.setString(1, idSekarang);
                ps.setString(2, nim);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + tabelSekarang + " WHERE nim = ?")) {
                ps.setString(1, nim);
                ps.executeUpdate();
            }
            return "KELUAR (" + namaRuangTujuan + "): Sampai jumpa, " + nama + "!";
        }

        return "DITOLAK: Anda masih berada di " + namaRuangSekarang + "!";
    }
}