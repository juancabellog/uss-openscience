package cl.uss.ca.domain;

import cl.uss.ca.domain.OpenAlexPersonMatcher.WorkEvidence;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistence cardinality for OpenAlex match_work. Not executed by the agent.
 */
public final class OpenAlexMatchWorkPlannerTest {
    private OpenAlexMatchWorkPlannerTest() {
    }

    public static void main(String[] args) {
        sameMatchSameOutputTwoAuthorshipsIsOneWorkTwoEvidences();
        sameMatchWorkRepeatedIsIdempotent();
        sameMatchTwoOutputsAreTwoWorks();
        fullRepeatIsIdempotent();
        incrementalRepeatIsIdempotent();
        System.out.println("OpenAlexMatchWorkPlannerTest OK");
    }

    static void sameMatchSameOutputTwoAuthorshipsIsOneWorkTwoEvidences() {
        List<WorkEvidence> works = List.of(
                evidence(1772L, 0, "Dongsheng Cai"),
                evidence(1772L, 1, "Jingjing Cai")
        );
        List<OpenAlexMatchWorkPlanner.MatchWorkGroup> groups = OpenAlexMatchWorkPlanner.groupByResearchOutput(works);
        assert groups.size() == 1;
        assert groups.get(0).evidences.size() == 2;
        assertEquals("Dongsheng Cai", groups.get(0).evidences.get(0).rawAuthorName);
        assertEquals("Jingjing Cai", groups.get(0).evidences.get(1).rawAuthorName);
    }

    static void sameMatchWorkRepeatedIsIdempotent() {
        List<WorkEvidence> works = List.of(
                evidence(1772L, 0, "Dongsheng Cai"),
                evidence(1772L, 0, "Dongsheng Cai")
        );
        List<OpenAlexMatchWorkPlanner.MatchWorkGroup> groups = OpenAlexMatchWorkPlanner.groupByResearchOutput(works);
        assert groups.size() == 1;
        assert groups.get(0).evidences.size() == 1;
    }

    static void sameMatchTwoOutputsAreTwoWorks() {
        List<WorkEvidence> works = List.of(
                evidence(1772L, 0, "Jingjing Cai"),
                evidence(1776L, 0, "Jingjing Cai")
        );
        List<OpenAlexMatchWorkPlanner.MatchWorkGroup> groups = OpenAlexMatchWorkPlanner.groupByResearchOutput(works);
        assert groups.size() == 2;
    }

    static void fullRepeatIsIdempotent() {
        List<WorkEvidence> first = List.of(
                evidence(1772L, 0, "Dongsheng Cai"),
                evidence(1772L, 1, "Jingjing Cai")
        );
        List<WorkEvidence> second = new ArrayList<>(first);
        second.add(evidence(1772L, 0, "Dongsheng Cai"));
        assert OpenAlexMatchWorkPlanner.groupByResearchOutput(first).size()
                == OpenAlexMatchWorkPlanner.groupByResearchOutput(second).size();
        assert OpenAlexMatchWorkPlanner.groupByResearchOutput(second).get(0).evidences.size() == 2;
    }

    static void incrementalRepeatIsIdempotent() {
        List<WorkEvidence> incremental = List.of(evidence(1772L, 1, "Jingjing Cai"));
        List<OpenAlexMatchWorkPlanner.MatchWorkGroup> first = OpenAlexMatchWorkPlanner.groupByResearchOutput(incremental);
        List<OpenAlexMatchWorkPlanner.MatchWorkGroup> second = OpenAlexMatchWorkPlanner.groupByResearchOutput(incremental);
        assert first.size() == 1;
        assert second.size() == 1;
        assert first.get(0).researchOutputId.equals(second.get(0).researchOutputId);
        assert first.get(0).evidences.size() == second.get(0).evidences.size();
    }

    private static WorkEvidence evidence(long outputId, int sourceOrder, String rawName) {
        WorkEvidence evidence = new WorkEvidence();
        evidence.researchOutputId = outputId;
        evidence.normalizedDoi = "10.1080/15548627.2015.1100356";
        evidence.sourceOrder = sourceOrder;
        evidence.rawAuthorName = rawName;
        evidence.authorDisplayName = "Jingjing Cai";
        evidence.openAlexAuthorId = "A5004790427";
        return evidence;
    }

    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
