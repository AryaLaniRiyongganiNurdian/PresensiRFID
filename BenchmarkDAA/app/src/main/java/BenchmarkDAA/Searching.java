public final class Searching {
    private Searching() {}

    // ---------- Brute Force ----------
    public static int sequential(String[] a, String target) {
        for (int i = 0; i < a.length; i++) {
            if (a[i].equals(target)) {
                return i;
            }
        }
        return -1;
    }

    // ---------- Decrease and Conquer (array harus sudah terurut) ----------
    public static int binaryIteratif(String[] a, String target) {
        int kiri = 0;
        int kanan = a.length - 1;
        while (kiri <= kanan) {
            int tengah = (kiri + kanan) >>> 1;
            int c = a[tengah].compareTo(target);
            if (c == 0) {
                return tengah;
            } else if (c < 0) {
                kiri = tengah + 1;
            } else {
                kanan = tengah - 1;
            }
        }
        return -1;
    }

    public static int binaryRekursif(String[] a, String target) {
        return binaryRekursif(a, target, 0, a.length - 1);
    }

    private static int binaryRekursif(String[] a, String target, int kiri, int kanan) {
        if (kiri > kanan) {
            return -1;
        }
        int tengah = (kiri + kanan) >>> 1;
        int c = a[tengah].compareTo(target);
        if (c == 0) {
            return tengah;
        } else if (c < 0) {
            return binaryRekursif(a, target, tengah + 1, kanan);
        } else {
            return binaryRekursif(a, target, kiri, tengah - 1);
        }
    }

    // ---------- MinMaks ----------
    public static final class HasilMinMaks {
        public String min;
        public String maks;
        public long perbandingan;
    }

    // Brute Force: dua perbandingan untuk setiap elemen kedua dan seterusnya, total 2n - 2
    public static HasilMinMaks minMaksBruteForce(String[] a) {
        HasilMinMaks h = new HasilMinMaks();
        h.min = a[0];
        h.maks = a[0];
        for (int i = 1; i < a.length; i++) {
            h.perbandingan++;
            if (a[i].compareTo(h.min) < 0) {
                h.min = a[i];
            }
            h.perbandingan++;
            if (a[i].compareTo(h.maks) > 0) {
                h.maks = a[i];
            }
        }
        return h;
    }

    // Divide and Conquer: total ceil(3n/2) - 2 perbandingan
    public static HasilMinMaks minMaksDivideConquer(String[] a) {
        long[] hitung = new long[1];
        int[] idx = dnc(a, 0, a.length - 1, hitung);
        HasilMinMaks h = new HasilMinMaks();
        h.min = a[idx[0]];
        h.maks = a[idx[1]];
        h.perbandingan = hitung[0];
        return h;
    }

    // Mengembalikan {indeks min, indeks maks} pada rentang kiri..kanan
    private static int[] dnc(String[] a, int kiri, int kanan, long[] hitung) {
        if (kiri == kanan) {
            return new int[] {kiri, kiri};
        }
        if (kanan == kiri + 1) {
            hitung[0]++;
            if (a[kiri].compareTo(a[kanan]) <= 0) {
                return new int[] {kiri, kanan};
            }
            return new int[] {kanan, kiri};
        }
        // Sisi kiri berukuran pangkat dua terbesar yang kurang dari n, sehingga
        // jumlah perbandingan tepat ceil(3n/2) - 2 untuk setiap n.
        int n = kanan - kiri + 1;
        int tengah = kiri + Integer.highestOneBit(n - 1) - 1;
        int[] kr = dnc(a, kiri, tengah, hitung);
        int[] kn = dnc(a, tengah + 1, kanan, hitung);

        hitung[0]++;
        int idxMin = (a[kr[0]].compareTo(a[kn[0]]) <= 0) ? kr[0] : kn[0];
        hitung[0]++;
        int idxMaks = (a[kr[1]].compareTo(a[kn[1]]) >= 0) ? kr[1] : kn[1];
        return new int[] {idxMin, idxMaks};
    }
}