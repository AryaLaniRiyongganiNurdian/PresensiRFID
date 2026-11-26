package com.PresensiRFID.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Servlet API menyerahkan status HTTP dan objek hasil ke write(). Gson mengubah objek menjadi JSON UTF-8 untuk browser. */
final class JsonUtil {
    private static final Gson GSON = new Gson();

    private JsonUtil() {}

    static JsonObject msg(String status, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("status", status);
        o.addProperty("message", message);
        return o;
    }

    static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().print(GSON.toJson(body));
    }
}