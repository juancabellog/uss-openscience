package cl.uss.ca.service;

import cl.uss.ca.api.dto.JournalCiteScoreSummaryDto;
import cl.uss.ca.api.dto.JournalMetricsDto;
import cl.uss.ca.api.dto.JournalQuartileCategoryDto;
import cl.uss.ca.api.dto.JournalQuartileSummaryDto;
import cl.uss.ca.api.dto.MetricFetchStateDto;
import cl.uss.ca.api.dto.MetricObservationDto;
import cl.uss.ca.api.dto.PersonHindexSummaryDto;
import cl.uss.ca.api.dto.PersonMetricsDto;
import cl.uss.ca.api.dto.ResearchOutputFwciSummaryDto;
import cl.uss.ca.api.dto.ResearchOutputMetricsDto;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.pure.metrics.PureMetricCatalog;
import cl.uss.ca.external.pure.metrics.PureMetricsParser;
import cl.uss.ca.repository.JournalRepository;
import cl.uss.ca.repository.PersonRepository;
import cl.uss.ca.repository.PureMetricRepository;
import cl.uss.ca.repository.PureMetricTables;
import cl.uss.ca.repository.ResearchOutputRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

@ApplicationScoped
public class PureMetricsService {

    @Inject
    PureMetricRepository metricRepository;

    @Inject
    JournalRepository journalRepository;

    @Inject
    ResearchOutputRepository researchOutputRepository;

    @Inject
    PersonRepository personRepository;

    public JournalMetricsDto journalMetrics(long journalId, int page, int size) {
        if (journalRepository.findById(journalId) == null) {
            throw new NotFoundException("JOURNAL_NOT_FOUND", "Journal was not found");
        }
        int safePage = Math.max(0, page);
        int safeSize = size < 1 ? 50 : Math.min(size, 100);
        JournalMetricsDto dto = new JournalMetricsDto();
        dto.journalId = journalId;
        dto.scopusJournal = fetchOrNever(PureMetricTables.JOURNAL, journalId, PureMetricCatalog.COLLECTION_SCOPUS_JOURNAL);
        dto.scopusCitescore = fetchOrNever(PureMetricTables.JOURNAL, journalId, PureMetricCatalog.COLLECTION_SCOPUS_CITESCORE);
        List<MetricObservationDto> all = metricRepository.loadObservations(PureMetricTables.JOURNAL, journalId);
        List<MetricObservationDto> citeScores = new ArrayList<>();
        List<MetricObservationDto> others = new ArrayList<>();
        List<MetricObservationDto> citescoreCollection = new ArrayList<>();
        for (MetricObservationDto observation : all) {
            if (PureMetricCatalog.COLLECTION_SCOPUS_JOURNAL.equals(observation.collectionId)) {
                if (PureMetricCatalog.METRIC_CITESCORE.equals(observation.metricId)) {
                    citeScores.add(observation);
                } else {
                    others.add(observation);
                }
            } else if (PureMetricCatalog.COLLECTION_SCOPUS_CITESCORE.equals(observation.collectionId)) {
                citescoreCollection.add(observation);
            }
        }
        citeScores.sort(yearDesc());
        dto.citeScoreHistory = citeScores;
        dto.otherScopusJournal = others;
        dto.latestCiteScore = latestCiteScore(citeScores);
        List<JournalQuartileCategoryDto> pivoted = pivotQuartiles(citescoreCollection);
        dto.latestQuartiles = latestQuartiles(pivoted, citescoreCollection);
        dto.quartileTotal = pivoted.size();
        dto.quartilePage = safePage;
        dto.quartileSize = safeSize;
        int from = Math.min(safePage * safeSize, pivoted.size());
        int to = Math.min(from + safeSize, pivoted.size());
        dto.quartileHistory = new ArrayList<>(pivoted);
        dto.quartileRows = toQuartileHistory(pivoted, from, to);
        return dto;
    }

    public ResearchOutputMetricsDto researchOutputMetrics(long id) {
        if (researchOutputRepository.findById(id) == null) {
            throw new NotFoundException("RESEARCH_OUTPUT_NOT_FOUND", "Research output not found");
        }
        ResearchOutputMetricsDto dto = new ResearchOutputMetricsDto();
        dto.researchOutputId = id;
        dto.sciVal = fetchOrNever(PureMetricTables.RESEARCH_OUTPUT, id, PureMetricCatalog.COLLECTION_SCIVAL);
        dto.observations = metricRepository.loadObservations(PureMetricTables.RESEARCH_OUTPUT, id);
        dto.fwci = latestFwci(dto.observations);
        return dto;
    }

    public PersonMetricsDto personMetrics(long id) {
        if (personRepository.findPersonById(id) == null) {
            throw new NotFoundException("PERSON_NOT_FOUND", "Person not found");
        }
        PersonMetricsDto dto = new PersonMetricsDto();
        dto.personId = id;
        dto.hIndex = fetchOrNever(PureMetricTables.PERSON, id, PureMetricCatalog.COLLECTION_HINDEX);
        dto.observations = metricRepository.loadObservations(PureMetricTables.PERSON, id);
        dto.scopusAllYears = scopusAllYears(dto.observations);
        return dto;
    }

