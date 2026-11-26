package com.PresensiRFID.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * PemuatData dan TabelServlet memakai Tabel untuk membaca mahasiswa, dosen,
 * dan riwayat ke memori. Baris hasil query diacak dengan seed tetap agar urutan
 * awal dapat diulang. Pilihan kolom urut pada halaman web menentukan kapan
 * Sorting.mergeSort dipanggil untuk mengurutkan salinan data.
 */
public final class DataMemori {
    private static final long SEED = 12321L;

    // Basis Data Pertemuan 5 — LEFT JOIN pada riwayat
    // log_presensi menyimpan NIM dan ID ruangan setiap kunjungan. Dua LEFT JOIN
    // mencari nama mahasiswa dan nama ruangan untuk baris log tersebut.
    // COALESCE memberi teks pengganti bila hasil pencocokan kosong.
    // TabelServlet.barisLog memakai urutan kolom ini saat membentuk JSON untuk web.
    private static final String SQL_LOG =
            "SELECT l.id_log, l.nim, "
            + "COALESCE(m.nama, '(tidak ada di data mahasiswa)'), "
            + "COALESCE(r.nama_ruangan, l.id_ruangan), "
            + "DATE_FORMAT(l.waktu_masuk, '%Y-%m-%d %H:%i:%s'), "
            + "DATE_FORMAT(l.waktu_keluar, '%Y-%m-%d %H:%i:%s'), "
            + "TIME_FORMAT(l.durasi_kunjungan, '%H:%i:%s') "
            + "FROM log_presensi l "
            + "LEFT JOIN mahasiswa m ON m.nim = l.nim "
            + "LEFT JOIN ruangan r ON r.id_ruangan = l.id_ruangan";

    /** Kolom: nim, nama, kelas, uid_rfid */
    public static final Tabel MAHASISWA = new Tabel("mahasiswa",
            "SELECT nim, nama, kelas, uid_rfid FROM mahasiswa", 4, 2, null, null);

    /** Kolom: nip, nama, email */
    public static final Tabel DOSEN = new Tabel("dosen",
            "SELECT nip, nama, email FROM dosen", 3, -1, null, null);

    /** Kolom: id_log, nim, nama, ruangan, masuk, keluar, durasi. */
    public static final Tabel LOG = new Tabel("log_presensi",
            SQL_LOG, 7, 3,
            SQL_LOG + " WHERE l.id_log > ?",
            "SELECT COUNT(*), (SELECT COALESCE(MAX(id_log), 0) FROM log_presensi) "
                    + "FROM log_presensi WHERE id_log <= ?");

    private DataMemori() {}

    public static void hapusIdentitasMahasiswa() {
        // Riwayat menyimpan nama dari hasil JOIN. Hapus kedua cache setelah commit
        // agar permintaan berikutnya membaca nama terbaru, termasuk di kunjungan lama.
        MAHASISWA.hapus();
        LOG.hapus();
    }

    public static final class Tabel {
        private final String tabelDb;
        private final String sqlMuat;
        private final int jumlahKolom;
        private final int kolomSama;        // kolom yang nilainya berulang (kelas, ruangan), String-nya dipakai bersama
        private final String sqlBaru;       // baris log di atas ID terakhir dalam cache
        private final String sqlRingkasanLog;

        private String[][] data;            // urutan acak, tidak pernah diurutkan
        private long idTerakhir;
        private long totalDilihat = -1;

        private String[][] terurut;         // hasil Merge Sort terakhir (dipakai ulang saat pindah halaman)
        private String[][] sumberUrut;
        private String kunciUrut;
        private boolean turunUrut;

        private Tabel(String tabelDb, String sqlMuat, int jumlahKolom, int kolomSama,
                      String sqlBaru, String sqlRingkasanLog) {
            this.tabelDb = tabelDb;
            this.sqlMuat = sqlMuat;
            this.jumlahKolom = jumlahKolom;
            this.kolomSama = kolomSama;
            this.sqlBaru = sqlBaru;
            this.sqlRingkasanLog = sqlRingkasanLog;
        }

