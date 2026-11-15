import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Random;

public final class DataLoader {
    private DataLoader() {}

    // Mengambil n baris pertama kolom kunci memakai LIMIT (tanpa WHERE dan ORDER BY).
    public static String[] muat(String tabel, String kolom, int n) throws SQLException {
        String[] hasil = new String[n];
        int terisi = 0;
        String sql = "SELECT " + kolom + " FROM " + tabel + " LIMIT " + n;

        try (Connection c = DriverManager.getConnection(
                     Konfigurasi.DB_URL, Konfigurasi.DB_USER, Konfigurasi.DB_PASS);
             Statement st = c.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            st.setFetchSize(Integer.MIN_VALUE);   // streaming agar 2 juta baris tidak menumpuk di driver
            try (ResultSet rs = st.executeQuery(sql)) {
                while (terisi < n && rs.next()) {
                    hasil[terisi++] = rs.getString(1);
                }
            }
        }

        if (terisi < n) {
            hasil = Arrays.copyOf(hasil, terisi);
        }
        acak(hasil);
        return hasil;
    }

    // Fisher-Yates dengan seed tetap, supaya urutan awal pasti acak dan hasil bisa diulang.
    private static void acak(String[] a) {
        Random r = new Random(Konfigurasi.SEED);
        for (int i = a.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            String tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }
}