    public static JournalCiteScoreSummaryDto latestCiteScore(List<MetricObservationDto> citeScores) {
        if (citeScores == null || citeScores.isEmpty()) {
            return null;
        }
        MetricObservationDto latest = citeScores.stream()
                .filter(item -> item.metricYear != null)
                .max(Comparator.comparingInt(item -> item.metricYear))
                .orElse(citeScores.get(0));
        JournalCiteScoreSummaryDto dto = new JournalCiteScoreSummaryDto();
        dto.value = PureMetricRepository.numericValue(latest);
        dto.year = latest.metricYear;
        dto.lastSyncedAt = latest.lastSyncedAt;
        return dto;
    }

    public static List<JournalQuartileCategoryDto> pivotQuartiles(List<MetricObservationDto> rows) {
        Map<String, JournalQuartileCategoryDto> byKey = new LinkedHashMap<>();
        Map<String, Integer> years = new LinkedHashMap<>();
        if (rows == null) {
            return List.of();
        }
        for (MetricObservationDto row : rows) {
            String key = (row.metricYear == null ? "" : row.metricYear)
                    + "|" + Objects.toString(row.categoryUri, "")
                    + "|" + Objects.toString(row.categoryTermEn, "")
                    + "|" + Objects.toString(row.categoryTermEs, "");
            JournalQuartileCategoryDto dto = byKey.computeIfAbsent(key, ignored -> {
                JournalQuartileCategoryDto created = new JournalQuartileCategoryDto();
                created.year = row.metricYear;
                created.categoryUri = row.categoryUri;
                created.categoryTermEn = row.categoryTermEn;
                created.categoryTermEs = row.categoryTermEs;
                years.put(key, row.metricYear);
                return created;
            });
            if (PureMetricCatalog.METRIC_QUARTILE.equals(row.metricId)) {
                dto.quartile = PureMetricRepository.integerValue(row);
            } else if (PureMetricCatalog.METRIC_PERCENTILE.equals(row.metricId)) {
                dto.percentile = PureMetricRepository.integerValue(row);
            } else if (PureMetricCatalog.METRIC_RANK.equals(row.metricId)) {
                dto.rank = PureMetricRepository.integerValue(row);
            } else if (PureMetricCatalog.METRIC_CATEGORY.equals(row.metricId) && dto.categoryTermEn == null) {
                dto.categoryTermEn = row.stringValue;
            }
        }
        List<Map.Entry<String, JournalQuartileCategoryDto>> entries = new ArrayList<>(byKey.entrySet());
        entries.sort((left, right) -> {
            Integer yearLeft = years.get(left.getKey());
            Integer yearRight = years.get(right.getKey());
            int yearCmp = Integer.compare(yearRight == null ? Integer.MIN_VALUE : yearRight, yearLeft == null ? Integer.MIN_VALUE : yearLeft);
            if (yearCmp != 0) {
                return yearCmp;
            }
            String catLeft = Objects.toString(left.getValue().categoryTermEn, Objects.toString(left.getValue().categoryTermEs, ""));
            String catRight = Objects.toString(right.getValue().categoryTermEn, Objects.toString(right.getValue().categoryTermEs, ""));
            return catLeft.compareToIgnoreCase(catRight);
        });
        List<JournalQuartileCategoryDto> result = new ArrayList<>();
        for (Map.Entry<String, JournalQuartileCategoryDto> entry : entries) {
            result.add(entry.getValue());
        }
        return result;
    }

    public static JournalQuartileSummaryDto latestQuartiles(
            List<JournalQuartileCategoryDto> pivoted,
            List<MetricObservationDto> raw
    ) {
        Integer latestYear = null;
        if (raw != null) {
            for (MetricObservationDto row : raw) {
                if (row.metricYear == null || !PureMetricCatalog.METRIC_QUARTILE.equals(row.metricId)) {
                    continue;
                }
                if (latestYear == null || row.metricYear > latestYear) {
                    latestYear = row.metricYear;
                }
            }
        }
        if (latestYear == null) {
            return null;
        }
        JournalQuartileSummaryDto dto = new JournalQuartileSummaryDto();
        dto.year = latestYear;
        TreeSet<Integer> quartiles = new TreeSet<>();
        for (JournalQuartileCategoryDto row : pivoted) {
            if (!Objects.equals(row.year, latestYear) || row.quartile == null) {
                continue;
            }
            dto.categories.add(row);
            quartiles.add(row.quartile);
        }
        dto.multipleCategories = dto.categories.size() > 1;
        dto.compactLabel = compactQuartiles(quartiles);
        if (!dto.categories.isEmpty()) {
            dto.lastSyncedAt = lastSynced(raw, PureMetricCatalog.METRIC_QUARTILE, latestYear);
        }
        return dto;
    }

    public static String compactQuartiles(TreeSet<Integer> quartiles) {
        if (quartiles == null || quartiles.isEmpty()) {
            return null;
        }
        List<String> labels = new ArrayList<>();
        for (Integer value : quartiles) {
            labels.add("Q" + value);
        }
        return String.join(" · ", labels);
    }

