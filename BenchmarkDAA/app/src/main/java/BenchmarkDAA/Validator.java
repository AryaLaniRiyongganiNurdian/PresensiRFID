public final class Validator {
    private Validator() {}

    public static boolean isSorted(String[] a) {
        for (int i = 1; i < a.length; i++) {
            if (a[i - 1].compareTo(a[i]) > 0) {
                return false;
            }
        }
        return true;
    }

    public static String labelSorted(String[] a) {
        return isSorted(a) ? "VALID" : "INVALID";
    }

    // MATCH jika: semua algoritma sepakat ditemukan / tidak ditemukan,
    // Binary Iteratif dan Rekursif memberi indeks yang sama persis,
    // dan setiap elemen yang ditemukan memang sama dengan target.
    public static String cocok(String[] asli, String[] urut, String target,
                               int idxSeq, int idxIter, int idxRek) {
        boolean ada = idxSeq >= 0;
        if ((idxIter >= 0) != ada || (idxRek >= 0) != ada) {
            return "MISMATCH";
        }
        if (idxIter != idxRek) {
            return "MISMATCH";
        }
        if (ada && (!asli[idxSeq].equals(target) || !urut[idxIter].equals(target))) {
            return "MISMATCH";
        }
        return "MATCH";
    }
}