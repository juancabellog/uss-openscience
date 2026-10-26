package cl.uss.ca.domain;

import java.util.List;

/**
 * Gestionar DSpace access comparison. Not executed by the agent.
 */
public final class DspaceAccessComparisonTest {
    private DspaceAccessComparisonTest() {
    }

    public static void main(String[] args) {
        dspaceStatuses();
        pureOpenWinsOverClosed();
        unlinkedIsNotEvaluable();
        unknownIsNotEvaluable();
        metadataOnlyVsOpenIsDifference();
        restrictedVsClosedIsConsistent();
        embargoVsClosedIsConsistent();
        openVsOpenIsConsistent();
        publicDownloadGate();
        System.out.println("DspaceAccessComparisonTest OK");
    }

    static void dspaceStatuses() {
        assertEquals(DspaceAccessComparison.OPEN, DspaceAccessComparison.fromDspace("open.access"));
        assertEquals(DspaceAccessComparison.METADATA_ONLY, DspaceAccessComparison.fromDspace("metadata.only"));
        assertEquals(DspaceAccessComparison.RESTRICTED, DspaceAccessComparison.fromDspace("restricted"));
        assertEquals(DspaceAccessComparison.EMBARGOED, DspaceAccessComparison.fromDspace("embargo"));
        assertEquals(DspaceAccessComparison.UNKNOWN, DspaceAccessComparison.fromDspace(null));
    }

    static void pureOpenWinsOverClosed() {
        assertEquals(
                DspaceAccessComparison.OPEN,
                DspaceAccessComparison.fromPureValues(List.of("Closed Access", "Open Access"))
        );
        assertEquals(DspaceAccessComparison.CLOSED, DspaceAccessComparison.fromPure("embargoed"));
        assertEquals(DspaceAccessComparison.CLOSED, DspaceAccessComparison.fromPure("cerrado"));
        assertEquals(DspaceAccessComparison.OPEN, DspaceAccessComparison.fromPure("abierto"));
        assertEquals(DspaceAccessComparison.UNKNOWN, DspaceAccessComparison.fromPureValues(List.of()));
    }

    static void unlinkedIsNotEvaluable() {
        assertEquals(
                DspaceAccessComparison.NOT_EVALUABLE,
                DspaceAccessComparison.compare(DspaceAccessComparison.OPEN, DspaceAccessComparison.OPEN, false)
        );
    }

    static void unknownIsNotEvaluable() {
        assertEquals(
                DspaceAccessComparison.NOT_EVALUABLE,
                DspaceAccessComparison.compare(DspaceAccessComparison.OPEN, DspaceAccessComparison.UNKNOWN, true)
        );
        assertEquals(
                DspaceAccessComparison.NOT_EVALUABLE,
                DspaceAccessComparison.compare(DspaceAccessComparison.UNKNOWN, DspaceAccessComparison.CLOSED, true)
        );
    }

    static void metadataOnlyVsOpenIsDifference() {
        assertEquals(
                DspaceAccessComparison.DIFFERENCE,
                DspaceAccessComparison.compare(
                        DspaceAccessComparison.METADATA_ONLY, DspaceAccessComparison.OPEN, true)
        );
    }

    static void restrictedVsClosedIsConsistent() {
        assertEquals(
                DspaceAccessComparison.CONSISTENT,
                DspaceAccessComparison.compare(
                        DspaceAccessComparison.RESTRICTED, DspaceAccessComparison.CLOSED, true)
        );
    }

    static void embargoVsClosedIsConsistent() {
        assertEquals(
                DspaceAccessComparison.CONSISTENT,
                DspaceAccessComparison.compare(
                        DspaceAccessComparison.EMBARGOED, DspaceAccessComparison.CLOSED, true)
        );
    }

    static void openVsOpenIsConsistent() {
        assertEquals(
                DspaceAccessComparison.CONSISTENT,
                DspaceAccessComparison.compare(DspaceAccessComparison.OPEN, DspaceAccessComparison.OPEN, true)
        );
    }

    static void publicDownloadGate() {
        assert DspaceAccessComparison.isPublicDownload(true, "restricted");
        assert DspaceAccessComparison.isPublicDownload(false, "open.access");
        assert !DspaceAccessComparison.isPublicDownload(false, "metadata.only");
        assert !DspaceAccessComparison.isPublicDownload(false, "embargo");
    }

    private static void assertEquals(String expected, String actual) {
        assert expected.equals(actual) : expected + " != " + actual;
    }
}
