import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

public final class Benchmark {
    private static final Locale ID = Locale.forLanguageTag("id-ID");
    private static final String TIDAK_ADA = "~TIDAK_ADA";
    private static long sink = 0;   // menampung hasil pencarian agar pemanggilan tidak dibuang JIT

    private Benchmark() {}

    // ---------- Format dan pengukuran ----------
    public static String angka(long x) {
        return String.format(ID, "%,d", x);
    }

    public static String ms(double nilai) {
        return String.format(ID, "%,.4f", nilai);
    }

    private static String mb(long bytes) {
        return String.format(ID, "%,.2f MB", Math.max(0L, bytes) / 1048576.0);
    }

    private static long memoriTerpakai() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    private static double msDari(long awal, long akhir) {
        return (akhir - awal) / 1_000_000.0;
    }

    // ---------- Benchmark Sorting ----------
    public static void sorting(String[] asli, String info) {
        int n = asli.length;
        System.out.println();
        System.out.println("BENCHMARK SORTING | " + info);

        TabelAscii tabel = new TabelAscii("Algoritma", "Paradigma", "Kompleksitas",
                "Space (teori)", "Waktu (ms)", "Memori Tambahan", "Validasi");
        boolean adaEstimasi = false;

        for (int k = 0; k < Sorting.DAFTAR.length; k++) {
            String[] d = Sorting.DAFTAR[k];

            if (Sorting.kuadratik(k) && n > Konfigurasi.BATAS_KUADRATIK) {
                System.out.print("  > " + d[0] + " (N > batas, dihitung estimasi) ... ");
                double est = estimasiMs(k, asli);
                System.out.println("selesai");
                tabel.tambah(d[0], d[1], d[2], d[3], "~" + ms(est) + " (estimasi)", "-", "SKIP");
                adaEstimasi = true;
                continue;
            }

            System.out.print("  > " + d[0] + " ... ");
            String[] salinan = asli.clone();   // setiap algoritma menerima salinan identik
            System.gc();
            long mem0 = memoriTerpakai();
            long t0 = System.nanoTime();
            Sorting.jalankan(k, salinan);
            long t1 = System.nanoTime();
            long mem1 = memoriTerpakai();
            System.out.println("selesai");

            tabel.tambah(d[0], d[1], d[2], d[3], ms(msDari(t0, t1)),
                    mb(mem1 - mem0), Validator.labelSorted(salinan));
        }

        System.out.println();
        tabel.cetak();
        if (adaEstimasi) {
            System.out.println("Estimasi = waktu sampel " + angka(Konfigurasi.UKURAN_KALIBRASI)
                    + " data x (N / " + angka(Konfigurasi.UKURAN_KALIBRASI) + ")^2, karena N > "
                    + angka(Konfigurasi.BATAS_KUADRATIK) + ".");
        }
        System.out.println("Memori Tambahan = selisih memori terpakai (Runtime) sebelum dan sesudah sorting,"
                + " bersifat estimasi karena dipengaruhi Garbage Collector.");
    }

    private static double estimasiMs(int kode, String[] asli) {
        int m = Math.min(Konfigurasi.UKURAN_KALIBRASI, asli.length);
        String[] sampel = Arrays.copyOf(asli, m);
        long t0 = System.nanoTime();
        Sorting.jalankan(kode, sampel);
        long t1 = System.nanoTime();
        double rasio = (double) asli.length / m;
        return msDari(t0, t1) * rasio * rasio;
    }

    // ---------- Uji khusus: Insertion Sort pada data yang sudah terurut ----------
    public static void insertionBestCase(String[] asli, String info) {
        System.out.println();
        System.out.println("UJI INSERTION SORT BEST CASE | " + info);
        System.out.println("  > Menyiapkan data terurut (Merge Sort) ...");
        String[] urut = asli.clone();
        Sorting.mergeSort(urut);

        System.out.println("  > Insertion Sort pada data terurut ...");
        String[] salinan = urut.clone();
        System.gc();
        long mem0 = memoriTerpakai();
        long t0 = System.nanoTime();
        Sorting.insertionSort(salinan);
        long t1 = System.nanoTime();
        long mem1 = memoriTerpakai();

        double waktu = msDari(t0, t1);
        double nsPerElemen = (t1 - t0) / (double) salinan.length;

        TabelAscii tabel = new TabelAscii("Algoritma", "Kondisi Data", "N", "Waktu (ms)",
                "ns per Elemen", "Memori Tambahan", "Validasi");
        tabel.tambah("Insertion Sort", "Terurut (Best Case)", angka(salinan.length), ms(waktu),
                String.format(ID, "%,.2f", nsPerElemen), mb(mem1 - mem0), Validator.labelSorted(salinan));
        System.out.println();
        tabel.cetak();
        System.out.println("Pada Best Case tidak ada pergeseran elemen, sehingga waktu tumbuh linear O(n)."
                + " Bandingkan nilai ns per Elemen pada N berbeda: nilainya hampir konstan.");
    }

