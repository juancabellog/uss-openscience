package cl.uss.ca.sync;

/**
 * record_uuid vs record_key persistence bounds. Not executed by the agent.
 */
public final class SyncAuditFieldsTest {
    private SyncAuditFieldsTest() {
    }

    public static void main(String[] args) {
        longLookupKeyDoesNotOverflowUuid();
        keyKeepsLookup();
        System.out.println("SyncAuditFieldsTest OK");
    }

    static void longLookupKeyDoesNotOverflowUuid() {
        String url = "https://tienda.thomsonreuters.cl/" + "desafios-actuales-del-derecho-privado-".repeat(8);
        assert url.length() > SyncAuditFields.RECORD_UUID_MAX;
        String uuid = SyncAuditFields.uuid("11111111-1111-1111-1111-111111111111");
        assert uuid.length() == 36;
        String clippedUuid = SyncAuditFields.uuid(url);
        assert clippedUuid.length() == SyncAuditFields.RECORD_UUID_MAX;
    }

    static void keyKeepsLookup() {
        String doi = "10.1101/350330";
        assert doi.equals(SyncAuditFields.key(doi));
        String url = "https://tienda.thomsonreuters.cl/desafios-actuales-del-derecho-privado";
        assert url.equals(SyncAuditFields.key(url));
        String huge = "https://example.com/" + "x".repeat(2000);
        assert SyncAuditFields.key(huge).length() == SyncAuditFields.RECORD_KEY_MAX;
    }
}
