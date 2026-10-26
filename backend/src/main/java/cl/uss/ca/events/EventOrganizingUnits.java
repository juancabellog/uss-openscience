package cl.uss.ca.events;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class EventOrganizingUnits {
    public static final String OTHER = "OTHER";

    private static final List<Unit> SEED = List.of(
            new Unit("VICERRECTORIA", "Vicerrectoría", "Vice-Rectory", true, 1),
            new Unit("INVESTIGACION_DESARROLLO", "Investigación y Desarrollo", "Research and Development", true, 2),
            new Unit("DOCTORADOS", "Doctorados", "Doctoral programs", true, 3),
            new Unit("DISEI", "DISEI", "DISEI", true, 4),
            new Unit("PROPIEDAD_INTELECTUAL", "Propiedad Intelectual", "Intellectual Property", true, 5),
            new Unit("CIENCIA_ABIERTA", "Ciencia Abierta", "Open Science", true, 6),
            new Unit(OTHER, "Otro (especificar)", "Other (specify)", true, 99)
    );

    private static final Map<String, Unit> BY_CODE = SEED.stream()
            .collect(Collectors.toUnmodifiableMap(unit -> unit.code, unit -> unit));

    private EventOrganizingUnits() {
    }

    public static List<Unit> seed() {
        return SEED;
    }

    public static Unit find(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return BY_CODE.get(code.trim().toUpperCase(Locale.ROOT));
    }

    public static Unit matchExact(String raw) {
        String needle = normalize(raw);
        if (needle.isEmpty()) {
            return null;
        }
        for (Unit unit : SEED) {
            if (unit.isOther()) {
                continue;
            }
            if (needle.equals(normalize(unit.nameEs)) || needle.equals(normalize(unit.nameEn))) {
                return unit;
            }
        }
        return null;
    }

    public static Resolved resolve(String codeRaw, String textRaw) {
        String code = EventRules.trimToNull(codeRaw);
        String text = EventRules.trimToNull(textRaw);
        if (code != null) {
            Unit unit = find(code);
            if (unit == null || !unit.active) {
                return Resolved.invalid();
            }
            if (unit.isOther()) {
                return Resolved.other(text);
            }
            return Resolved.official(unit);
        }
        if (text == null) {
            return Resolved.empty();
        }
        Unit matched = matchExact(text);
        if (matched != null) {
            return Resolved.official(matched);
        }
        return Resolved.other(text);
    }

    public static String effectiveCode(String storedCode, String storedText) {
        if (EventRules.trimToNull(storedCode) != null) {
            return storedCode.trim().toUpperCase(Locale.ROOT);
        }
        Unit matched = matchExact(storedText);
        if (matched != null) {
            return matched.code;
        }
        return EventRules.trimToNull(storedText) == null ? null : OTHER;
    }

    public static String display(String storedCode, String storedText, String language) {
        String code = effectiveCode(storedCode, storedText);
        Unit unit = find(code);
        if (unit != null && !unit.isOther()) {
            return unit.label(language);
        }
        return EventRules.trimToNull(storedText);
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public static final class Unit {
        public final String code;
        public final String nameEs;
        public final String nameEn;
        public final boolean active;
        public final int sortOrder;

        public Unit(String code, String nameEs, String nameEn, boolean active, int sortOrder) {
            this.code = code;
            this.nameEs = nameEs;
            this.nameEn = nameEn;
            this.active = active;
            this.sortOrder = sortOrder;
        }

        public boolean isOther() {
            return OTHER.equals(code);
        }

        public String label(String language) {
            return EventLanguage.isEnglish(language) ? nameEn : nameEs;
        }
    }

    public static final class Resolved {
        public final String code;
        public final String storedText;
        public final boolean invalid;

        private Resolved(String code, String storedText, boolean invalid) {
            this.code = code;
            this.storedText = storedText;
            this.invalid = invalid;
        }

        public static Resolved official(Unit unit) {
            return new Resolved(unit.code, unit.nameEs, false);
        }

        public static Resolved other(String text) {
            return new Resolved(OTHER, text, false);
        }

        public static Resolved empty() {
            return new Resolved(null, null, false);
        }

        public static Resolved invalid() {
            return new Resolved(null, null, true);
        }

        public boolean isEmpty() {
            return !invalid && code == null;
        }

        public boolean other() {
            return OTHER.equals(code);
        }
    }
}
