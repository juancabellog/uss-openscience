package cl.uss.ca.external.pure;

import cl.uss.ca.external.pure.json.ClassificationRef;
import cl.uss.ca.external.pure.json.LocalizedText;

public final class LocalizedTexts {
    private LocalizedTexts() {
    }

    public static String obtenerTextoPreferido(LocalizedText texto) {
        if (texto == null) {
            return null;
        }
        String preferred = texto.preferred();
        if (preferred != null) {
            return preferred;
        }
        String esCl = trimToNull(texto.getEsCl());
        if (esCl != null) {
            return esCl;
        }
        String enUs = trimToNull(texto.getEnUs());
        return enUs != null ? enUs : trimToNull(texto.getEnGb());
    }

    public static String obtenerTextoPreferido(ClassificationRef clasificacion) {
        return clasificacion == null ? null : obtenerTextoPreferido(clasificacion.getTerm());
    }

    public static String es(LocalizedText texto) {
        return texto == null ? null : trimToNull(texto.getEsCl());
    }

    public static String en(LocalizedText texto) {
        return texto == null ? null : trimToNull(texto.getEnUs());
    }

    public static String uri(ClassificationRef clasificacion) {
        return clasificacion == null ? null : trimToNull(clasificacion.getUri());
    }

    public static String es(ClassificationRef clasificacion) {
        return clasificacion == null ? null : es(clasificacion.getTerm());
    }

    public static String en(ClassificationRef clasificacion) {
        return clasificacion == null ? null : en(clasificacion.getTerm());
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
