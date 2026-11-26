package com.PresensiRFID.controller;

import com.PresensiRFID.model.Dosen;
import com.PresensiRFID.util.Db;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Formulir login mengirim email dan kata sandi ke POST /api/login.
 * Servlet mencocokkannya dengan tabel dosen dan menyimpan objek Dosen dalam sesi.
 * GET /api/me membaca identitas sesi saat halaman dibuka. POST /api/logout mengakhirinya.
 */
@WebServlet(urlPatterns = {"/api/login", "/api/me", "/api/logout"})
public class LoginServlet extends HttpServlet {

    public static final String SESSION_DOSEN = "dosen";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();

        if ("/api/logout".equals(path)) {
            HttpSession s = request.getSession(false);
            if (s != null) {
                s.invalidate();
            }
            JsonUtil.write(response, HttpServletResponse.SC_OK, ok());
            return;
        }

        if (!"/api/login".equals(path)) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        String email = request.getParameter("email");
        String password = request.getParameter("password");
        if (email == null || password == null) {
            JsonUtil.write(response, HttpServletResponse.SC_BAD_REQUEST, JsonUtil.msg("error", "Email dan password wajib diisi."));
            return;
        }

        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT nama, nip, email FROM dosen WHERE email = ? AND password = SHA2(?, 256)")) {
            ps.setString(1, email.trim());
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                // PBO Modul 1 — Membuat objek Dosen dari hasil query
                // Jika rs.next() menemukan akun yang cocok, constructor menerima nama,
                // NIP, dan email dari baris itu. Sesi lama diganti dan sesi baru menyimpan
                // referensi ke objek d. PresensiServlet kemudian dapat mengambilnya lewat Auth
                // untuk mencatat dosen yang melakukan input manual.
                if (rs.next()) {
                    Dosen d = new Dosen(rs.getString("nama"), rs.getString("nip"), rs.getString("email"));
                    HttpSession old = request.getSession(false);
                    if (old != null) {
                        old.invalidate();
                    }
                    request.getSession(true).setAttribute(SESSION_DOSEN, d);
                    JsonUtil.write(response, HttpServletResponse.SC_OK, dosenJson(d));
                } else {
                    JsonUtil.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                            JsonUtil.msg("error", "Email atau password salah!"));
                }
            }
        } catch (SQLException e) {
            log("Error login", e);
            JsonUtil.write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonUtil.msg("error", "Kesalahan server: " + e.getMessage()));
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!"/api/me".equals(request.getServletPath())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        HttpSession s = request.getSession(false);
        Dosen d = (s == null) ? null : (Dosen) s.getAttribute(SESSION_DOSEN);
        if (d == null) {
            JsonUtil.write(response, HttpServletResponse.SC_OK, loggedOut());
        } else {
            JsonUtil.write(response, HttpServletResponse.SC_OK, dosenJson(d));
        }
    }

    private static JsonObject dosenJson(Dosen d) {
        JsonObject o = new JsonObject();
        o.addProperty("status", "success");
        o.addProperty("nama", d.getNama());
        o.addProperty("nip", d.getNomorIdentitas());
        o.addProperty("email", d.getEmail());
        o.addProperty("peran", d.getPeran());
        return o;
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("status", "success");
        return o;
    }

    private static JsonObject loggedOut() {
        JsonObject o = new JsonObject();
        o.addProperty("status", "anonymous");
        return o;
    }
}