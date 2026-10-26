package cl.uss.ca.service;

/**
 * Proposed manual / future automated cases for PURE child correction.
 * Not executed by the agent. Do not call PURE or MySQL from this class.
 *
 * Scopus A: person has identifier A. Remove A.
 *   Expected: PURE no longer contains A; MySQL no longer contains A; UI count updates.
 *
 * Scopus B: person has A value=123 and B value=123. Remove A by childPureId.
 *   Expected: A disappears; B remains.
 *
 * Scopus C: GET current no longer contains A.
 *   Expected: no PUT; reconcile; ALREADY_REMOVED; B is not removed.
 *
 * DOI A: research output has EV A and EV B with the same DOI. Remove A by versionPureId.
 *   Expected: A disappears; B remains.
 *
 * DOI B: A=Closed and B=Open, same DOI. Remove Closed.
 *   Expected: Open remains intact.
 *
 * PURE error: PUT fails.
 *   Expected: MySQL unchanged; UI shows PURE could not be updated.
 *
 * Verify error: PUT OK but GET still shows the child.
 *   Expected: PURE_VERIFICATION_FAILED; MySQL not rewritten as success.
 *
 * MySQL error: PURE updated and GET confirms, reconcile fails.
 *   Expected: REMOTE_SUCCESS_LOCAL_SYNC_FAILED; do not say the remote delete failed.
 *
 * Full sync: PEOPLE or RESEARCH_OUTPUT running.
 *   Expected: 409 SYNC_IN_PROGRESS; no GET/PUT correction.
 */
public final class PureCorrectionCases {

    private PureCorrectionCases() {
    }
}