    public static ResearchOutputFwciSummaryDto latestFwci(List<MetricObservationDto> observations) {
        MetricObservationDto latest = null;
        if (observations == null) {
            return null;
        }
        for (MetricObservationDto row : observations) {
            if (!PureMetricCatalog.COLLECTION_SCIVAL.equals(row.collectionId)
                    || !PureMetricCatalog.METRIC_FWCI.equals(row.metricId)) {
                continue;
            }
            if (latest == null || compareYear(row.metricYear, latest.metricYear) > 0) {
                latest = row;
            }
        }
        if (latest == null) {
            return null;
        }
        ResearchOutputFwciSummaryDto dto = new ResearchOutputFwciSummaryDto();
        dto.value = PureMetricRepository.numericValue(latest);
        dto.year = latest.metricYear;
        dto.sourceTermEn = latest.sourceTermEn;
        dto.lastSyncedAt = latest.lastSyncedAt;
        return dto;
    }

    public static PersonHindexSummaryDto scopusAllYears(List<MetricObservationDto> observations) {
        if (observations == null) {
            return null;
        }
        MetricObservationDto hindex = null;
        MetricObservationDto citations = null;
        for (MetricObservationDto row : observations) {
            if (!PureMetricCatalog.COLLECTION_HINDEX.equals(row.collectionId)) {
                continue;
            }
            if (!PureMetricsParser.looksLikeScopus(row.sourceUri, row.sourceTermEn, row.sourceTermEs)) {
                continue;
            }
            if (!PureMetricsParser.looksLikeAllYears(row.categoryUri, row.categoryTermEn, row.categoryTermEs)) {
                continue;
            }
            if (PureMetricCatalog.METRIC_HINDEX.equalsIgnoreCase(row.metricId)) {
                hindex = row;
            } else if (PureMetricCatalog.METRIC_TOTAL_CITATIONS.equalsIgnoreCase(row.metricId)) {
                citations = row;
            }
        }
        if (hindex == null && citations == null) {
            return null;
        }
        PersonHindexSummaryDto dto = new PersonHindexSummaryDto();
        MetricObservationDto source = hindex != null ? hindex : citations;
        dto.hindex = PureMetricRepository.integerValue(hindex);
        dto.totalCitations = PureMetricRepository.integerValue(citations);
        dto.sourceTermEn = source.sourceTermEn;
        dto.sourceTermEs = source.sourceTermEs;
        dto.categoryTermEn = source.categoryTermEn;
        dto.categoryTermEs = source.categoryTermEs;
        dto.lastSyncedAt = source.lastSyncedAt;
        return dto;
    }

    private MetricFetchStateDto fetchOrNever(PureMetricTables tables, long ownerId, String collection) {
        MetricFetchStateDto dto = metricRepository.loadFetch(tables, ownerId, collection);
        if (dto != null) {
            return dto;
        }
        MetricFetchStateDto never = new MetricFetchStateDto();
        never.collectionId = collection;
        never.lastOutcome = "NEVER";
        never.observationCount = 0;
        return never;
    }

    private static List<MetricObservationDto> toQuartileHistory(List<JournalQuartileCategoryDto> rows, int from, int to) {
        List<MetricObservationDto> items = new ArrayList<>();
        for (JournalQuartileCategoryDto row : rows.subList(from, to)) {
            MetricObservationDto item = new MetricObservationDto();
            item.collectionId = PureMetricCatalog.COLLECTION_SCOPUS_CITESCORE;
            item.categoryUri = row.categoryUri;
            item.categoryTermEs = row.categoryTermEs;
            item.categoryTermEn = row.categoryTermEn;
            item.metricYear = row.year;
            item.metricId = PureMetricCatalog.METRIC_QUARTILE;
            item.valueKind = "INTEGER";
            item.integerValue = row.quartile == null ? null : row.quartile.longValue();
            item.stringValue = compactQuartilePayload(row);
            items.add(item);
        }
        return items;
    }

    private static String compactQuartilePayload(JournalQuartileCategoryDto row) {
        return "percentile=" + Objects.toString(row.percentile, "")
                + ";rank=" + Objects.toString(row.rank, "");
    }

    private static String lastSynced(List<MetricObservationDto> raw, String metricId, Integer year) {
        for (MetricObservationDto row : raw) {
            if (metricId.equals(row.metricId) && Objects.equals(year, row.metricYear)) {
                return row.lastSyncedAt;
            }
        }
        return null;
    }

    private static Comparator<MetricObservationDto> yearDesc() {
        return Comparator.comparing((MetricObservationDto item) -> item.metricYear == null ? Integer.MIN_VALUE : item.metricYear)
                .reversed();
    }

    private static int compareYear(Integer left, Integer right) {
        int l = left == null ? Integer.MIN_VALUE : left;
        int r = right == null ? Integer.MIN_VALUE : right;
        return Integer.compare(l, r);
    }

    public static void assertPage(int page, int size) {
        if (page < 0) {
            throw new ValidationException("INVALID_PAGE", "page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new ValidationException("INVALID_SIZE", "size must be between 1 and 100");
        }
    }
}
