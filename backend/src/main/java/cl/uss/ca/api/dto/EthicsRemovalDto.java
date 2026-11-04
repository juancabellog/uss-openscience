package cl.uss.ca.api.dto;

/** Resultado de si el alta de Ethics puede eliminarse. No autoriza por sí mismo. */
public class EthicsRemovalDto {
    public boolean deletable;
    public String reason;
    public boolean pendingInvitation;
    public long snapshots;
    public long reviewAssignments;
    public long recordedActs;
    public long committeeAssignments;
    /** Project, Submission, Review y Document todavía no tienen tabla de Ethics. */
    public boolean serverTracksProjects;
}
