package cl.uss.ca.quality.consistency;

import cl.uss.ca.quality.CrossrefComparison;

import java.util.ArrayList;
import java.util.List;

public final class ConsistencyEvaluator {
    private ConsistencyEvaluator() {
    }

    public static ConsistencyEvaluation evaluate(
            PureSnapshot pure,
            List<SourceSnapshot> sources,
            ConsistencyField field
    ) {
        ConsistencyEvaluation evaluation = new ConsistencyEvaluation();
        evaluation.field = field;
        evaluation.pureValue = pureValue(pure, field);
        evaluation.pureNormalized = normalizePure(pure, field);
        if (sources != null) {
            for (SourceSnapshot source : sources) {
                ExternalEvidence evidence = compare(pure, source, field);
                if (evidence != null) {
                    evaluation.evidence.add(evidence);
                }
            }
        }
        evaluation.aggregate = ConsistencyAggregator.aggregate(evaluation.evidence);
        return evaluation;
    }

    static ExternalEvidence compare(PureSnapshot pure, SourceSnapshot source, ConsistencyField field) {
        if (source == null || field == null) {
            return null;
        }
        return switch (field) {
            case TITLE -> textEvidence(source, field, source.title,
                    CrossrefComparison.normalizeComparable(source.title),
                    BibliographicComparators.title(pure.title, source.title));
            case PUBLICATION_YEAR -> yearEvidence(source, pure.publicationYear);
            case JOURNAL_TITLE -> textEvidence(source, field, source.journalTitle,
                    CrossrefComparison.normalizeComparable(source.journalTitle),
                    BibliographicComparators.journal(pure.journalTitle, source.journalTitle));
            case ISSN -> issnEvidence(source, pure.issns);
            case VOLUME -> textEvidence(source, field, source.volume,
                    BibliographicComparators.normalizeExact(source.volume),
                    BibliographicComparators.exactText(pure.volume, source.volume));
            case ISSUE -> textEvidence(source, field, source.issue,
                    BibliographicComparators.normalizeExact(source.issue),
                    BibliographicComparators.exactText(pure.issue, source.issue));
            case PAGES -> pagesEvidence(pure, source);
            case PUBLISHER -> textEvidence(source, field, source.publisher,
                    BibliographicComparators.normalizeExact(source.publisher),
                    BibliographicComparators.publisher(pure.publisher, source.publisher));
            case ACCESS_TYPE -> accessEvidence(pure, source);
            case LICENSE -> licenseEvidence(pure, source);
            case FUNDING_AWARD -> awardEvidence(pure, source);
            case AUTHORS -> authorsEvidence(pure, source);
        };
    }

    private static ExternalEvidence textEvidence(
            SourceSnapshot source,
            ConsistencyField field,
            String original,
            String normalized,
            ComparisonResult result
    ) {
        if (BibliographicComparators.blank(original)) {
            return null;
        }
        return evidence(source, field, original, normalized, result);
    }

    private static ExternalEvidence yearEvidence(SourceSnapshot source, Integer pureYear) {
        if (source.publicationYear == null) {
            return null;
        }
        return evidence(
                source,
                ConsistencyField.PUBLICATION_YEAR,
                String.valueOf(source.publicationYear),
                String.valueOf(source.publicationYear),
                BibliographicComparators.year(pureYear, source.publicationYear)
        );
    }

    private static ExternalEvidence issnEvidence(SourceSnapshot source, List<String> pureIssns) {
        if (source.issns.isEmpty()) {
            return null;
        }
        return evidence(
                source,
                ConsistencyField.ISSN,
                BibliographicComparators.display(source.issns),
                String.join(",", source.issns.stream().map(IssnNormalizer::normalize).filter(v -> v != null).toList()),
                BibliographicComparators.issn(pureIssns, source.issns)
        );
    }

    private static ExternalEvidence pagesEvidence(PureSnapshot pure, SourceSnapshot source) {
        boolean hasExternal = !BibliographicComparators.blank(source.pages)
                || !BibliographicComparators.blank(source.articleNumber);
        if (!hasExternal) {
            return null;
        }
        String original = !BibliographicComparators.blank(source.pages) ? source.pages : source.articleNumber;
        LocatorKind kind = BibliographicComparators.locatorKind(source.pages, source.articleNumber);
        ComparisonResult result = BibliographicComparators.pages(pure.pages, source.pages, source.articleNumber);
        ExternalEvidence item = evidence(
                source,
                ConsistencyField.PAGES,
                original,
                BibliographicComparators.normalizeExact(original),
                result
        );
        item.locatorKind = kind;
        return item;
    }

    private static ExternalEvidence accessEvidence(PureSnapshot pure, SourceSnapshot source) {
        if (source.access == null && BibliographicComparators.blank(source.accessRaw)) {
            return null;
        }
        AccessSemantic semantic = source.access != null
                ? source.access
                : AccessMapper.fromOa(null, source.accessRaw);
        String original = source.accessRaw != null ? source.accessRaw : String.valueOf(semantic);
        return evidence(
                source,
                ConsistencyField.ACCESS_TYPE,
                original,
                semantic.name(),
                BibliographicComparators.access(pure.access, semantic)
        );
    }

