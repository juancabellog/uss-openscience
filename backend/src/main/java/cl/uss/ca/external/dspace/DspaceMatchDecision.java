package cl.uss.ca.external.dspace;

public class DspaceMatchDecision {
    public String itemUuid;
    public long researchOutputId;
    public String method;
    public String status;
    public boolean canonical;
    public Double confidence;
    public String evidenceJson;

    public static DspaceMatchDecision of(
            String itemUuid,
            long researchOutputId,
            String method,
            String status,
            boolean canonical,
            Double confidence
    ) {
        DspaceMatchDecision decision = new DspaceMatchDecision();
        decision.itemUuid = itemUuid;
        decision.researchOutputId = researchOutputId;
        decision.method = method;
        decision.status = status;
        decision.canonical = canonical;
        decision.confidence = confidence;
        return decision;
    }
}
