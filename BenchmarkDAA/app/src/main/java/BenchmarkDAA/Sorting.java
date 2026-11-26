public final class Sorting {
    private Sorting() {}

    // {nama, paradigma, kompleksitas, memori tambahan (teori)}
    public static final String[][] DAFTAR = {
        {"Bubble Sort",    "Brute Force",          "O(n^2)",     "O(1)"},
        {"Selection Sort", "Brute Force",          "O(n^2)",     "O(1)"},
        {"Insertion Sort", "Decrease and Conquer", "O(n^2)",     "O(1)"},
        {"Merge Sort",     "Divide and Conquer",   "O(n log n)", "O(n)"},
        {"Quick Sort",     "Divide and Conquer",   "O(n log n)", "O(log n)"}
    };

    // Tiga algoritma pertama berkompleksitas O(n^2)
    public static boolean kuadratik(int kode) {
        return kode <= 2;
    }

    public static void jalankan(int kode, String[] a) {
        switch (kode) {
            case 0: bubbleSort(a); break;
            case 1: selectionSort(a); break;
            case 2: insertionSort(a); break;
            case 3: mergeSort(a); break;
            default: quickSort(a); break;
        }
    }

    private static void tukar(String[] a, int i, int j) {
        String tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

    // ---------- Brute Force ----------
    public static void bubbleSort(String[] a) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (a[j].compareTo(a[j + 1]) > 0) {
                    tukar(a, j, j + 1);
                }
            }
        }
    }

    public static void selectionSort(String[] a) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            int min = i;
            for (int j = i + 1; j < n; j++) {
                if (a[j].compareTo(a[min]) < 0) {
                    min = j;
                }
            }
            tukar(a, i, min);
        }
    }

    // ---------- Decrease and Conquer ----------
    public static void insertionSort(String[] a) {
        for (int i = 1; i < a.length; i++) {
            String kunci = a[i];
            int j = i - 1;
            while (j >= 0 && a[j].compareTo(kunci) > 0) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = kunci;
        }
    }

    // ---------- Divide and Conquer ----------
    public static void mergeSort(String[] a) {
        String[] bantu = new String[a.length];
        mergeSort(a, bantu, 0, a.length - 1);
    }

    private static void mergeSort(String[] a, String[] bantu, int kiri, int kanan) {
        if (kiri >= kanan) {
            return;
        }
        int tengah = (kiri + kanan) >>> 1;
        mergeSort(a, bantu, kiri, tengah);
        mergeSort(a, bantu, tengah + 1, kanan);
        gabung(a, bantu, kiri, tengah, kanan);
    }

    private static void gabung(String[] a, String[] bantu, int kiri, int tengah, int kanan) {
        int i = kiri;
        int j = tengah + 1;
        int k = kiri;
        while (i <= tengah && j <= kanan) {
            if (a[i].compareTo(a[j]) <= 0) {
                bantu[k++] = a[i++];
            } else {
                bantu[k++] = a[j++];
            }
        }
        while (i <= tengah) {
            bantu[k++] = a[i++];
        }
        while (j <= kanan) {
            bantu[k++] = a[j++];
        }
        for (k = kiri; k <= kanan; k++) {
            a[k] = bantu[k];
        }
    }

    // Pivot elemen tengah (partisi Hoare). Bagian yang lebih kecil direkursi,
    // bagian yang lebih besar diproses dengan loop, sehingga kedalaman rekursi
    // paling banyak log2(n) dan tidak terjadi StackOverflowError pada 2 juta data.
    public static void quickSort(String[] a) {
        quickSort(a, 0, a.length - 1);
    }

    private static void quickSort(String[] a, int kiri, int kanan) {
        while (kiri < kanan) {
            String pivot = a[(kiri + kanan) >>> 1];
            int i = kiri;
            int j = kanan;
            while (i <= j) {
                while (a[i].compareTo(pivot) < 0) {
                    i++;
                }
                while (a[j].compareTo(pivot) > 0) {
                    j--;
                }
                if (i <= j) {
                    tukar(a, i, j);
                    i++;
                    j--;
                }
            }
            if (j - kiri < kanan - i) {
                quickSort(a, kiri, j);
                kiri = i;
            } else {
                quickSort(a, i, kanan);
                kanan = j;
            }
        }
    }
}