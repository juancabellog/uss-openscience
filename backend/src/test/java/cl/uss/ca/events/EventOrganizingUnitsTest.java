package cl.uss.ca.events;

public final class EventOrganizingUnitsTest {
    private EventOrganizingUnitsTest() {
    }

    public static void main(String[] args) {
        assert EventOrganizingUnits.matchExact("Vicerrectoría") != null;
        assert "VICERRECTORIA".equals(EventOrganizingUnits.matchExact("vicerrectoria").code);
        assert EventOrganizingUnits.matchExact("Vice-Rectory") != null;
        assert "CIENCIA_ABIERTA".equals(EventOrganizingUnits.matchExact("Ciencia Abierta").code);
        assert EventOrganizingUnits.matchExact("Vicerrectoría de Investigación") == null;
        assert EventOrganizingUnits.matchExact("Vicerrectoria de Investigacion y Doctorados") == null;

        EventOrganizingUnits.Resolved official = EventOrganizingUnits.resolve("CIENCIA_ABIERTA", "texto previo de Otro");
        assert "CIENCIA_ABIERTA".equals(official.code);
        assert "Ciencia Abierta".equals(official.storedText);

        EventOrganizingUnits.Resolved other = EventOrganizingUnits.resolve("OTHER", "Facultad de Medicina");
        assert other.other();
        assert "Facultad de Medicina".equals(other.storedText);

        EventOrganizingUnits.Resolved leftover = EventOrganizingUnits.resolve("OTHER", "  ");
        assert leftover.other();
        assert leftover.storedText == null;

        EventOrganizingUnits.Resolved legacy = EventOrganizingUnits.resolve(null, "DISEI");
        assert "DISEI".equals(legacy.code);

        EventOrganizingUnits.Resolved unknown = EventOrganizingUnits.resolve(null, "Vicerrectoría de Investigación");
        assert unknown.other();
        assert "Vicerrectoría de Investigación".equals(unknown.storedText);

        assert EventOrganizingUnits.resolve("NO_EXISTE", null).invalid;
        assert "Open Science".equals(EventOrganizingUnits.display("CIENCIA_ABIERTA", "Ciencia Abierta", "en"));
        assert "Ciencia Abierta".equals(EventOrganizingUnits.display("CIENCIA_ABIERTA", "Ciencia Abierta", "es"));
        assert "Facultad de Medicina".equals(EventOrganizingUnits.display("OTHER", "Facultad de Medicina", "en"));
        System.out.println("EventOrganizingUnitsTest OK");
    }
}
