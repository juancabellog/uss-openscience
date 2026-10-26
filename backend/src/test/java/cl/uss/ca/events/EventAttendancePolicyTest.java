package cl.uss.ca.events;

public final class EventAttendancePolicyTest {
    private EventAttendancePolicyTest() {
    }

    public static void main(String[] args) {
        assert EventAttendanceStatus.ASISTIO == EventAttendanceStatus.parse("asistio");
        assert EventAttendanceStatus.NO_ASISTIO == EventAttendanceStatus.parse("NO_ASISTIO");
        assert EventAttendanceStatus.REVISAR == EventAttendanceStatus.parse("REVIEW");
        assert "MATCHED".equals(EventAttendancePolicy.matchStatus(1));
        assert "AMBIGUOUS".equals(EventAttendancePolicy.matchStatus(2));
        assert "UNMATCHED".equals(EventAttendancePolicy.matchStatus(0));
        assert EventAttendancePolicy.allowedEvidence("acta.pdf", "SIGNED_MINUTES");
        assert EventAttendancePolicy.allowedEvidence("foto.webp", "PHOTO");
        assert !EventAttendancePolicy.allowedEvidence("foto.gif", "PHOTO");
        assert EventAttendancePolicy.blankRows(null) == 10;
        System.out.println("EventAttendancePolicyTest OK");
    }
}
