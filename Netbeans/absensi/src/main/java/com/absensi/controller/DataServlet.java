package com.absensi.controller;

import com.absensi.util.Db;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/data")
public class DataServlet extends HttpServlet {

    private static final String TS = "DATE_FORMAT(%s, '%%Y-%%m-%%d %%H:%%i:%%s')";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!Auth.isLoggedIn(request)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "error");
            err.put("message", "Silakan login terlebih dahulu.");
            JsonUtil.write(response, HttpServletResponse.SC_UNAUTHORIZED, err);
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();

        try (Connection conn = Db.get(); Statement st = conn.createStatement()) {

            List<Map<String, Object>> ruangan = new ArrayList<>();
            List<String[]> daftarRuang = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT id_ruangan, nama_ruangan, tabel_aktif FROM ruangan ORDER BY id_ruangan")) {
                while (rs.next()) {
                    daftarRuang.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
                }
            }
            for (String[] r : daftarRuang) {
                if (!r[2].matches("[A-Za-z0-9_]+")) {
                    continue;
                }
                Map<String, Object> room = new LinkedHashMap<>();
                room.put("id", r[0]);
                room.put("nama", r[1]);
                List<Map<String, Object>> aktif = new ArrayList<>();
                String sql = "SELECT m.nim, m.nama, m.kelas, " + String.format(TS, "a.waktu_masuk") + " AS masuk "
                        + "FROM " + r[2] + " a JOIN mahasiswa m ON m.nim = a.nim ORDER BY a.waktu_masuk";
                try (ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("nim", rs.getString("nim"));
                        row.put("nama", rs.getString("nama"));
                        row.put("kelas", rs.getString("kelas"));
                        row.put("masuk", rs.getString("masuk"));
                        aktif.add(row);
                    }
                }
                room.put("aktif", aktif);
                ruangan.add(room);
            }
            result.put("ruangan", ruangan);

            List<Map<String, Object>> logs = new ArrayList<>();
            String sqlLog = "SELECT l.id_log, m.nim, m.nama, r.nama_ruangan, "
                    + String.format(TS, "l.waktu_masuk") + " AS masuk, "
                    + String.format(TS, "l.waktu_keluar") + " AS keluar, "
                    + "TIME_FORMAT(l.durasi_kunjungan, '%H:%i:%s') AS durasi "
                    + "FROM log_presensi l JOIN mahasiswa m ON m.nim = l.nim "
                    + "JOIN ruangan r ON r.id_ruangan = l.id_ruangan "
                    + "ORDER BY l.id_log DESC LIMIT 200";
            try (ResultSet rs = st.executeQuery(sqlLog)) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getInt("id_log"));
                    row.put("nim", rs.getString("nim"));
                    row.put("nama", rs.getString("nama"));
                    row.put("ruangan", rs.getString("nama_ruangan"));
                    row.put("masuk", rs.getString("masuk"));
                    row.put("keluar", rs.getString("keluar"));
                    row.put("durasi", rs.getString("durasi"));
                    logs.add(row);
                }
            }
            result.put("log", logs);

            List<Map<String, Object>> mhs = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT nim, nama, kelas, uid_rfid FROM mahasiswa ORDER BY nim")) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("nim", rs.getString("nim"));
                    row.put("nama", rs.getString("nama"));
                    row.put("kelas", rs.getString("kelas"));
                    row.put("uid", rs.getString("uid_rfid"));
                    mhs.add(row);
                }
            }
            result.put("mahasiswa", mhs);

            List<Map<String, Object>> dosen = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT nip, nama, email FROM dosen ORDER BY nama")) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("nip", rs.getString("nip"));
                    row.put("nama", rs.getString("nama"));
                    row.put("email", rs.getString("email"));
                    dosen.add(row);
                }
            }
            result.put("dosen", dosen);

            JsonUtil.write(response, HttpServletResponse.SC_OK, result);
        } catch (SQLException e) {
            log("Error data", e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "error");
            err.put("message", e.getMessage());
            JsonUtil.write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, err);
        }
    }
}