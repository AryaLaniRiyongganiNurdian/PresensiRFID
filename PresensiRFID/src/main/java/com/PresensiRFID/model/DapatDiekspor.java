package com.PresensiRFID.model;

import java.util.StringJoiner;

/**
 * PBO Modul 8 — Interface untuk objek yang dapat menghasilkan satu baris CSV.
 * BerkasCsv.tulis cukup memanggil keBarisCsv(). MahasiswaAbsensi menentukan urutan dan isi kolom.
 */
public interface DapatDiekspor {
    String keBarisCsv();

    /** Kolom berkoma, berkutip, atau berganti baris dibungkus kutip. Kutip di dalamnya digandakan. */
    static String formatBaris(String... kolom) {
        StringJoiner baris = new StringJoiner(",");
        for (String nilai : kolom) {
            if (nilai == null) {
                baris.add("");
            } else if (nilai.indexOf(',') >= 0 || nilai.indexOf('"') >= 0
                    || nilai.indexOf('\r') >= 0 || nilai.indexOf('\n') >= 0) {
                baris.add("\"" + nilai.replace("\"", "\"\"") + "\"");
            } else {
                baris.add(nilai);
            }
        }
        return baris.toString();
    }
}
