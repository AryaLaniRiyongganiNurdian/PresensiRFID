package com.PresensiRFID.util;

import java.util.Arrays;
import java.util.Comparator;

/**
 * DataMemori.Tabel.terurut memberi salinan array dan pembanding kolom pilihan pengguna.
 * Merge Sort membagi rentang menjadi dua, mengurutkan tiap bagian, lalu menggabungkannya.
 * Saat nilai sama, gabung mengambil elemen kiri dahulu sehingga urutan relatifnya tetap.
 */
public final class Sorting {
    private Sorting() {}

    public static <T> void mergeSort(T[] a, Comparator<? super T> cmp) {
        if (a.length < 2) {
            return;
        }
        T[] bantu = Arrays.copyOf(a, a.length);
        mergeSort(a, bantu, 0, a.length - 1, cmp);
    }

    private static <T> void mergeSort(T[] a, T[] bantu, int kiri, int kanan, Comparator<? super T> cmp) {
        if (kiri >= kanan) {
            return;
        }
        int tengah = (kiri + kanan) >>> 1;
        mergeSort(a, bantu, kiri, tengah, cmp);
        mergeSort(a, bantu, tengah + 1, kanan, cmp);
        gabung(a, bantu, kiri, tengah, kanan, cmp);
    }

    private static <T> void gabung(T[] a, T[] bantu, int kiri, int tengah, int kanan, Comparator<? super T> cmp) {
        System.arraycopy(a, kiri, bantu, kiri, kanan - kiri + 1);
        int i = kiri;
        int j = tengah + 1;
        for (int k = kiri; k <= kanan; k++) {
            if (i > tengah) {
                a[k] = bantu[j++];
            } else if (j > kanan) {
                a[k] = bantu[i++];
            } else if (cmp.compare(bantu[j], bantu[i]) < 0) {
                a[k] = bantu[j++];
            } else {
                a[k] = bantu[i++];
            }
        }
    }
}