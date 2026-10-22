package com.absensi.controller;

import com.absensi.util.Db;
import com.absensi.util.KartuTerakhir;
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

        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement("UPDATE mahasiswa SET uid_rfid = ? WHERE nim = ?")) {
            ps.setString(1, uid);
            ps.setString(2, nim);
            if (ps.executeUpdate() == 0) {
                JsonUtil.write(response, HttpServletResponse.SC_NOT_FOUND, msg("error", "NIM tidak ditemukan."));
                return;
            }
            KartuTerakhir.hapus();
            JsonUtil.write(response, HttpServletResponse.SC_OK, msg("success", "Kartu berhasil ditautkan ke " + nim + "."));
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