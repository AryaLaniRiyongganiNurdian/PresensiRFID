package com.absensi.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

final class Auth {
    private Auth() {}

    static boolean isLoggedIn(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute(LoginServlet.SESSION_DOSEN) != null;
    }
}