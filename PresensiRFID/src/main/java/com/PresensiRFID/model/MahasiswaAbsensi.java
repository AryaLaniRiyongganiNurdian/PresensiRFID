package com.PresensiRFID.model;

/**
 * PresensiServlet membuat satu objek dari isian dosen. BerkasCsv membuat objek
 * yang sama dari kolom impor. Keduanya memakai getter untuk menyimpan data.
 * PBO Modul 8 — Kartu memberi akses ke UID, sedangkan DapatDiekspor
 * menyediakan record berisi NIM, nama, kelas, dan UID untuk penulis CSV.
 */
public class MahasiswaAbsensi extends Pengguna implements DapatDiekspor, Kartu {
    private final String kelas;
    private String uidRfid;

    // PBO Modul 1 dan 6 — Constructor, this() dan super()
    // Formulir dan impor memakai tiga parameter jika UID kosong. Jika ada UID,
    // constructor empat parameter memanggil versi tiga melalui this(), lalu
    // setter memeriksa UID. super() menyimpan nama dan NIM pada Pengguna.
    public MahasiswaAbsensi(String nama, String nim, String kelas) {
        super(nama, nim);
        this.kelas = kelas;
    }

    public MahasiswaAbsensi(String nama, String nim, String kelas, String uidRfid) {
        this(nama, nim, kelas);
        setUidRfid(uidRfid);
    }

    public String getKelas() {
        return kelas;
    }

    @Override
    public String getUidRfid() {
        return uidRfid;
    }

    // PBO Modul 6 — Validasi dalam setter
    // uidRfid bersifat private. Nilai baru harus berupa 8 sampai 20 karakter
    // heksadesimal. Setter menyimpannya dengan huruf kapital agar seragam.
    // Jika nilainya tidak cocok, atribut mempertahankan nilai sebelumnya.
    // Pada objek baru tanpa UID, sudahTerdaftar() akan menghasilkan false.
    public void setUidRfid(String uid) {
        if (uid != null && uid.matches("[0-9A-Fa-f]{8,20}")) {
            this.uidRfid = uid.toUpperCase();
        }
    }

    @Override
    public boolean sudahTerdaftar() {
        return uidRfid != null;
    }

    @Override
    public String getPeran() {
        return "Mahasiswa";
    }

    @Override
    public String ringkas() {
        return super.ringkas() + " kelas " + kelas;
    }

    @Override
    public String keBarisCsv() {
        return DapatDiekspor.formatBaris(getNomorIdentitas(), getNama(), getKelas(), getUidRfid());
    }

    /**
     * PBO Modul 2 dan 3 — for berindeks, Character, dan exception NIM
     * NIM harus memiliki 2 sampai 20 karakter. Character.isLetter memeriksa karakter
     * pertama dan for berindeks mulai dari karakter kedua. Misalnya, Z42 lolos, sedangkan
     * Z4X melempar NimTidakValidException saat loop mencapai X. Formulir dan impor
     * dapat memakai aturan yang sama dan melaporkan kegagalan dengan cara masing-masing.
     */
    public static void validasiNim(String nim) throws NimTidakValidException {
        if (nim == null || nim.length() < 2) {
            throw new NimTidakValidException("NIM terlalu pendek.");
        }
        if (nim.length() > 20) {
            throw new NimTidakValidException("NIM maksimal 20 karakter.");
        }
        if (!Character.isLetter(nim.charAt(0))) {
            throw new NimTidakValidException("NIM harus diawali huruf.");
        }
        for (int i = 1; i < nim.length(); i++) {
            if (!Character.isDigit(nim.charAt(i))) {
                throw new NimTidakValidException("NIM hanya boleh berisi angka setelah huruf pertama (karakter ke-" + (i + 1) + ").");
            }
        }
    }

    /**
     * PBO Modul 3 — Manipulasi teks dengan StringBuilder.replace
     * Catatan tap memakai salinan UID dengan sebagian karakter diganti bintang.
     * Misalnya, D0C0B0A0 menjadi D0C0****. UID pada objek tetap dapat dipakai untuk pencocokan.
     */
    public static String maskingUid(String uid) {
        if (uid == null || uid.isEmpty()) {
            return "-";
        }
        int tampil = Math.min(4, uid.length() / 2);
        StringBuilder bintang = new StringBuilder();
        for (int i = tampil; i < uid.length(); i++) {
            bintang.append('*');
        }
        StringBuilder sb = new StringBuilder(uid);
        sb.replace(tampil, sb.length(), bintang.toString());
        return sb.toString();
    }

    /** PBO Modul 3 — StringBuilder.insert menyisipkan titik dua per pasangan karakter saat UID ditampilkan. */
    public String uidTerformat() {
        if (uidRfid == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder(uidRfid);
        for (int i = 2; i < sb.length(); i += 3) {
            sb.insert(i, ':');
        }
        return sb.toString();
    }
}
