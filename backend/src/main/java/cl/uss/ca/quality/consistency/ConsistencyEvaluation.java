package cl.uss.ca.quality.consistency;

import java.util.ArrayList;
import java.util.List;

public class ConsistencyEvaluation {
    public ConsistencyField field;
    public String pureValue;
    public String pureNormalized;
    public ComparisonResult aggregate;
    public final List<ExternalEvidence> evidence = new ArrayList<>();
}
