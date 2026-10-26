package cl.uss.ca.quality;

import java.util.ArrayList;
import java.util.List;

public class DataQualityPatch {
    public String entity;
    public String recordUuid;
    public Long localRecordId;
    public Long childPureId;
    public String field;
    public String locale;
    public String expectedValue;
    public String newValue;
    public String issueKind;
    public String ruleCode;
    public Long findingId;
    public List<Long> removeChildPureIds = new ArrayList<>();
    public Long keepChildPureId;
}
