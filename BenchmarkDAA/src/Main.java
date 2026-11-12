import java.sql.SQLException;
import java.util.Scanner;

public class Main {
    private static final Scanner IN = new Scanner(System.in);

    private static int sumber = 0;   // indeks di Konfigurasi.SUMBER
    private static int ukuran = 1;   // indeks di Konfigurasi.UKURAN_N

    // Data hasil fetch disimpan agar tidak perlu query ulang untuk pilihan yang sama
    private static String[] cache = null;
    private static int cacheSumber = -1;
    private static int cacheN = -1;

    public static void main(String[] args) {
        boolean lanjut = true;
        while (lanjut) {
            cetakMenu();
            int pilih = bacaAngka("Pilihan > ");
            switch (pilih) {
                case 1: pilihSumber(); break;
                case 2: pilihUkuran(); break;
                case 3: case 4: case 5: case 6: jalankan(pilih); break;
                case 0: lanjut = false; break;
                default: System.out.println("Pilihan tidak dikenal.");
            }
        }
        System.out.println("Selesai.");
    }

    private static void cetakMenu() {
        String[] s = Konfigurasi.SUMBER[sumber];
        System.out.println();
        System.out.println("=============== BENCHMARK DAA - PresensiRFID ===============");
        System.out.println("Sumber data : " + s[0] + " (tabel " + s[1] + ", kolom " + s[2] + ")");
        System.out.println("Ukuran N    : " + Benchmark.angka(Konfigurasi.UKURAN_N[ukuran]));
        System.out.println("------------------------------------------------------------");
        System.out.println("1. Pilih sumber tabel");
        System.out.println("2. Pilih ukuran sampel (N)");
        System.out.println("3. Benchmark Sorting (Bubble, Selection, Insertion, Merge, Quick)");
        System.out.println("4. Benchmark Searching (Sequential, Binary Iteratif, Binary Rekursif)");
        System.out.println("5. Benchmark MinMaks (Brute Force vs Divide and Conquer)");
        System.out.println("6. Uji khusus Insertion Sort Best Case (data terurut)");
        System.out.println("0. Keluar");
    }

    private static void pilihSumber() {
        for (int i = 0; i < Konfigurasi.SUMBER.length; i++) {
            String[] s = Konfigurasi.SUMBER[i];
            System.out.println((i + 1) + ". " + s[0] + " (tabel " + s[1] + ", kolom " + s[2] + ")");
        }
        int p = bacaAngka("Pilih sumber > ");
        if (p >= 1 && p <= Konfigurasi.SUMBER.length) {
            sumber = p - 1;
        } else {
            System.out.println("Pilihan tidak valid, sumber tidak diubah.");
        }
    }

    private static void pilihUkuran() {
        for (int i = 0; i < Konfigurasi.UKURAN_N.length; i++) {
            System.out.println((i + 1) + ". N = " + Benchmark.angka(Konfigurasi.UKURAN_N[i]));
        }
        int p = bacaAngka("Pilih ukuran > ");
        if (p >= 1 && p <= Konfigurasi.UKURAN_N.length) {
            ukuran = p - 1;
        } else {
            System.out.println("Pilihan tidak valid, ukuran tidak diubah.");
        }
    }

    private static void jalankan(int pilih) {
        try {
            int n = Konfigurasi.UKURAN_N[ukuran];
            if (pilih == 6) {
                System.out.println("1. N = 1.000.000");
                System.out.println("2. N = 2.000.000");
                int p = bacaAngka("Pilih ukuran > ");
                if (p != 1 && p != 2) {
                    System.out.println("Pilihan tidak valid.");
                    return;
                }
                n = (p == 1) ? 1000000 : 2000000;
            }

            String[] data = ambilData(sumber, n);
            if (data.length == 0) {
                System.out.println("Tabel tidak berisi data.");
                return;
            }

            String[] s = Konfigurasi.SUMBER[sumber];
            String info = "Tabel " + s[1] + " | Kolom " + s[2] + " | N = " + Benchmark.angka(data.length);
            switch (pilih) {
                case 3: Benchmark.sorting(data, info); break;
                case 4: Benchmark.searching(data, info); break;
                case 5: Benchmark.minMaks(data, info); break;
                default: Benchmark.insertionBestCase(data, info); break;
            }
        } catch (SQLException e) {
            System.out.println("Gagal mengambil data dari database: " + e.getMessage());
        } catch (OutOfMemoryError e) {
            cache = null;
            System.out.println("Memori JVM tidak cukup. Jalankan ulang dengan opsi -Xmx lebih besar, misalnya -Xmx4g.");
        }
    }

    private static String[] ambilData(int s, int n) throws SQLException {
        if (cache != null && cacheSumber == s && cacheN == n) {
            return cache;
        }
        cache = null;
        String[] cfg = Konfigurasi.SUMBER[s];
        System.out.println("Memuat " + Benchmark.angka(n) + " baris dari tabel " + cfg[1]
                + " (kolom " + cfg[2] + ") ...");
        long t0 = System.nanoTime();
        String[] hasil = DataLoader.muat(cfg[1], cfg[2], n);
        double detik = (System.nanoTime() - t0) / 1_000_000_000.0;
        System.out.println("Dimuat " + Benchmark.angka(hasil.length) + " baris dalam "
                + String.format("%.2f", detik) + " detik (urutan diacak dengan seed tetap).");
        if (hasil.length < n) {
            System.out.println("Peringatan: tabel hanya memiliki " + Benchmark.angka(hasil.length)
                    + " baris, N yang dipakai menyesuaikan.");
        }
        cache = hasil;
        cacheSumber = s;
        cacheN = n;
        return cache;
    }

    private static int bacaAngka(String prompt) {
        System.out.print(prompt);
        String teks = IN.nextLine().trim();
        try {
            return Integer.parseInt(teks);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}