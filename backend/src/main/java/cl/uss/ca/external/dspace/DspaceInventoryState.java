package cl.uss.ca.external.dspace;

/**
 * ORIGINAL bitstream inventory for one item.
 * {@code CONFIRMED} (including confirmed empty) is the only state that may
 * replace {@code dspace_bitstream}. Embed presence is not confirmation.
 */
public enum DspaceInventoryState {
    NOT_LOADED,
    CONFIRMED,
    FETCH_FAILED;

    public boolean mayReplaceBitstreams() {
        return this == CONFIRMED;
    }
}
