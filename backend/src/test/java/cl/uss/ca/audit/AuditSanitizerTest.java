package cl.uss.ca.audit;

/**
 * Output sanitization for audit payloads. Not executed by the agent.
 */
public final class AuditSanitizerTest {
    private AuditSanitizerTest() {
    }

    public static void main(String[] args) {
        redactsApiKeyInQuery();
        redactsTokensAndPassword();
        redactsBearerAndJsonSecrets();
        leavesHarmlessText();
        System.out.println("AuditSanitizerTest OK");
    }

    static void redactsApiKeyInQuery() {
        String raw = "https://api.elsevier.com/content/search?apiKey=secret-live-key&query=uss";
        String clean = AuditSanitizer.redact(raw);
        assert clean.contains("[REDACTED]");
        assert !clean.contains("secret-live-key");
        assert clean.contains("query=uss");
    }

    static void redactsTokensAndPassword() {
        String raw = "Authorization=Bearer abc.def password=SuperSecret token=xyz access_token=aaa refresh_token=bbb client_secret=ccc";
        String clean = AuditSanitizer.redact(raw);
        assert !clean.contains("SuperSecret");
        assert !clean.contains("abc.def");
        assert !clean.contains("xyz");
        assert !clean.contains("aaa");
        assert !clean.contains("bbb");
        assert !clean.contains("ccc");
        assert clean.contains("[REDACTED]");
    }

    static void redactsBearerAndJsonSecrets() {
        String raw = "{\"api_key\":\"k-123\",\"password\":\"p@ss\",\"note\":\"ok\"} Bearer eyJhbGciOi";
        String clean = AuditSanitizer.redact(raw);
        assert !clean.contains("k-123");
        assert !clean.contains("p@ss");
        assert clean.contains("\"note\":\"ok\"");
        assert !clean.contains("eyJhbGciOi");
    }

    static void leavesHarmlessText() {
        String raw = "DOI 10.1234/abc record_key=10.1234/abc stage=MATCH";
        String clean = AuditSanitizer.redact(raw);
        assert raw.equals(clean);
    }
}
