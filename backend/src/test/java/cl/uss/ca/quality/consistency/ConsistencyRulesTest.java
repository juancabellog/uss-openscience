package cl.uss.ca.quality.consistency;

import cl.uss.ca.quality.DataQualityCandidate;
import cl.uss.ca.quality.DataQualityCatalog;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EXT-01..EXT-12. Not executed by the agent.
 */
public final class ConsistencyRulesTest {
    private ConsistencyRulesTest() {
    }

    public static void main(String[] args) {
        catalog();
        ext01();
        ext02();
        ext04();
        ext07();
        ext08();
        ext09();
        ext09DspaceMetadataOnlyIsNotFulltextOa();
        ext10();
        ext11();
        ext12();
        noDuplicationKey();
        capabilities();
        System.out.println("ConsistencyRulesTest OK");
    }

    private static void catalog() {
        DataQualityCatalog.RuleDef title = DataQualityCatalog.require("EXT-01");
        assert DataQualityCatalog.SOURCE_EXTERNAL.equals(title.sourceFamily());
        assert DataQualityCatalog.CAT_EXTERNAL.equals(title.category());
        assert DataQualityCatalog.MODE_VIEW.equals(title.correctionMode());
        assert DataQualityCatalog.require("EXT-12").ruleText().contains("autores");
        assert !DataQualityCatalog.require("EXT-12").ruleText().contains("financiamiento")
                || DataQualityCatalog.require("EXT-12").ruleText().contains("copy/paste");
        assert DataQualityCatalog.implementableCodes().contains("EXT-12");
        assert DataQualityCatalog.supersededByExtConsistency().contains("CROSSREF-003");
    }

    private static void ext01() {
        PureSnapshot pure = output("Machine learning in medicine");
        SourceSnapshot crossref = sourceSnapshot("CROSSREF", "Machine learning in medicine");
        SourceSnapshot openalex = sourceSnapshot("OPENALEX", "Machine learning in medicine");
        ConsistencyEvaluation ok = ConsistencyEvaluator.evaluate(pure, List.of(crossref, openalex), ConsistencyField.TITLE);
        assert ok.aggregate == ComparisonResult.CONSISTENT;
        assert !ConsistencyAggregator.shouldPersistFinding(ok.aggregate, ConsistencyField.TITLE);

        SourceSnapshot scopus = sourceSnapshot("SCOPUS", "Something else entirely about cats");
        ConsistencyEvaluation conflict = ConsistencyEvaluator.evaluate(
                pure, List.of(crossref, openalex, scopus), ConsistencyField.TITLE);
        assert conflict.aggregate == ComparisonResult.EXTERNAL_SOURCE_CONFLICT;
        assert conflict.evidence.size() == 3;
        assert ConsistencyAggregator.shouldPersistFinding(conflict.aggregate, ConsistencyField.TITLE);

        ConsistencyEvaluation none = ConsistencyEvaluator.evaluate(pure, List.of(), ConsistencyField.TITLE);
        assert none.aggregate == ComparisonResult.NO_EXTERNAL_DATA;
        assert !ConsistencyAggregator.shouldPersistFinding(none.aggregate, ConsistencyField.TITLE);
    }

    private static void ext02() {
        PureSnapshot pure = output("Title");
        pure.publicationYear = 2020;
        SourceSnapshot published = sourceSnapshot("CROSSREF", "Title");
        published.publicationYear = 2020;
        assert ConsistencyEvaluator.evaluate(pure, List.of(published), ConsistencyField.PUBLICATION_YEAR)
                .aggregate == ComparisonResult.CONSISTENT;

        published.publicationYear = 2021;
        assert ConsistencyEvaluator.evaluate(pure, List.of(published), ConsistencyField.PUBLICATION_YEAR)
                .aggregate == ComparisonResult.DIFFERENCE_DETECTED;

        SourceSnapshot depositedOnly = sourceSnapshot("CROSSREF", "Title");
        depositedOnly.publicationYear = null;
        assert ConsistencyEvaluator.evaluate(pure, List.of(depositedOnly), ConsistencyField.PUBLICATION_YEAR)
                .aggregate == ComparisonResult.NO_EXTERNAL_DATA;
        assert PublishedYearExtractor.from("2019-05-01", null) == 2019;
        assert PublishedYearExtractor.from((String) null) == null;
    }

