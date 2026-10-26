package cl.uss.ca.events;

import java.util.ArrayList;
import java.util.List;

/**
 * Medición y corte de líneas para Helvetica / Helvetica-Bold (WinAnsi).
 * Los anchos siguen las tablas AFM de Adobe en milésimas de em.
 */
public final class PdfType1Metrics {
    private static final int[] HELVETICA = new int[256];
    private static final int[] HELVETICA_BOLD = new int[256];

    static {
        fillHelvetica(HELVETICA);
        fillHelveticaBold(HELVETICA_BOLD);
    }

    private PdfType1Metrics() {
    }

    public static float width(String text, float fontSize, boolean bold) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        int[] table = bold ? HELVETICA_BOLD : HELVETICA;
        float sum = 0f;
        for (int i = 0; i < text.length(); i++) {
            sum += table[winAnsi(text.charAt(i))];
        }
        return sum * fontSize / 1000f;
    }

    public static List<String> wrap(String text, float maxWidth, float fontSize, boolean bold) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || maxWidth <= 0f) {
            return lines;
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').replaceAll(" +", " ").trim();
        if (normalized.isEmpty()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : normalized.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (width(word, fontSize, bold) > maxWidth) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                lines.addAll(breakToken(word, maxWidth, fontSize, bold));
                continue;
            }
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (width(candidate, fontSize, bold) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    public static String encode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            int code = winAnsi(text.charAt(i));
            char ch = (char) code;
            if (ch == '(' || ch == ')' || ch == '\\') {
                out.append('\\').append(ch);
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    static int winAnsi(char ch) {
        if (ch <= 255) {
            return ch;
        }
        return switch (ch) {
            case '\u2018', '\u2019', '\u00B4' -> '\'';
            case '\u201C', '\u201D' -> '"';
            case '\u2013' -> 150;
            case '\u2014' -> 151;
            case '\u2026' -> 133;
            case '\u20AC' -> 128;
            default -> '?';
        };
    }

    private static List<String> breakToken(String token, float maxWidth, float fontSize, boolean bold) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            current.append(token.charAt(i));
            if (width(current.toString(), fontSize, bold) > maxWidth && current.length() > 1) {
                current.setLength(current.length() - 1);
                parts.add(current.toString());
                current.setLength(0);
                current.append(token.charAt(i));
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static void fillHelvetica(int[] w) {
        java.util.Arrays.fill(w, 600);
        int[] ascii = {
                278, 278, 355, 556, 556, 889, 667, 222, 333, 333, 389, 584, 278, 333, 278, 278,
                556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
                1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
                667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
                222, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
                556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584
        };
        System.arraycopy(ascii, 0, w, 32, ascii.length);
        w[128] = 556;
        w[130] = 222;
        w[131] = 556;
        w[132] = 333;
        w[133] = 1000;
        w[134] = 556;
        w[135] = 556;
        w[136] = 333;
        w[137] = 1000;
        w[138] = 667;
        w[139] = 333;
        w[140] = 1000;
        w[142] = 611;
        w[145] = 222;
        w[146] = 222;
        w[147] = 333;
        w[148] = 333;
        w[149] = 350;
        w[150] = 556;
        w[151] = 1000;
        w[152] = 333;
        w[153] = 1000;
        w[154] = 500;
        w[155] = 333;
        w[156] = 722;
        w[158] = 500;
        w[159] = 667;
        int[] latin = {
                278, 333, 556, 556, 556, 556, 260, 556, 333, 737, 370, 556, 584, 333, 737, 333,
                400, 584, 333, 333, 333, 556, 537, 278, 333, 333, 365, 556, 834, 834, 834, 611
        };
        System.arraycopy(latin, 0, w, 160, latin.length);
        copyAccentedLetters(w, false);
    }

    private static void fillHelveticaBold(int[] w) {
        java.util.Arrays.fill(w, 600);
        int[] ascii = {
                278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
                556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
                975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
                667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
                278, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
                611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584
        };
        System.arraycopy(ascii, 0, w, 32, ascii.length);
        w[128] = 556;
        w[130] = 238;
        w[131] = 556;
        w[132] = 500;
        w[133] = 1000;
        w[134] = 556;
        w[135] = 556;
        w[136] = 333;
        w[137] = 1000;
        w[138] = 667;
        w[139] = 333;
        w[140] = 1000;
        w[142] = 611;
        w[145] = 278;
        w[146] = 278;
        w[147] = 500;
        w[148] = 500;
        w[149] = 350;
        w[150] = 556;
        w[151] = 1000;
        w[152] = 333;
        w[153] = 1000;
        w[154] = 556;
        w[155] = 333;
        w[156] = 944;
        w[158] = 500;
        w[159] = 667;
        int[] latin = {
                278, 333, 556, 556, 556, 556, 280, 556, 333, 737, 370, 556, 584, 333, 737, 333,
                400, 584, 333, 333, 333, 611, 556, 278, 333, 333, 365, 556, 834, 834, 834, 611
        };
        System.arraycopy(latin, 0, w, 160, latin.length);
        copyAccentedLetters(w, true);
    }

    private static void copyAccentedLetters(int[] w, boolean bold) {
        int a = w['A'];
        int c = w['C'];
        int e = w['E'];
        int i = w['I'];
        int n = w['N'];
        int o = w['O'];
        int u = w['U'];
        int y = w['Y'];
        int la = w['a'];
        int lc = w['c'];
        int le = w['e'];
        int li = w['i'];
        int ln = w['n'];
        int lo = w['o'];
        int lu = w['u'];
        int ly = w['y'];
        for (int code = 192; code <= 197; code++) {
            w[code] = a;
        }
        w[198] = bold ? 1000 : 1000;
        w[199] = c;
        for (int code = 200; code <= 203; code++) {
            w[code] = e;
        }
        for (int code = 204; code <= 207; code++) {
            w[code] = i;
        }
        w[208] = w['D'];
        w[209] = n;
        for (int code = 210; code <= 214; code++) {
            w[code] = o;
        }
        w[215] = 584;
        w[216] = o;
        for (int code = 217; code <= 220; code++) {
            w[code] = u;
        }
        w[221] = y;
        w[222] = bold ? 667 : 667;
        w[223] = bold ? 611 : 611;
        for (int code = 224; code <= 229; code++) {
            w[code] = la;
        }
        w[230] = bold ? 889 : 889;
        w[231] = lc;
        for (int code = 232; code <= 235; code++) {
            w[code] = le;
        }
        for (int code = 236; code <= 239; code++) {
            w[code] = li;
        }
        w[240] = lo;
        w[241] = ln;
        for (int code = 242; code <= 246; code++) {
            w[code] = lo;
        }
        w[247] = 584;
        w[248] = bold ? 611 : 611;
        for (int code = 249; code <= 252; code++) {
            w[code] = lu;
        }
        w[253] = ly;
        w[254] = lo;
        w[255] = ly;
    }
}
