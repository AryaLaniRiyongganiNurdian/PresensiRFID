package com.absensi.util;

public final class KartuTerakhir {
    private static volatile String uid;
    private static volatile long waktu;

    private KartuTerakhir() {}

    public static void simpan(String u) {
        uid = u;
        waktu = System.currentTimeMillis();
    }

    public static String ambil() {
        return (uid != null && System.currentTimeMillis() - waktu < 60_000) ? uid : null;
    }

    public static void hapus() {
        uid = null;
    }
}