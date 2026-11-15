package com.absensi.controller;

import com.absensi.util.CacheTotal;
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
            try (ResultSet rs = st.executeQuery("SELECT id_ruangan, nama_ruangan, tabel_aktif FROM ruangan")) {
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
                        + "FROM " + r[2] + " a JOIN mahasiswa m ON m.nim = a.nim";
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

            result.put("total_mahasiswa", CacheTotal.ambil(conn, "mahasiswa"));
            result.put("total_log", CacheTotal.ambil(conn, "log_presensi"));
            result.put("total_dosen", CacheTotal.ambil(conn, "dosen"));

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