    private static ExternalEvidence licenseEvidence(PureSnapshot pure, SourceSnapshot source) {
        if (BibliographicComparators.blank(source.licenseRaw) && BibliographicComparators.blank(source.licenseNormalized)) {
            return null;
        }
        String original = source.licenseRaw != null ? source.licenseRaw : source.licenseNormalized;
        String normalized = source.licenseNormalized != null
                ? source.licenseNormalized
                : LicenseNormalizer.normalize(source.licenseRaw);
        return evidence(
                source,
                ConsistencyField.LICENSE,
                original,
                normalized,
                BibliographicComparators.license(pure.licenseRaw, original)
        );
    }

    private static ExternalEvidence awardEvidence(PureSnapshot pure, SourceSnapshot source) {
        if (source.awardNumbers.isEmpty()) {
            return null;
        }
        return evidence(
                source,
                ConsistencyField.FUNDING_AWARD,
                BibliographicComparators.display(source.awardNumbers),
                String.join(",", source.awardNumbers.stream()
                        .map(BibliographicComparators::normalizeExact)
                        .filter(v -> !v.isEmpty())
                        .toList()),
                BibliographicComparators.awards(pure.awardNumbers, source.awardNumbers)
        );
    }

    private static ExternalEvidence authorsEvidence(PureSnapshot pure, SourceSnapshot source) {
        if (source.authors.isEmpty()) {
            return null;
        }
        List<AuthorEvidence> local = AuthorSetComparator.copy(pure.authors);
        List<AuthorEvidence> remote = AuthorSetComparator.copy(source.authors);
        ComparisonResult result = AuthorSetComparator.compare(local, remote, source.completeAuthorList);
        ExternalEvidence item = evidence(
                source,
                ConsistencyField.AUTHORS,
                AuthorSetComparator.summary(local, remote),
                AuthorSetComparator.fingerprint(remote),
                result
        );
        item.completeAuthorList = source.completeAuthorList;
        item.authors.addAll(remote);
        item.pureAuthors = local;
        return item;
    }

    private static ExternalEvidence evidence(
            SourceSnapshot source,
            ConsistencyField field,
            String original,
            String normalized,
            ComparisonResult result
    ) {
        ExternalEvidence item = new ExternalEvidence();
        item.source = source.source;
        item.sourceRecordId = source.sourceRecordId;
        item.field = field;
        item.originalValue = original;
        item.normalizedValue = normalized;
        item.result = result;
        item.lastExternalSyncAt = source.lastSyncAt;
        return item;
    }

    static String pureValue(PureSnapshot pure, ConsistencyField field) {
        return switch (field) {
            case TITLE -> pure.title;
            case PUBLICATION_YEAR -> pure.publicationYear == null ? null : String.valueOf(pure.publicationYear);
            case JOURNAL_TITLE -> pure.journalTitle;
            case ISSN -> BibliographicComparators.display(pure.issns);
            case VOLUME -> pure.volume;
            case ISSUE -> pure.issue;
            case PAGES -> pure.pages;
            case PUBLISHER -> pure.publisher;
            case ACCESS_TYPE -> pure.accessRaw != null
                    ? pure.accessRaw
                    : (pure.access == null ? null : pure.access.name());
            case LICENSE -> pure.licenseRaw;
            case FUNDING_AWARD -> BibliographicComparators.display(pure.awardNumbers);
            case AUTHORS -> AuthorSetComparator.summary(pure.authors, List.of());
        };
    }

    static String normalizePure(PureSnapshot pure, ConsistencyField field) {
        return switch (field) {
            case TITLE -> CrossrefComparison.normalizeComparable(pure.title);
            case PUBLICATION_YEAR -> pure.publicationYear == null ? null : String.valueOf(pure.publicationYear);
            case JOURNAL_TITLE -> CrossrefComparison.normalizeComparable(pure.journalTitle);
            case ISSN -> pure.issns.stream().map(IssnNormalizer::normalize).filter(v -> v != null).reduce((a, b) -> a + "," + b).orElse(null);
            case VOLUME, ISSUE, PAGES, PUBLISHER -> BibliographicComparators.normalizeExact(pureValue(pure, field));
            case ACCESS_TYPE -> pure.access == null ? null : pure.access.name();
            case LICENSE -> LicenseNormalizer.normalize(pure.licenseRaw);
            case FUNDING_AWARD -> pure.awardNumbers.stream()
                    .map(BibliographicComparators::normalizeExact)
                    .filter(v -> !v.isEmpty())
                    .reduce((a, b) -> a + "," + b)
                    .orElse(null);
            case AUTHORS -> AuthorSetComparator.fingerprint(pure.authors);
        };
    }
}
