package com.absensi.controller;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

final class JsonUtil {
    private static final Gson GSON = new Gson();

    private JsonUtil() {}

    static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().print(GSON.toJson(body));
    }
}