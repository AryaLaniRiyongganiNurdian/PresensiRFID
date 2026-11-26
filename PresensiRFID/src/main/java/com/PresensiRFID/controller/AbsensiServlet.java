package com.PresensiRFID.controller;

import com.PresensiRFID.model.MahasiswaAbsensi;
import com.PresensiRFID.util.CatatanHarian;
import com.PresensiRFID.util.Db;
import com.PresensiRFID.util.KartuTerakhir;
import com.PresensiRFID.util.Penghitung;
import com.PresensiRFID.util.TransaksiPresensi;
import com.PresensiRFID.util.TransaksiPresensi.Kunjungan;
import com.PresensiRFID.util.TransaksiPresensi.Mahasiswa;
import com.PresensiRFID.util.TransaksiPresensi.Ruangan;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/** Reader mengirim formulir UID dan ID ruangan ke POST /proses_rfid. */
@WebServlet("/proses_rfid")
public class AbsensiServlet extends HttpServlet {

    private static final int PORT_READER = Integer.getInteger("kitahadir.reader.port", 8080);
    private static final String TOKEN_READER = System.getenv("KITAHADIR_READER_TOKEN");

    // PBO Modul 1 — Constructor default implisit membuat penghitung untuk /api/status.
    public static final Penghitung TOTAL_TAP = new Penghitung();

    private record Hasil(int status, String kategori, String pesan) {}

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        // Periksa port soket penerima setelah Tomcat memetakan request ke servlet ini.
        if (request.getLocalPort() != PORT_READER) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String token = request.getHeader("X-Reader-Token");
        if (TOKEN_READER == null || TOKEN_READER.isBlank() || token == null
                || !MessageDigest.isEqual(TOKEN_READER.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        super.service(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain;charset=UTF-8");

        String uid = request.getParameter("uid");
        String idRuangan = request.getParameter("id_ruangan");
        if (uid == null || uid.isBlank() || idRuangan == null || idRuangan.isBlank()) {
            kirim(response, new Hasil(400, "error", "Parameter tidak lengkap."));
            return;
        }
        uid = uid.trim().toUpperCase(Locale.ROOT);
        idRuangan = idRuangan.trim();
        TOTAL_TAP.tambah();

        Hasil hasil = null;
        try (Connection conn = Db.get()) {
            TransaksiPresensi.mulai(conn);
            try {
                Hasil calon = proses(conn, uid, idRuangan);
                conn.commit();
                hasil = calon;
            } catch (SQLException e) {
                TransaksiPresensi.rollback(conn, e);
                throw e;
            }
        } catch (SQLException e) {
            log("Error proses_rfid", e);
            if (hasil == null) {
                kirim(response, new Hasil(500, "error", "Hasil tap belum dapat dikonfirmasi. Periksa dashboard."));
                return;
            }
        }

        if ("kartu_baru".equals(hasil.kategori())) {
            KartuTerakhir.simpan(uid);
        }
        CatatanHarian.UTAMA.tulisCatatan(CatatanHarian.sekarang(),
                "RFID " + idRuangan + " | " + hasil.pesan() + " | kartu " + MahasiswaAbsensi.maskingUid(uid));
        kirim(response, hasil);
    }

    private Hasil proses(Connection conn, String uid, String idRuangan) throws SQLException {
        String nim = cariNim(conn, uid);
        if (nim == null) {
            return new Hasil(409, "kartu_baru", "Kartu belum terdaftar");
        }
        Mahasiswa mahasiswa = TransaksiPresensi.kunciMahasiswa(conn, nim);
        if (mahasiswa == null || !uid.equalsIgnoreCase(mahasiswa.uid())) {
            return new Hasil(409, "tolak", "Tautan kartu berubah. Periksa dashboard.");
        }

        List<Ruangan> ruangan = TransaksiPresensi.daftarRuangan(conn);
        Ruangan tujuan = ruangan.stream().filter(r -> r.id().equalsIgnoreCase(idRuangan)).findFirst().orElse(null);
        if (tujuan == null) {
            return new Hasil(409, "tolak", "Ruangan tidak valid!");
        }

        Kunjungan aktif = TransaksiPresensi.posisi(conn, ruangan, mahasiswa.nim());
        if (aktif == null) {
            TransaksiPresensi.masuk(conn, tujuan, mahasiswa.nim());
            return new Hasil(200, "masuk", "MASUK " + mahasiswa.nama());
        }
        if (aktif.ruangan().id().equals(tujuan.id())) {
            TransaksiPresensi.pindahKeRiwayat(conn, aktif);
            return new Hasil(200, "keluar", "KELUAR " + mahasiswa.nama());
        }
        return new Hasil(409, "tolak", "Masih berada di " + aktif.ruangan().nama() + "!");
    }

    // Basis Data Pertemuan 4 — SELECT dan WHERE menemukan NIM pada indeks UID.
    // Setelah memperoleh kunci NIM, proses memeriksa ulang tautan sebelum mengubah presensi.
    private String cariNim(Connection conn, String uid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT nim FROM mahasiswa WHERE uid_rfid = ?")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void kirim(HttpServletResponse response, Hasil hasil) throws IOException {
        response.setStatus(hasil.status());
        response.setHeader("X-Presensi-Hasil", hasil.kategori());
        response.getWriter().print(hasil.pesan());
    }
}
