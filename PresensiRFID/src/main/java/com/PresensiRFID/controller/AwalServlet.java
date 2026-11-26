package com.PresensiRFID.controller;

import com.PresensiRFID.util.DataMemori;
import com.PresensiRFID.util.PembersihPresensi;
import com.PresensiRFID.util.PemuatData;
import com.google.gson.JsonObject;
import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * Container memanggil init() saat aplikasi dimuat untuk memulai pemuat data dan pembersih.
 * GET /api/status mengembalikan jumlah tap dan keadaan thread pembersih kepada dosen.
 * Saat aplikasi dihentikan, destroy() meminta thread berhenti dan melepas driver JDBC.
 */
@WebServlet(urlPatterns = "/api/status", loadOnStartup = 1)
public class AwalServlet extends HttpServlet {

    private PemuatData[] pemuat;
    private Thread pembersih;

    @Override
    public void init() {
        // PBO Modul 4 dan 9 — Array objek tetap dan start()
        // Array memiliki tiga objek PemuatData, satu untuk tiap tabel di DataMemori.
        // start() menjadwalkan run() tiap pemuat sehingga pembacaan dapat berlangsung bersamaan.
        pemuat = new PemuatData[]{
                new PemuatData("mahasiswa", DataMemori.MAHASISWA),
                new PemuatData("dosen", DataMemori.DOSEN),
                new PemuatData("log_presensi", DataMemori.LOG)
        };
        for (PemuatData p : pemuat) {
            p.start();
        }

        // PBO Modul 9 — Thread ini menjalankan Runnable PembersihPresensi secara berkala.
        pembersih = new Thread(new PembersihPresensi(), "pembersih-presensi");
        pembersih.setDaemon(true);
        pembersih.start();
    }

    @Override
    public void destroy() {
        long batas = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        pembersih.interrupt();
        for (PemuatData p : pemuat) {
            p.interrupt();
        }
        for (PemuatData p : pemuat) {
            try {
                p.hentikan();
            } catch (SQLException | RuntimeException e) {
                log("Gagal membatalkan " + p.getName(), e);
            }
        }

        // PBO Modul 9 — join menunggu loader dan pembersih dengan satu tenggat.
        try {
            for (PemuatData p : pemuat) {
                tungguBerhenti(p, batas);
            }
            tungguBerhenti(pembersih, batas);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        tutupDriverJdbc(batas);
    }

    private void tungguBerhenti(Thread thread, long batas) throws InterruptedException {
        if (thread.isAlive()) {
            long sisa = batas - System.nanoTime();
            if (sisa > 0) {
                thread.join(TimeUnit.NANOSECONDS.toMillis(sisa), (int) (sisa % 1_000_000));
            }
            if (thread.isAlive()) {
                log("Thread belum berhenti: " + thread.getName());
            }
        }
    }

    private void tutupDriverJdbc(long batas) {
        ClassLoader pemilik = AwalServlet.class.getClassLoader();
        boolean driverLokal = AbandonedConnectionCleanupThread.class.getClassLoader() == pemilik;
        if (driverLokal) {
            AbandonedConnectionCleanupThread.checkedShutdown();
        }
        DriverManager.drivers().filter(driver -> driver.getClass().getClassLoader() == pemilik)
                .forEach(driver -> {
                    try {
                        DriverManager.deregisterDriver(driver);
                    } catch (SQLException e) {
                        log("Gagal melepas driver JDBC " + driver.getClass().getName(), e);
                    }
                });
        if (driverLokal) {
            // Connector/J menghentikan thread pembersih secara asinkron.
            try {
                while (AbandonedConnectionCleanupThread.isAlive() && System.nanoTime() < batas) {
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (AbandonedConnectionCleanupThread.isAlive()) {
                log("Thread pembersih MySQL belum berhenti.");
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!Auth.isLoggedIn(request)) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Silakan login terlebih dahulu.");
            JsonUtil.write(response, HttpServletResponse.SC_UNAUTHORIZED, err);
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("status", "success");
        o.addProperty("tap_sejak_nyala", AbsensiServlet.TOTAL_TAP.getNilai());
        o.addProperty("status_pembersih", pembersih == null ? "belum dijalankan" : pembersih.getState().toString());
        JsonUtil.write(response, HttpServletResponse.SC_OK, o);
    }
}
