package cl.uss.ca.external.dspace;

import java.util.ArrayList;
import java.util.List;

public class DspacePureCandidate {
    public long researchOutputId;
    public String normalizedDoi;
    public String handle;
    public String dspaceUuid;
    public String title;
    public Integer year;
    public final List<String> authors = new ArrayList<>();
    public boolean inResearchScope = true;
}
