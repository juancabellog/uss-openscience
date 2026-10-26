package cl.uss.ca.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Classifies an ORCID Employment as USS canonical, USS variant, or other.
 * <p>
 * Canonical audited string (trim + NFC only): {@code Universidad San Sebastián}.
 * Identity evidence prefers the ROR of Universidad San Sebastián (Chile).
 * Name fallback is a small explicit alias set plus a campus/faculty suffix on
 * that Spanish name. It does not use {@code contains("San Sebastián")} and
 * does not treat English labels as USS without an identifier.
 */
public final class UssOrganizationIdentity {
    public static final String NORMALIZED_NAME = "Universidad San Sebastián";

    /**
     * Universidad San Sebastián (Chile) ROR. Used only when ORCID Employment
     * includes a disambiguated organization identifier.
     */
    private static final Set<String> USS_ROR_IDS = Set.of(
            "047gc3g35",
            "https://ror.org/047gc3g35",
            "ror.org/047gc3g35"
    );

    /**
     * Semantic (accent-stripped, lowercased) aliases confirmed as USS.
     * English forms are not listed: without ROR they are not assumed.
     */
    private static final Set<String> USS_NAME_ALIASES = Set.of(
            "universidad san sebastian",
            "u san sebastian",
            "univ san sebastian"
    );

    private static final String USS_SPANISH_PREFIX = "universidad san sebastian";

    public enum Kind {
        NORMALIZED,
        USS_VARIANT,
        OTHER
    }

    private UssOrganizationIdentity() {
    }

    public static String technicalNormalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    }

    public static boolean isExactNormalizedName(String organizationName) {
        return NORMALIZED_NAME.equals(technicalNormalize(organizationName));
    }

    public static boolean isUssIdentifier(String identifier, String identifierType) {
        String id = compactIdentifier(identifier);
        return USS_ROR_IDS.contains(id);
    }

    public static boolean isUssTextEvidence(String organizationName) {
        String normalized = semanticNormalize(organizationName);
        if (normalized.isEmpty()) {
            return false;
        }
        if (USS_NAME_ALIASES.contains(normalized)) {
            return true;
        }
        return normalized.startsWith(USS_SPANISH_PREFIX + " ");
    }

    public static Kind classifyEmployment(String organizationName, String identifier, String identifierType) {
        if (isExactNormalizedName(organizationName)) {
            return Kind.NORMALIZED;
        }
        if (isUssIdentifier(identifier, identifierType) || isUssTextEvidence(organizationName)) {
            return Kind.USS_VARIANT;
        }
        return Kind.OTHER;
    }

    /**
     * Accent-insensitive compact form for alias comparison only.
     * Does not drop campus/faculty words.
     */
    public static String semanticNormalize(String organizationName) {
        String t = AuthorNameMatcher.normalize(organizationName);
        if (t.isEmpty()) {
            return "";
        }
        t = t.replaceAll("[^a-z0-9\\s]", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static String compactIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }
        String value = identifier.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("http://")) {
            value = value.substring("http://".length());
        } else if (value.startsWith("https://")) {
            value = value.substring("https://".length());
        }
        if (value.startsWith("www.")) {
            value = value.substring(4);
        }
        return value;
    }
}