    private static void ext04() {
        PureSnapshot pure = output("Title");
        pure.issns.add("1234-5678");
        SourceSnapshot crossref = sourceSnapshot("CROSSREF", "Title");
        crossref.issns.add("12345678");
        crossref.issns.add("8765-4321");
        assert ConsistencyEvaluator.evaluate(pure, List.of(crossref), ConsistencyField.ISSN)
                .aggregate == ComparisonResult.CONSISTENT;

        SourceSnapshot other = sourceSnapshot("CROSSREF", "Title");
        other.issns.add("87654321");
        assert ConsistencyEvaluator.evaluate(pure, List.of(other), ConsistencyField.ISSN)
                .aggregate == ComparisonResult.DIFFERENCE_DETECTED;
    }

    private static void ext07() {
        PureSnapshot pure = output("Title");
        pure.pages = "123-130";
        SourceSnapshot pages = sourceSnapshot("CROSSREF", "Title");
        pages.pages = "123-130";
        assert ConsistencyEvaluator.evaluate(pure, List.of(pages), ConsistencyField.PAGES)
                .aggregate == ComparisonResult.CONSISTENT;

        SourceSnapshot article = sourceSnapshot("CROSSREF", "Title");
        article.articleNumber = "e105";
        ConsistencyEvaluation notComparable = ConsistencyEvaluator.evaluate(
                pure, List.of(article), ConsistencyField.PAGES);
        assert notComparable.aggregate == ComparisonResult.NOT_COMPARABLE;
        assert ConsistencyAggregator.shouldPersistFinding(notComparable.aggregate, ConsistencyField.PAGES);
    }

    private static void ext08() {
        PureSnapshot pure = output("Title");
        pure.publisher = "Springer Nature";
        SourceSnapshot same = sourceSnapshot("UNPAYWALL", "Title");
        same.publisher = "springer nature";
        assert ConsistencyEvaluator.evaluate(pure, List.of(same), ConsistencyField.PUBLISHER)
                .aggregate == ComparisonResult.CONSISTENT;

        SourceSnapshot longer = sourceSnapshot("CROSSREF", "Title");
        longer.publisher = "Springer Nature Switzerland AG";
        assert ConsistencyEvaluator.evaluate(pure, List.of(longer), ConsistencyField.PUBLISHER)
                .aggregate == ComparisonResult.DIFFERENCE_DETECTED;
    }

    private static void ext09() {
        PureSnapshot pure = output("Title");
        pure.access = AccessSemantic.OPEN;
        pure.accessRaw = "Open";
        SourceSnapshot unpaywall = sourceSnapshot("UNPAYWALL", "Title");
        unpaywall.access = AccessSemantic.OPEN;
        unpaywall.accessRaw = "gold";
        ConsistencyEvaluation ok = ConsistencyEvaluator.evaluate(
                pure, List.of(unpaywall), ConsistencyField.ACCESS_TYPE);
        assert ok.aggregate == ComparisonResult.CONSISTENT;
        assert "gold".equals(ok.evidence.get(0).originalValue);

        unpaywall.access = AccessSemantic.CLOSED;
        unpaywall.accessRaw = "closed";
        SourceSnapshot openalex = sourceSnapshot("OPENALEX", "Title");
        openalex.access = AccessSemantic.OPEN;
        openalex.accessRaw = "gold";
        ConsistencyEvaluation mixed = ConsistencyEvaluator.evaluate(
                pure, List.of(unpaywall, openalex), ConsistencyField.ACCESS_TYPE);
        assert mixed.aggregate == ComparisonResult.EXTERNAL_SOURCE_CONFLICT;
        assert mixed.evidence.size() == 2;
    }