    // ---------- Benchmark Searching ----------
    public static void searching(String[] asli, String info) {
        int n = asli.length;
        System.out.println();
        System.out.println("BENCHMARK SEARCHING | " + info);
        System.out.println("  > Menyiapkan array terurut (Merge Sort) untuk Binary Search ...");
        String[] urut = asli.clone();
        Sorting.mergeSort(urut);
        if (!Validator.isSorted(urut)) {
            System.out.println("Array terurut INVALID, benchmark searching dibatalkan.");
            return;
        }

        String acak = asli[new Random(Konfigurasi.SEED).nextInt(n)];
        String[] namaSkenario = {
            "Best (indeks pertama)", "Average (acak)", "Worst (tidak ditemukan)", "Best Binary (elemen tengah)"
        };
        String[] target = {asli[0], acak, TIDAK_ADA, urut[(n - 1) >>> 1]};

        System.out.println("Target Best    : " + target[0]);
        System.out.println("Target Average : " + target[1]);
        System.out.println("Target Worst   : " + target[2]);
        System.out.println("Target Best Binary : " + target[3]);

        TabelAscii tabel = new TabelAscii("Skenario", "Algoritma", "Paradigma", "Kompleksitas",
                "Indeks", "Rata-rata Waktu (ms)", "Ulangan", "Validasi");

        for (int s = 0; s < target.length; s++) {
            String t = target[s];
            int repSeq = (s == 0) ? Konfigurasi.ULANG_CEPAT : Konfigurasi.ULANG_LAMBAT;
            int repBin = Konfigurasi.ULANG_CEPAT;

            int idxSeq = Searching.sequential(asli, t);
            int idxIter = Searching.binaryIteratif(urut, t);
            int idxRek = Searching.binaryRekursif(urut, t);
            String status = Validator.cocok(asli, urut, t, idxSeq, idxIter, idxRek);

            long t0 = System.nanoTime();
            for (int i = 0; i < repSeq; i++) {
                sink += Searching.sequential(asli, t);
            }
            double wSeq = msDari(t0, System.nanoTime()) / repSeq;

            t0 = System.nanoTime();
            for (int i = 0; i < repBin; i++) {
                sink += Searching.binaryIteratif(urut, t);
            }
            double wIter = msDari(t0, System.nanoTime()) / repBin;

            t0 = System.nanoTime();
            for (int i = 0; i < repBin; i++) {
                sink += Searching.binaryRekursif(urut, t);
            }
            double wRek = msDari(t0, System.nanoTime()) / repBin;

            tabel.tambah(namaSkenario[s], "Sequential Search", "Brute Force", "O(n)",
                    angka(idxSeq), ms(wSeq), angka(repSeq), status);
            tabel.tambah(namaSkenario[s], "Binary Iteratif", "Decrease and Conquer", "O(log n)",
                    angka(idxIter), ms(wIter), angka(repBin), status);
            tabel.tambah(namaSkenario[s], "Binary Rekursif", "Decrease and Conquer", "O(log n)",
                    angka(idxRek), ms(wRek), angka(repBin), status);
        }

        System.out.println();
        tabel.cetak();
        System.out.println("Sequential Search berjalan pada array acak, Binary Search pada array hasil Merge Sort."
                + " Indeks -1 berarti tidak ditemukan.");
        System.out.println("Waktu adalah rata-rata dari sejumlah ulangan karena satu kali pencarian"
                + " terlalu singkat untuk diukur.");
    }

    // ---------- Benchmark MinMaks ----------
    public static void minMaks(String[] a, String info) {
        long n = a.length;
        System.out.println();
        System.out.println("BENCHMARK MINMAKS | " + info);

        Searching.HasilMinMaks bf = null;
        Searching.HasilMinMaks dc = null;
        double totalBf = 0;
        double totalDc = 0;
        int ulang = Konfigurasi.ULANG_MINMAKS;
        for (int i = 0; i < ulang; i++) {
            long t0 = System.nanoTime();
            bf = Searching.minMaksBruteForce(a);
            totalBf += msDari(t0, System.nanoTime());

            t0 = System.nanoTime();
            dc = Searching.minMaksDivideConquer(a);
            totalDc += msDari(t0, System.nanoTime());
        }

        long teoriBf = 2 * n - 2;
        long teoriDc = (3 * n + 1) / 2 - 2;
        String status = (bf.min.equals(dc.min) && bf.maks.equals(dc.maks)) ? "MATCH" : "MISMATCH";

        TabelAscii tabel = new TabelAscii("Metode", "Paradigma", "Min", "Maks",
                "Perbandingan (aktual)", "Perbandingan (teori)", "Waktu rata-rata (ms)", "Validasi");
        tabel.tambah("MinMaks Brute Force", "Brute Force", bf.min, bf.maks, angka(bf.perbandingan),
                angka(teoriBf) + " (2n-2)", ms(totalBf / ulang), status);
        tabel.tambah("MinMaks Divide and Conquer", "Divide and Conquer", dc.min, dc.maks, angka(dc.perbandingan),
                angka(teoriDc) + " (3n/2-2)", ms(totalDc / ulang), status);

        System.out.println();
        tabel.cetak();
        long hemat = bf.perbandingan - dc.perbandingan;
        System.out.println("Divide and Conquer menghemat " + angka(hemat) + " perbandingan ("
                + String.format(ID, "%.2f", 100.0 * hemat / bf.perbandingan) + "%) dibanding Brute Force."
                + " Waktu rata-rata dari " + ulang + " ulangan.");
    }
}