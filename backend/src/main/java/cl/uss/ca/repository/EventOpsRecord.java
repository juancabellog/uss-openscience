package cl.uss.ca.repository;

import java.time.LocalDateTime;

public final class EventOpsRecord {
    private EventOpsRecord() {
    }

    public static final class Artifact {
        public long id;
        public long eventId;
        public int versionNo;
        public String kind;
        public String originalFilename;
        public String storedName;
        public String sha256;
        public long byteSize;
        public int blankRows;
        public String generatedBy;
        public LocalDateTime generatedAt;
    }

    public static final class ArtifactRow {
        public long artifactId;
        public int rowNumber;
        public Long registrationId;
        public String publicCode;
        public String displayName;
        public String facultyOrInstitution;
        public String positionTitle;
        public String emailNormalized;
        public boolean blankRow;
    }

    public static final class Document {
        public long id;
        public long eventId;
        public String kind;
        public String originalFilename;
        public String storedName;
        public String contentType;
        public String sha256;
        public long byteSize;
        public String note;
        public String uploadedBy;
        public LocalDateTime uploadedAt;
    }

    public static final class Attendance {
        public long id;
        public long eventId;
        public long registrationId;
        public String status;
        public String source;
        public Long artifactId;
        public Integer artifactRowNumber;
        public boolean walkIn;
        public String notes;
        public String confirmedBy;
        public LocalDateTime confirmedAt;
        public String publicCode;
        public String displayName;
        public String email;
        public String facultyOrInstitution;
        public String positionTitle;
        public String affiliationOrigin;
        public int seqNo;
    }

    public static final class Import {
        public long id;
        public long eventId;
        public Long artifactId;
        public String source;
        public String originalFilename;
        public String storedName;
        public String sha256;
        public String status;
        public String createdBy;
        public LocalDateTime createdAt;
        public LocalDateTime confirmedAt;
    }

    public static final class ImportRow {
        public long id;
        public long importId;
        public int rowNumber;
        public String registrationCode;
        public Integer artifactVersion;
        public String proposedStatus;
        public String matchStatus;
        public Long matchedRegistrationId;
        public String displayName;
        public String notes;
    }
}
