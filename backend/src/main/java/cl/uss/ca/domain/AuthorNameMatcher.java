package cl.uss.ca.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Port of {@code tools} {@code ResearcherMatchingService#coinciden}.
 * J. Abarca ≡ Jaime Alberto Abarca Reveco; J.E. Abarca does not; J.R. Abarca does.
 */
public final class AuthorNameMatcher {

    public boolean coinciden(String left, String right) {
        List<String> query = tokens(left);
        List<String> candidate = tokens(right);
        if (matchAllQueryTokens(query, candidate)) {
            return true;
        }
        return matchAllQueryTokens(candidate, query) && tieneSolapamientoFuerte(query, candidate);
    }

    public static String firstToken(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split("[\\s\\-]+");
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                return part.trim();
            }
        }
        return null;
    }

    public static String displayName(String firstName, String lastName) {
        String last = lastName == null ? "" : lastName.trim();
        String first = firstName == null ? "" : firstName.trim();
        if (!last.isEmpty() && !first.isEmpty()) {
            return last + ", " + first;
        }
        return (last + " " + first).trim();
    }

    private boolean matchAllQueryTokens(List<String> queryTokens, List<String> candTokens) {
        if (queryTokens.isEmpty()) {
            return false;
        }
        boolean[] used = new boolean[candTokens.size()];
        for (String query : queryTokens) {
            int index = findMatchForQueryToken(query, candTokens, used);
            if (index == -1) {
                return false;
            }
            used[index] = true;
        }
        return true;
    }

    private int findMatchForQueryToken(String query, List<String> candTokens, boolean[] used) {
        boolean initial = query.length() == 1;
        char first = query.charAt(0);
        for (int i = 0; i < candTokens.size(); i++) {
            if (used[i]) {
                continue;
            }
            String candidate = candTokens.get(i);
            if (initial) {
                if (!candidate.isEmpty() && candidate.charAt(0) == first) {
                    return i;
                }
            } else if (candidate.length() == 1) {
                if (candidate.charAt(0) == first) {
                    return i;
                }
            } else if (candidate.equals(query)) {
                return i;
            }
        }
        return -1;
    }

    private boolean tieneSolapamientoFuerte(List<String> planilla, List<String> candidato) {
        Set<String> setP = new HashSet<>(planilla);
        boolean fuerte = false;
        for (String token : candidato) {
            if (token.length() >= 2 && setP.contains(token)) {
                fuerte = true;
                break;
            }
        }
        if (!fuerte) {
            return false;
        }
        if (planilla.size() >= 2) {
            Set<String> apellidos = new HashSet<>(apellidosDe(planilla));
            for (String token : candidato) {
                if (token.length() >= 2 && apellidos.contains(token)) {
                    return true;
                }
            }
        }
        return true;
    }

    private List<String> apellidosDe(List<String> tokens) {
        if (tokens.isEmpty()) {
            return List.of();
        }
        if (tokens.size() == 1) {
            return List.of(tokens.get(0));
        }
        if (tokens.size() == 2) {
            return List.of(tokens.get(1));
        }
        return List.of(tokens.get(tokens.size() - 2), tokens.get(tokens.size() - 1));
    }

    private List<String> tokens(String value) {
        String normalized = normalize(value).replace(',', ' ').replace(';', ' ');
        normalized = normalized.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(normalized.split(" "))).stream()
                .filter(part -> !part.isEmpty())
                .toList();
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String text = value.toLowerCase(Locale.ROOT);
        text = text.replace('ı', 'i').replace('ȷ', 'j');
        text = text.replace('.', ' ').replace('-', ' ').replaceAll("[‐-‒–]", " ");
        text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return text.replaceAll("[\\p{Z}\\s]+", " ").trim();
    }

    /**
     * Canonical given-family full name: {@code first + " " + last}.
     * Distinct from {@link #displayName}, which formats directory names as
     * {@code last, first}. Use this before comparing person vs contributor
     * partitions; first_name/last_name boundaries are not reliable in PURE.
     */
    public static String fullName(String firstName, String lastName) {
        return signatureDisplay(firstName, lastName);
    }

    /**
     * Visible publication signature. Distinct from {@link #displayName}, which
     * formats directory names as {@code last, first}.
     */
    public static String signatureDisplay(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        return (first + " " + last).trim();
    }

    /**
     * True when two display strings are the same after {@link #normalize}.
     * Does not use {@link #coinciden}: exact normalized equality only.
     */
    public static boolean sameNormalizedFullName(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        return !a.isEmpty() && a.equals(b);
    }

    /**
     * Substantive signature key: {@link #normalize} of {@link #signatureDisplay}.
     * Does not use {@link #coinciden}: that matcher treats initials as the same
     * person and would collapse distinct publication forms.
     */
    public static String signatureKey(String firstName, String lastName) {
        return normalize(signatureDisplay(firstName, lastName));
    }
}
