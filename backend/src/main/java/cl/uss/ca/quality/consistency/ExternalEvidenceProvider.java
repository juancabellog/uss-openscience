package cl.uss.ca.quality.consistency;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ExternalEvidenceProvider {
    String source();

    Set<ConsistencyField> capabilities();

    boolean available();

    Map<String, SourceSnapshot> loadByDois(List<String> normalizedDois);
}
