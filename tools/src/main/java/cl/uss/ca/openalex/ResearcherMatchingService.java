package cl.uss.ca.openalex;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lógica de matching de nombres portada desde ResearcherMatchingService
 * del proyecto SISGIC. No depende de Spring ni de RRHH.
 */
public class ResearcherMatchingService {

    public boolean coinciden(String planilla, String candidatoBD) {
        List<String> q = tokens(planilla);
        List<String> c = tokens(candidatoBD);
        if (matchAllQueryTokens(q, c)) {
            return true;
        }
        return matchAllQueryTokens(c, q) && tieneSolapamientoFuerte(q, c);
    }

    public List<String> buscarCoincidencias(String planilla, List<String> listaBD) {
        List<String> out = new ArrayList<>();
        for (String cand : listaBD) {
            if (coinciden(planilla, cand)) {
                out.add(cand);
            }
        }
        return out;
    }

    public boolean esCasiIgual(String planilla, String item) {
        String[] token1 = planilla.split("[ ]+");
        ArrayList<String> tokens = new ArrayList<>(Arrays.asList(item.split("[ ]+")));
        for (String t1 : token1) {
            if (!tokens.contains(t1)) {
                return false;
            }
        }
        return true;
    }

    public OpenAlexAuthor buscarAutorCorrespondiente(String firstName, String lastName,
                                                     List<OpenAlexAuthor> authors) {
        if (authors == null || authors.isEmpty()) {
            return null;
        }
        String nombreBd = nombreCompleto(firstName, lastName);
        if (nombreBd.isEmpty()) {
            return null;
        }
        List<OpenAlexAuthor> matches = new ArrayList<>();
        for (OpenAlexAuthor author : authors) {
            if (author != null && coincideConPersona(nombreBd, author)) {
                matches.add(author);
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        return null;
    }

    public boolean coincideConPersona(String nombreBd, OpenAlexAuthor author) {
        for (String nombreOa : author.nombresCandidatos()) {
            if (nombreOa == null || nombreOa.isBlank()) {
                continue;
            }
            if (coinciden(nombreOa, nombreBd)) {
                return true;
            }
        }
        return false;
    }

    public static String nombreCompleto(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        return (first + " " + last).trim();
    }

    private boolean matchAllQueryTokens(List<String> queryTokens, List<String> candTokens) {
        if (queryTokens.isEmpty()) {
            return false;
        }
        boolean[] used = new boolean[candTokens.size()];
        for (String q : queryTokens) {
            int j = findMatchForQueryToken(q, candTokens, used);
            if (j == -1) {
                return false;
            }
            used[j] = true;
        }
        return true;
    }

    private int findMatchForQueryToken(String q, List<String> candTokens, boolean[] used) {
        boolean qEsInicial = q.length() == 1;
        char qIni = q.charAt(0);
        for (int i = 0; i < candTokens.size(); i++) {
            if (used[i]) {
                continue;
            }
            String c = candTokens.get(i);
            if (qEsInicial) {
                if (!c.isEmpty() && c.charAt(0) == qIni) {
                    return i;
                }
            } else if (c.length() == 1) {
                if (c.charAt(0) == qIni) {
                    return i;
                }
            } else if (c.equals(q)) {
                return i;
            }
        }
        return -1;
    }

    private boolean tieneSolapamientoFuerte(List<String> planilla, List<String> candidato) {
        Set<String> setP = new HashSet<>(planilla);
        boolean hayMatchFuerte = false;
        for (String tc : candidato) {
            if (tc.length() >= 2 && setP.contains(tc)) {
                hayMatchFuerte = true;
                break;
            }
        }
        if (!hayMatchFuerte) {
            return false;
        }
        if (planilla.size() >= 2) {
            Set<String> setApe = new HashSet<>(apellidosDe(planilla));
            for (String tc : candidato) {
                if (tc.length() >= 2 && setApe.contains(tc)) {
                    return true;
                }
            }
        }
        return true;
    }

    private List<String> apellidosDe(List<String> planillaTokens) {
        List<String> ap = new ArrayList<>();
        if (planillaTokens.isEmpty()) {
            return ap;
        }
        if (planillaTokens.size() == 1) {
            ap.add(planillaTokens.get(0));
        } else if (planillaTokens.size() == 2) {
            ap.add(planillaTokens.get(1));
        } else {
            ap.add(planillaTokens.get(planillaTokens.size() - 2));
            ap.add(planillaTokens.get(planillaTokens.size() - 1));
        }
        return ap;
    }

    protected List<String> tokens(String s) {
        return tokens(s, true);
    }

    protected List<String> tokens(String s, boolean normaliza) {
        String t = normaliza ? normalize(s) : s;
        if (t == null || t.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = t.split(" ");
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String t = s.toLowerCase(Locale.ROOT);
        t = t.replace('ı', 'i').replace('ȷ', 'j');
        t = t.replace('.', ' ').replace('-', ' ').replaceAll("[‐-‒–]", " ");
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        t = t.replaceAll("[\\p{Z}\\s]+", " ").trim();
        return t;
    }
}
