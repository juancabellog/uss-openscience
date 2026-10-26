package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class EventsCatalogDto {
    public EventsModuleStatusDto module;
    public List<EventPublicSummaryDto> items = new ArrayList<>();
}
