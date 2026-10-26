package cl.uss.ca.quality.consistency;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Capability registry. Not executed by the agent.
 */
public final class ExternalEvidenceCapabilityTest {
    private ExternalEvidenceCapabilityTest() {
    }

    public static void main(String[] args) {
        ExternalEvidenceProvider crossref = stub("CROSSREF", EnumSet.of(
                ConsistencyField.TITLE, ConsistencyField.PUBLICATION_YEAR, ConsistencyField.JOURNAL_TITLE,
                ConsistencyField.ISSN, ConsistencyField.VOLUME, ConsistencyField.ISSUE, ConsistencyField.PAGES,
                ConsistencyField.PUBLISHER, ConsistencyField.LICENSE, ConsistencyField.FUNDING_AWARD,
                ConsistencyField.AUTHORS
        ), true);
        ExternalEvidenceProvider openalex = stub("OPENALEX", EnumSet.of(
                ConsistencyField.TITLE, ConsistencyField.PUBLICATION_YEAR, ConsistencyField.JOURNAL_TITLE,
                ConsistencyField.ISSN, ConsistencyField.ACCESS_TYPE, ConsistencyField.AUTHORS
        ), true);
        ExternalEvidenceProvider unpaywall = stub("UNPAYWALL", EnumSet.of(
                ConsistencyField.TITLE, ConsistencyField.PUBLICATION_YEAR, ConsistencyField.JOURNAL_TITLE,
                ConsistencyField.ISSN, ConsistencyField.PUBLISHER, ConsistencyField.ACCESS_TYPE, ConsistencyField.LICENSE
        ), true);
        ExternalEvidenceProvider orcid = stub("ORCID", EnumSet.of(
                ConsistencyField.TITLE, ConsistencyField.PUBLICATION_YEAR, ConsistencyField.JOURNAL_TITLE,
                ConsistencyField.ISSN, ConsistencyField.AUTHORS
        ), true);
        ExternalEvidenceProvider scopus = stub("SCOPUS", EnumSet.of(
                ConsistencyField.TITLE, ConsistencyField.PUBLICATION_YEAR, ConsistencyField.JOURNAL_TITLE,
                ConsistencyField.ISSN, ConsistencyField.VOLUME, ConsistencyField.ISSUE, ConsistencyField.PAGES,
                ConsistencyField.AUTHORS
        ), true);
        ExternalEvidenceProvider dspace = stub("DSPACE", EnumSet.of(
                ConsistencyField.TITLE, ConsistencyField.PUBLICATION_YEAR, ConsistencyField.JOURNAL_TITLE,
                ConsistencyField.ISSN, ConsistencyField.PUBLISHER, ConsistencyField.ACCESS_TYPE,
                ConsistencyField.LICENSE, ConsistencyField.AUTHORS
        ), true);
        ExternalEvidenceProvider missing = stub("MISSING", EnumSet.of(ConsistencyField.TITLE), false);
        ExternalMetadataCapabilityRegistry registry = ExternalMetadataCapabilityRegistry.of(
                List.of(crossref, openalex, unpaywall, orcid, scopus, dspace, missing)
        );
        assert registry.supports("CROSSREF", ConsistencyField.TITLE);
        assert !registry.supports("OPENALEX", ConsistencyField.VOLUME);
        assert !registry.supports("UNPAYWALL", ConsistencyField.AUTHORS);
        assert registry.supports("ORCID", ConsistencyField.AUTHORS);
        assert registry.supports("SCOPUS", ConsistencyField.PAGES);
        assert registry.supports("DSPACE", ConsistencyField.ACCESS_TYPE);
        assert !registry.supports("DSPACE", ConsistencyField.VOLUME);
        assert !registry.supports("MISSING", ConsistencyField.TITLE);
        assert registry.available().stream().noneMatch(item -> "MISSING".equals(item.source()));
        System.out.println("ExternalEvidenceCapabilityTest OK");
    }

    private static ExternalEvidenceProvider stub(String source, Set<ConsistencyField> fields, boolean available) {
        return new ExternalEvidenceProvider() {
            @Override
            public String source() {
                return source;
            }

            @Override
            public Set<ConsistencyField> capabilities() {
                return fields;
            }

            @Override
            public boolean available() {
                return available;
            }

            @Override
            public Map<String, SourceSnapshot> loadByDois(List<String> normalizedDois) {
                return Map.of();
            }
        };
    }
}