        /**
         * PBO Modul 9 — Akses tersinkronisasi ke data tabel
         * Permintaan pertama mengisi array. Untuk log, perbaruiLog memeriksa rentang
         * yang sudah dimuat sebelum menambahkan ID yang lebih besar. Untuk mahasiswa
         * dan dosen, perubahan jumlah baris yang terlihat memicu pembacaan ulang.
         * hapus() memaksa pemuatan berikutnya.
         * synchronized membatasi pengolahan pada satu pemanggil per objek Tabel.
         */
        public synchronized String[][] mentah(Connection conn) throws SQLException {
            if (data == null) {
                data = baca(conn, sqlMuat, false);
                totalDilihat = sqlRingkasanLog == null ? CacheTotal.ambil(conn, tabelDb) : -1;
            } else if (sqlRingkasanLog != null) {
                perbaruiLog(conn);
            } else {
                long total = CacheTotal.ambil(conn, tabelDb);
                if (total != totalDilihat) {
                    data = baca(conn, sqlMuat, false);
                    totalDilihat = total;
                }
            }
            return data;
        }

        /** Salinan data yang diurutkan Merge Sort dari data acak. Hasil terakhir disimpan. */
        public synchronized String[][] terurut(Connection conn, String kunci, boolean turun,
                                               Comparator<String[]> cmp) throws SQLException {
            String[][] sumber = mentah(conn);
            if (terurut != null && sumber == sumberUrut && kunci.equals(kunciUrut) && turun == turunUrut) {
                return terurut;
            }
            String[][] salinan = Arrays.copyOf(sumber, sumber.length);
            Sorting.mergeSort(salinan, turun ? cmp.reversed() : cmp);
            terurut = salinan;
            sumberUrut = sumber;
            kunciUrut = kunci;
            turunUrut = turun;
            return salinan;
        }

        /** Panggil setelah data tabel diubah lewat servlet lain, agar dimuat ulang pada permintaan berikutnya. */
        public synchronized void hapus() {
            CacheTotal.hapus(tabelDb);
            data = null;
            terurut = null;
            sumberUrut = null;
            kunciUrut = null;
        }

        private void perbaruiLog(Connection conn) throws SQLException {
            long totalLama;
            long maks;
            try (PreparedStatement ps = conn.prepareStatement(sqlRingkasanLog)) {
                ps.setLong(1, idTerakhir);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    totalLama = rs.getLong(1);
                    maks = rs.getLong(2);
                }
            }

            // ID dialokasikan saat INSERT, jadi ID besar bisa commit lebih dulu.
            // Hitung baris committed dalam rentang cache agar ID kecil yang baru
            // terlihat memicu muat ulang dan celah akibat rollback tidak dihitung.
            if (totalLama != data.length) {
                data = baca(conn, sqlMuat, false);
            } else if (maks > idTerakhir) {
                String[][] baru = baca(conn, sqlBaru, true);
                String[][] gabung = Arrays.copyOf(data, data.length + baru.length);
                System.arraycopy(baru, 0, gabung, data.length, baru.length);
                data = gabung;
            }
        }

        private String[][] baca(Connection conn, String sql, boolean denganId) throws SQLException {
            List<String[]> daftar = new ArrayList<>();
            Map<String, String> sama = new HashMap<>();
            long maks = denganId ? idTerakhir : 0;
            try (PreparedStatement ps = conn.prepareStatement(sql,
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                ps.setFetchSize(Integer.MIN_VALUE);   // streaming, jutaan baris tidak menumpuk di driver
                if (denganId) {
                    ps.setLong(1, idTerakhir);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String[] baris = new String[jumlahKolom];
                        for (int k = 0; k < jumlahKolom; k++) {
                            String v = rs.getString(k + 1);
                            if (k == kolomSama && v != null) {
                                String lama = sama.putIfAbsent(v, v);
                                if (lama != null) {
                                    v = lama;
                                }
                            }
                            baris[k] = v;
                        }
                        if (sqlRingkasanLog != null) {
                            maks = Math.max(maks, Long.parseLong(baris[0]));
                        }
                        daftar.add(baris);
                    }
                }
            }
            String[][] hasil = daftar.toArray(new String[0][]);
            acak(hasil);
            idTerakhir = maks;
            return hasil;
        }
    }

    /** Fisher-Yates dengan seed tetap, supaya urutan awal pasti acak dan hasil bisa diulang. */
    private static void acak(String[][] a) {
        Random r = new Random(SEED);
        for (int i = a.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            String[] tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }
}
