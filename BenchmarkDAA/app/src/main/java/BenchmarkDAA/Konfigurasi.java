public final class Konfigurasi {
    private Konfigurasi() {}

    // Koneksi database (sama dengan Db.java di proyek PresensiRFID)
    public static final String DB_URL =
            "jdbc:mysql://localhost:3306/db_absensi?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
    public static final String DB_USER = "root";
    public static final String DB_PASS = "";

    // Sumber data: {nama di menu, nama tabel, kolom kunci yang diurutkan / dicari}
    // Jika tabel riwayat Anda bernama history_absen dengan kolom waktu_absen,
    // cukup ubah dua nilai pada baris ketiga.
    public static final String[][] SUMBER = {
        {"dosen", "dosen", "nip"},
        {"mahasiswa", "mahasiswa", "nim"},
        {"history_absen", "log_presensi", "waktu_masuk"}
    };

    public static final int[] UKURAN_N = {1000, 10000, 50000, 100000, 1000000, 2000000};

    public static final int BATAS_KUADRATIK = 100000;   // N di atas ini: O(n^2) di-skip
    public static final int UKURAN_KALIBRASI = 10000;   // sampel untuk estimasi waktu teoritis
    public static final int ULANG_CEPAT = 100000;       // ulangan search yang sangat cepat
    public static final int ULANG_LAMBAT = 100;         // ulangan sequential search kasus rata-rata dan terburuk
    public static final int ULANG_MINMAKS = 5;
    public static final long SEED = 12321L;
}