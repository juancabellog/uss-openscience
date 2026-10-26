package cl.uss.ca.api.dto;

public class OrcidCandidateSelectDto {
    public Long candidateId;
    /**
     * When true, queue the person for PURE apply. When false, keep or clear only
     * the apply queue. Null means: choosing a candidate does not queue; clearing
     * a candidate also unqueues.
     */
    public Boolean queued;
}
