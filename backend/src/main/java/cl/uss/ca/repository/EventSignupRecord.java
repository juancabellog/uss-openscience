package cl.uss.ca.repository;

import java.time.LocalDateTime;

public class EventSignupRecord {
    public long id;
    public long eventId;
    public long participantId;
    public String publicCode;
    public int seqNo;
    public String status;
    public String emailNormalized;
    public String displayName;
    public String facultyOrInstitution;
    public String campusName;
    public String positionTitle;
    public String affiliationOrigin;
    public String directoryPersonUuid;
    public String snapshotJson;
    public LocalDateTime createdAt;
}
