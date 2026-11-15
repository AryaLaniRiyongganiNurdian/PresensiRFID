package com.absensi.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Hasil benchmark BenchmarkDAA:
 * - Data sudah terurut menurut kolom pencarian: Binary Search iteratif (waktu sama dengan
 *   versi rekursif, tetapi ruang tambahan O(1)).
 * - Data belum terurut: Sequential Search (Binary Search tidak boleh dipakai).
 */
public final class Searching {
    private Searching() {}

    /** Sequential Search (Brute Force): periksa elemen satu per satu, kumpulkan semua yang cocok. */
    public static <T> T[] sequential(T[] a, Predicate<T> cocok) {
        List<T> hasil = new ArrayList<>();
        for (int i = 0; i < a.length; i++) {
            if (cocok.test(a[i])) {
                hasil.add(a[i]);
            }
        }
        return hasil.toArray(Arrays.copyOf(a, 0));
    }

    /**
     * Binary Search iteratif. banding(x) bernilai negatif jika x berada sebelum target,
     * nol jika cocok, positif jika setelah target. Mengembalikan satu indeks yang cocok, atau -1.
     */
    public static <T> int binaryIteratif(T[] a, ToIntFunction<T> banding) {
        int kiri = 0;
        int kanan = a.length - 1;
        while (kiri <= kanan) {
            int tengah = (kiri + kanan) >>> 1;
            int c = banding.applyAsInt(a[tengah]);
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

    /** Semua elemen yang cocok. Pada array terurut, elemen yang cocok selalu berderet bersebelahan. */
    public static <T> T[] binary(T[] a, ToIntFunction<T> banding) {
        int tengah = binaryIteratif(a, banding);
        if (tengah < 0) {
            return Arrays.copyOf(a, 0);
        }
        int awal = tengah;
        while (awal > 0 && banding.applyAsInt(a[awal - 1]) == 0) {
            awal--;
        }
        int akhir = tengah;
        while (akhir < a.length - 1 && banding.applyAsInt(a[akhir + 1]) == 0) {
            akhir++;
        }
        return Arrays.copyOfRange(a, awal, akhir + 1);
    }
}