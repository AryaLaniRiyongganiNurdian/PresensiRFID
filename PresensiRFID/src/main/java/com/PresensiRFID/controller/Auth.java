package com.PresensiRFID.controller;

import com.PresensiRFID.model.Dosen;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Servlet memakai helper ini untuk memeriksa sesi dan mengambil objek Dosen yang disimpan LoginServlet. */
final class Auth {
    private Auth() {}

    static boolean isLoggedIn(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute(LoginServlet.SESSION_DOSEN) != null;
    }

    /** Dosen yang sedang login, atau null. */
    static Dosen dosen(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s == null ? null : (Dosen) s.getAttribute(LoginServlet.SESSION_DOSEN);
    }
}