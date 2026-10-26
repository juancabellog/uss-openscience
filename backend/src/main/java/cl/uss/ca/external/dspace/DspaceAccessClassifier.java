package cl.uss.ca.external.dspace;

import java.util.List;
import java.util.Locale;

public final class DspaceAccessClassifier {
    public static final String OPEN_ACCESS = "open.access";
    public static final String METADATA_ONLY = "metadata.only";
    public static final String RESTRICTED = "restricted";
    public static final String EMBARGO = "embargo";

    private DspaceAccessClassifier() {
    }

    public static String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean hasPublicOriginal(List<DspaceBitstreamRecord> bitstreams) {
        if (bitstreams == null) {
            return false;
        }
        for (DspaceBitstreamRecord bitstream : bitstreams) {
            if (bitstream == null) {
                continue;
            }
            if (!"ORIGINAL".equalsIgnoreCase(bitstream.bundleName)) {
                continue;
            }
            if (bitstream.publicDownload) {
                return true;
            }
            String access = normalizeStatus(bitstream.accessStatus);
            if (OPEN_ACCESS.equals(access) && bitstream.contentUrl != null) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasOriginalBundle(List<DspaceBitstreamRecord> bitstreams) {
        if (bitstreams == null) {
            return false;
        }
        for (DspaceBitstreamRecord bitstream : bitstreams) {
            if (bitstream != null && "ORIGINAL".equalsIgnoreCase(bitstream.bundleName)) {
                return true;
            }
        }
        return false;
    }

    public static DspaceFulltextAvailability classify(String accessStatus, List<DspaceBitstreamRecord> bitstreams) {
        String status = normalizeStatus(accessStatus);
        if (EMBARGO.equals(status)) {
            return DspaceFulltextAvailability.EMBARGOED;
        }
        if (RESTRICTED.equals(status)) {
            return DspaceFulltextAvailability.RESTRICTED;
        }
        if (METADATA_ONLY.equals(status)) {
            return DspaceFulltextAvailability.METADATA_ONLY;
        }
        if (OPEN_ACCESS.equals(status)) {
            if (hasPublicOriginal(bitstreams) || hasOriginalBundle(bitstreams)) {
                return hasPublicOriginal(bitstreams) || bitstreamOpen(bitstreams)
                        ? DspaceFulltextAvailability.FULLTEXT_PUBLIC
                        : DspaceFulltextAvailability.UNKNOWN;
            }
            return DspaceFulltextAvailability.METADATA_ONLY;
        }
        if (hasPublicOriginal(bitstreams)) {
            return DspaceFulltextAvailability.FULLTEXT_PUBLIC;
        }
        return DspaceFulltextAvailability.UNKNOWN;
    }

    private static boolean bitstreamOpen(List<DspaceBitstreamRecord> bitstreams) {
        if (bitstreams == null) {
            return false;
        }
        for (DspaceBitstreamRecord bitstream : bitstreams) {
            if (bitstream == null || !"ORIGINAL".equalsIgnoreCase(bitstream.bundleName)) {
                continue;
            }
            String access = normalizeStatus(bitstream.accessStatus);
            if (OPEN_ACCESS.equals(access) || bitstream.publicDownload) {
                return true;
            }
        }
        return false;
    }
}
