package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class EventIndicatorsDto {
    public int totalEvents;
    public int publishedEvents;
    public int advanceRegistrations;
    public int effectiveAttendees;
    public int walkIns;
    public int confirmedAbsences;
    public Double registeredAttendanceRate;
    public int uniquePeople;
    public int recurringPeople;
    public int pureLinked;
    public boolean attendanceTablesAvailable;
    public String from;
    public String to;
    public final List<EventNameCountDto> byFaculty = new ArrayList<>();
    public final List<EventNameCountDto> byPosition = new ArrayList<>();

    public static class EventNameCountDto {
        public String label;
        public int total;
    }
}