    private static void ext09DspaceMetadataOnlyIsNotFulltextOa() {
        PureSnapshot pure = output("Title");
        pure.access = AccessSemantic.OPEN;
        pure.accessRaw = "Open";
        SourceSnapshot dspace = sourceSnapshot("DSPACE", "Title");
        dspace.access = AccessMapper.fromDspace("metadata.only", "METADATA_ONLY");
        dspace.accessRaw = "metadata.only";
        assert dspace.access == AccessSemantic.UNKNOWN;
        assert ConsistencyEvaluator.evaluate(pure, List.of(dspace), ConsistencyField.ACCESS_TYPE)
                .aggregate == ComparisonResult.NOT_COMPARABLE;
        SourceSnapshot unpaywall = sourceSnapshot("UNPAYWALL", "Title");
        unpaywall.access = AccessSemantic.OPEN;
        unpaywall.accessRaw = "gold";
        assert ConsistencyEvaluator.evaluate(pure, List.of(unpaywall), ConsistencyField.ACCESS_TYPE)
                .aggregate == ComparisonResult.CONSISTENT;
    }

    private static void ext10() {
        PureSnapshot pure = output("Title");
        pure.licenseRaw = "CC-BY";
        SourceSnapshot unpaywall = sourceSnapshot("UNPAYWALL", "Title");
        unpaywall.licenseRaw = "cc-by";
        SourceSnapshot crossref = sourceSnapshot("CROSSREF", "Title");
        crossref.licenseRaw = "https://creativecommons.org/licenses/by/4.0/";
        assert ConsistencyEvaluator.evaluate(pure, List.of(unpaywall, crossref), ConsistencyField.LICENSE)
                .aggregate == ComparisonResult.CONSISTENT;

        crossref.licenseRaw = "https://creativecommons.org/licenses/by-nc/4.0/";
        assert ConsistencyEvaluator.evaluate(pure, List.of(unpaywall, crossref), ConsistencyField.LICENSE)
                .aggregate == ComparisonResult.EXTERNAL_SOURCE_CONFLICT;
    }

    private static void ext11() {
        PureSnapshot pure = output("Title");
        pure.awardNumbers.add("12345");
        SourceSnapshot crossref = sourceSnapshot("CROSSREF", "Title");
        crossref.awardNumbers.add("12345");
        assert ConsistencyEvaluator.evaluate(pure, List.of(crossref), ConsistencyField.FUNDING_AWARD)
                .aggregate == ComparisonResult.CONSISTENT;

        SourceSnapshot namesOnly = sourceSnapshot("CROSSREF", "Title");
        namesOnly.awardNumbers.clear();
        assert ConsistencyEvaluator.evaluate(pure, List.of(namesOnly), ConsistencyField.FUNDING_AWARD)
                .aggregate == ComparisonResult.NO_EXTERNAL_DATA;
    }

    private static void ext12() {
        PureSnapshot pure = output("Title");
        pure.authors.add(author("Paulina Spaudo Valenzuela", 1));
        SourceSnapshot partitioned = sourceSnapshot("CROSSREF", "Title");
        partitioned.authors.add(author("Paulina Spaudo Valenzuela", 2));
        ConsistencyEvaluation matched = ConsistencyEvaluator.evaluate(
                pure, List.of(partitioned), ConsistencyField.AUTHORS);
        assert matched.aggregate == ComparisonResult.CONSISTENT;
        assert "MATCHED".equals(matched.evidence.get(0).authors.get(0).matchStatus);

        SourceSnapshot extra = sourceSnapshot("OPENALEX", "Title");
        extra.completeAuthorList = Boolean.TRUE;
        extra.authors.add(author("Paulina Spaudo Valenzuela", 1));
        extra.authors.add(author("Jaime Abarca", 2));
        ConsistencyEvaluation difference = ConsistencyEvaluator.evaluate(
                pure, List.of(extra), ConsistencyField.AUTHORS);
        assert difference.aggregate == ComparisonResult.DIFFERENCE_DETECTED;
        assert difference.evidence.get(0).authors.stream().anyMatch(item -> "EXTERNAL_ONLY".equals(item.matchStatus));

        extra.completeAuthorList = Boolean.FALSE;
        extra.authors.clear();
        extra.authors.add(author("Paulina Spaudo Valenzuela", 1));
        pure.authors.add(author("Jaime Alberto Abarca", 2));
        ConsistencyEvaluation truncated = ConsistencyEvaluator.evaluate(
                pure, List.of(extra), ConsistencyField.AUTHORS);
        assert truncated.aggregate == ComparisonResult.CONSISTENT;
    }

