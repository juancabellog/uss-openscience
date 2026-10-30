package cl.uss.ca.ethics.auth;

import java.util.List;

/**
 * Espejo de Project.researcherId del seed de ethics-uss.
 * Juan es responsable de sus seis proyectos; el YAML demo solo muestra uno.
 */
final class DemoProjectOwnershipSource implements ProjectOwnershipSource {
    private static final List<OwnedProject> OWNED = List.of(
            new OwnedProject("u-inv-1", "prj-1"),
            new OwnedProject("u-inv-1", "prj-2"),
            new OwnedProject("u-inv-1", "prj-3"),
            new OwnedProject("u-inv-1", "prj-4"),
            new OwnedProject("u-inv-1", "prj-5"),
            new OwnedProject("u-inv-1", "prj-6")
    );

    @Override
    public List<OwnedProject> ownedBy(String userId) {
        return OWNED.stream().filter(item -> item.userId().equals(userId)).toList();
    }
}
