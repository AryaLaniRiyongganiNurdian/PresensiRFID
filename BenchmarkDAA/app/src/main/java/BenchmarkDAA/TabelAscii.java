import java.util.ArrayList;
import java.util.List;

public final class TabelAscii {
    private final String[] judul;
    private final List<String[]> baris = new ArrayList<>();

    public TabelAscii(String... judul) {
        this.judul = judul;
    }

    public void tambah(String... sel) {
        baris.add(sel);
    }

    public void cetak() {
        int[] lebar = new int[judul.length];
        for (int i = 0; i < judul.length; i++) {
            lebar[i] = judul[i].length();
        }
        for (String[] b : baris) {
            for (int i = 0; i < b.length; i++) {
                lebar[i] = Math.max(lebar[i], b[i].length());
            }
        }

        String garis = garis(lebar);
        System.out.println(garis);
        cetakBaris(judul, lebar);
        System.out.println(garis);
        for (String[] b : baris) {
            cetakBaris(b, lebar);
        }
        System.out.println(garis);
    }

    private static String garis(int[] lebar) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : lebar) {
            sb.append("-".repeat(w + 2)).append('+');
        }
        return sb.toString();
    }

    private static void cetakBaris(String[] sel, int[] lebar) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < lebar.length; i++) {
            String isi = i < sel.length ? sel[i] : "";
            sb.append(' ').append(isi);
            sb.append(" ".repeat(lebar[i] - isi.length())).append(" |");
        }
        System.out.println(sb);
    }
}