    private static void noDuplicationKey() {
        PureSnapshot pure = output("Different title for this paper");
        SourceSnapshot crossref = sourceSnapshot("CROSSREF", "Completely unrelated wording here");
        ConsistencyQualityScanner scanner = new ConsistencyQualityScanner();
        List<DataQualityCandidate> first = scanner.evaluateOutput(pure, Map.of(
                "CROSSREF", Map.of("10.1/abc", crossref)
        ));
        List<DataQualityCandidate> second = scanner.evaluateOutput(pure, Map.of(
                "CROSSREF", Map.of("10.1/abc", crossref)
        ));
        assert !first.isEmpty();
        assert first.get(0).findingKey.equals(second.get(0).findingKey);
    }

    private static void capabilities() {
        ExternalEvidenceProvider datacite = new ExternalEvidenceProvider() {
            @Override
            public String source() {
                return "DATACITE";
            }

            @Override
            public Set<ConsistencyField> capabilities() {
                return EnumSet.of(ConsistencyField.TITLE, ConsistencyField.PUBLISHER);
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public Map<String, SourceSnapshot> loadByDois(List<String> normalizedDois) {
                SourceSnapshot snapshot = sourceSnapshot("DATACITE", "Machine learning in medicine");
                return Map.of("10.1/abc", snapshot);
            }
        };
        ExternalEvidenceProvider empty = new ExternalEvidenceProvider() {
            @Override
            public String source() {
                return "EMPTY";
            }

            @Override
            public Set<ConsistencyField> capabilities() {
                return EnumSet.of(ConsistencyField.TITLE);
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public Map<String, SourceSnapshot> loadByDois(List<String> normalizedDois) {
                return Map.of();
            }
        };
        ExternalMetadataCapabilityRegistry registry = ExternalMetadataCapabilityRegistry.of(List.of(datacite, empty));
        assert registry.supports("DATACITE", ConsistencyField.TITLE);
        assert !registry.supports("DATACITE", ConsistencyField.VOLUME);
        PureSnapshot pure = output("Machine learning in medicine");
        ConsistencyEvaluation evaluation = ConsistencyEvaluator.evaluate(
                pure,
                registry.evidenceFor(registry.loadByDois(List.of("10.1/abc")), "10.1/abc"),
                ConsistencyField.TITLE
        );
        assert evaluation.aggregate == ComparisonResult.CONSISTENT;
        assert evaluation.evidence.stream().anyMatch(item -> "DATACITE".equals(item.source));
    }

    private static PureSnapshot output(String title) {
        PureSnapshot snapshot = new PureSnapshot();
        snapshot.id = 1;
        snapshot.pureId = 10L;
        snapshot.uuid = "uuid";
        snapshot.title = title;
        snapshot.normalizedDois.add("10.1/abc");
        return snapshot;
    }

    private static SourceSnapshot sourceSnapshot(String name, String title) {
        SourceSnapshot snapshot = new SourceSnapshot();
        snapshot.source = name;
        snapshot.sourceRecordId = "1";
        snapshot.normalizedDoi = "10.1/abc";
        snapshot.title = title;
        return snapshot;
    }

    private static AuthorEvidence author(String name, int position) {
        AuthorEvidence author = new AuthorEvidence();
        author.fullName = name;
        author.position = position;
        return author;
    }
}
