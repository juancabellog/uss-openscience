package cl.uss.ca.domain;

import java.util.List;

/**
 * Proposed cases for the local ORCID group_key.
 * Not executed by the agent.
 */
public final class OrcidGroupKeyTest {
    private OrcidGroupKeyTest() {
    }

    public static void main(String[] args) {
        sameIdentitySameKey();
        orderDoesNotMatter();
        emptyIdentityIsNull();
        putCodeFallbackIsStable();
    }

    static void sameIdentitySameKey() {
        OrcidGroupKey.Result a = OrcidGroupKey.fromIdentityIds(
                "0000-0001-7556-8352",
                List.of("doi:10.3390/smartcities6020050", "eid:2-s2.0-85153698761")
        );
        OrcidGroupKey.Result b = OrcidGroupKey.fromIdentityIds(
                "0000-0001-7556-8352",
                List.of("doi:10.3390/smartcities6020050", "eid:2-s2.0-85153698761")
        );
        assert a != null && a.key.equals(b.key) && OrcidGroupKey.KIND_EXTERNAL.equals(a.kind);
    }

    static void orderDoesNotMatter() {
        OrcidGroupKey.Result a = OrcidGroupKey.fromIdentityIds(
                "0000-0001-7556-8352",
                List.of("eid:2-s2.0-85153698761", "doi:10.3390/smartcities6020050")
        );
        OrcidGroupKey.Result b = OrcidGroupKey.fromIdentityIds(
                "0000-0001-7556-8352",
                List.of("doi:10.3390/smartcities6020050", "eid:2-s2.0-85153698761")
        );
        assert a.key.equals(b.key);
    }

    static void emptyIdentityIsNull() {
        assert OrcidGroupKey.fromIdentityIds("0000-0001-7556-8352", List.of()) == null;
    }

    static void putCodeFallbackIsStable() {
        OrcidGroupKey.Result a = OrcidGroupKey.fromPutCodes("0000-0001-7556-8352", List.of(3L, 1L, 2L));
        OrcidGroupKey.Result b = OrcidGroupKey.fromPutCodes("0000-0001-7556-8352", List.of(1L, 2L, 3L));
        assert a.key.equals(b.key) && OrcidGroupKey.KIND_PUT_CODES.equals(a.kind);
    }
}
