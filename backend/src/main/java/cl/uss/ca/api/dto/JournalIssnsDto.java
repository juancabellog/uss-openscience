package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class JournalIssnsDto {
    public long journalId;
    public Long journalPureId;
    public String journalUuid;
    public int count;
    public List<JournalIssnItemDto> items = new ArrayList<>();
}
