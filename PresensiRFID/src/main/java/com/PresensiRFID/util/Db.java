package com.PresensiRFID.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Pintu masuk JDBC ke db_absensi. Servlet meminta Connection lewat get() untuk
 * menjalankan query. Worker memakai batas waktu koneksi dan baca untuk shutdown.
 */
public final class Db {
    private static final String URL = lingkungan("KITAHADIR_DB_URL",
            "jdbc:mysql://localhost:3306/db_absensi?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8");
    private static final String USER = lingkungan("KITAHADIR_DB_USER", "root");
    private static final String PASS = lingkungan("KITAHADIR_DB_PASSWORD", "");

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Db() {}

    public static Connection get() throws SQLException {
        return buka(URL);
    }

    static Connection getUntukWorker() throws SQLException {
        return buka(URL + (URL.contains("?") ? "&" : "?") + "connectTimeout=5000&socketTimeout=10000");
    }

    private static String lingkungan(String nama, String nilaiAwal) {
        String nilai = System.getenv(nama);
        return nilai == null ? nilaiAwal : nilai;
    }

    private static Connection buka(String url) throws SQLException {
        try {
            return DriverManager.getConnection(url, USER, PASS);
        } catch (SQLException e) {
            // Pesan driver bisa memuat identitas akun. Pertahankan kode diagnosis JDBC.
            throw new SQLException("Koneksi MySQL gagal.", e.getSQLState(), e.getErrorCode());
        }
    }
}
