package cl.uss.ca.external.dspace;

import java.util.ArrayList;
import java.util.List;

public class DspaceBundleRecord {
    public String uuid;
    public String name;
    public String bitstreamsHref;
    public boolean bitstreamInventoryComplete;
    public final List<DspaceBitstreamRecord> bitstreams = new ArrayList<>();
}
