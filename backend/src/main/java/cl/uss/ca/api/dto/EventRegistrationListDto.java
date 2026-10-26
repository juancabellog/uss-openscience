package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class EventRegistrationListDto {
    public Long eventId;
    public String title;
    public int confirmedCount;
    public Integer capacity;
    public List<EventRegistrationDto> items = new ArrayList<>();
}
