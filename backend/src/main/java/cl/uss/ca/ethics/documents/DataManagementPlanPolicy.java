package cl.uss.ca.ethics.documents;

import java.util.Comparator;
import java.util.List;

/**
 * El Plan de Gestión de Datos se identifica solo por {@link #TYPE}.
 * El nombre del archivo se conserva como dato de la versión y no clasifica el documento.
 */
public final class DataManagementPlanPolicy {
    public static final String TYPE = "DATA_MANAGEMENT_PLAN";

    public enum Channel {
        DATA_MANAGEMENT_PLAN,
        OTHER
    }

    public record Version(
            String id,
            String logicalId,
            String documentType,
            String fileName,
            int version,
            String uploadedBy,
            String uploadedAt
    ) {
    }

    private DataManagementPlanPolicy() {
    }

    public static boolean isPlan(String documentType) {
        return TYPE.equals(documentType);
    }

    public static Version current(List<Version> rows) {
        return rows.stream()
                .filter(row -> isPlan(row.documentType()))
                .max(Comparator.comparingInt(Version::version)
                        .thenComparing(Version::uploadedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    public static List<Version> history(List<Version> rows) {
        Version current = current(rows);
        if (current == null) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> isPlan(row.documentType()) && row != current && !row.id().equals(current.id()))
                .sorted(Comparator.comparingInt(Version::version).reversed())
                .toList();
    }

    /**
     * Una solicitud tiene un solo documento lógico de este tipo.
     * Otra carga desde el canal del plan agrega una versión y conserva las anteriores.
     */
    public static Version attach(List<Version> existing, Channel channel, String fileName, String uploadedBy, String uploadedAt) {
        if (channel != Channel.DATA_MANAGEMENT_PLAN) {
            return new Version("other-" + (existing.size() + 1), "other-" + (existing.size() + 1), null, fileName, 1, uploadedBy, uploadedAt);
        }
        Version current = current(existing);
        int version = current == null ? 1 : current.version() + 1;
        String logicalId = TYPE;
        return new Version(logicalId + "-v" + version, logicalId, TYPE, fileName, version, uploadedBy, uploadedAt);
    }
}
