package cl.uss.ca.service;

import cl.uss.ca.api.dto.IndicatorMeasureDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.ResearcherIdentityCaseDto;
import cl.uss.ca.api.dto.ResearcherIdentityIndicatorsDto;
import cl.uss.ca.api.dto.ResearcherIdentitySignatureDetailDto;
import cl.uss.ca.api.dto.ResearcherIdentitySignatureVariantDto;
import cl.uss.ca.domain.AuthorNameMatcher;
import cl.uss.ca.domain.UssOrganizationIdentity;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.OrcidEmploymentRepository;
import cl.uss.ca.repository.OrcidEmploymentRepository.StoredEmployment;
import cl.uss.ca.repository.ResearcherIdentityRepository;
import cl.uss.ca.repository.ResearcherIdentityRepository.PersonIdentityRow;
import cl.uss.ca.repository.ResearcherIdentityRepository.SignatureContributionRow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ResearcherIdentityIndicatorsService {
    public static final String INDICATOR_SCOPUS_COVERAGE = "scopusCoverage";
    public static final String INDICATOR_MULTIPLE_SCOPUS = "multipleScopusProfiles";
    public static final String INDICATOR_CONTROLLED_SIGNATURE = "controlledSignature";
    public static final String INDICATOR_HIGH_FRAGMENTATION = "highSignatureFragmentation";
    public static final String INDICATOR_ORCID_AFFILIATION = "normalizedUssOrcidAffiliation";
    public static final int HIGH_FRAGMENTATION_THRESHOLD = 3;

    public enum OrcidAffiliationClass {
        NORMALIZED,
        USS_VARIANT,
        USS_NOT_FOUND,
        NOT_EVALUABLE
    }

    private static final Set<String> CASE_SORT = Set.of(
            "name", "position", "orcid", "evaluatedPublications", "distinctSignatureCount", "classification"
    );

    @Inject
    ResearcherIdentityRepository identityRepository;

    @Inject
    OrcidEmploymentRepository employmentRepository;

    public ResearcherIdentityIndicatorsDto summary() {
        long active = identityRepository.countActivePersons();
        long withScopus = identityRepository.countActivePersonsWithScopus();
        long multiple = identityRepository.countActivePersonsWithMultipleDistinctScopus();
        SignatureIndex signatures = loadSignatures();
        OrcidIndex orcid = loadOrcid();

        ResearcherIdentityIndicatorsDto dto = new ResearcherIdentityIndicatorsDto();
        dto.scopusCoverage = IndicatorMeasureDto.of(withScopus, active, "ACTIVE_PERSONS");
        dto.multipleScopusProfiles = IndicatorMeasureDto.of(multiple, withScopus, "PERSONS_WITH_SCOPUS");
        dto.controlledSignature = IndicatorMeasureDto.of(
                signatures.countWithDistinct(1),
                signatures.evaluable.size(),
                "SIGNATURE_EVALUABLE"
        );
        dto.highSignatureFragmentation = IndicatorMeasureDto.of(
                signatures.countWithDistinctAtLeast(HIGH_FRAGMENTATION_THRESHOLD),
                signatures.evaluable.size(),
                "SIGNATURE_EVALUABLE"
        );
        dto.normalizedUssOrcidAffiliation = IndicatorMeasureDto.of(
                orcid.count(OrcidAffiliationClass.NORMALIZED),
                orcid.evaluableCount(),
                "ORCID_EVALUABLE"
        );
        dto.orcidNormalizedCount = orcid.count(OrcidAffiliationClass.NORMALIZED);
        dto.orcidVariantCount = orcid.count(OrcidAffiliationClass.USS_VARIANT);
        dto.orcidNotFoundCount = orcid.count(OrcidAffiliationClass.USS_NOT_FOUND);
        dto.orcidNotEvaluableCount = orcid.count(OrcidAffiliationClass.NOT_EVALUABLE);
        return dto;
    }

    public PageResponse<ResearcherIdentityCaseDto> cases(
            String indicator,
            String classification,
            String search,
            String sort,
            String direction,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? 25 : Math.min(size, 100);
        List<ResearcherIdentityCaseDto> items = listCases(indicator, classification, search, sort, direction);
        long total = items.size();
        int from = Math.min(safePage * safeSize, items.size());
        int to = Math.min(from + safeSize, items.size());
        return PageResponse.of(items.subList(from, to), safePage, safeSize, total);
    }

    public List<ResearcherIdentityCaseDto> exportCases(
            String indicator,
            String classification,
            String search,
            String sort,
            String direction
    ) {
        List<ResearcherIdentityCaseDto> items = listCases(indicator, classification, search, sort, direction);
        if (items.isEmpty()) {
            throw new ValidationException("EXPORT_EMPTY", "No records to export with the selected filters");
        }
        return items;
    }

    private List<ResearcherIdentityCaseDto> listCases(
            String indicator,
            String classification,
            String search,
            String sort,
            String direction
    ) {
        String code = requireIndicator(indicator);
        String safeSort = sort == null || sort.isBlank() ? "name" : sort.trim();
        if (!CASE_SORT.contains(safeSort)) {
            throw new ValidationException(
                    "INVALID_SORT",
                    "sort must be name, position, orcid, evaluatedPublications, distinctSignatureCount or classification"
            );
        }
        boolean descending = "desc".equalsIgnoreCase(direction);
        List<ResearcherIdentityCaseDto> items = switch (code) {
            case INDICATOR_CONTROLLED_SIGNATURE -> signatureCases(1, 1);
            case INDICATOR_HIGH_FRAGMENTATION -> signatureCases(HIGH_FRAGMENTATION_THRESHOLD, Integer.MAX_VALUE);
            case INDICATOR_ORCID_AFFILIATION -> orcidCases(classification);
            default -> throw new ValidationException(
                    "INVALID_INDICATOR",
                    "Use people list for Scopus coverage and multiple Scopus profiles"
            );
        };
        items = filterSearch(items, search);
        items.sort(caseComparator(safeSort, descending));
        return items;
    }

    public ResearcherIdentitySignatureDetailDto signatureDetail(long personId) {
        SignatureIndex signatures = loadSignatures();
        SignaturePerson person = signatures.evaluable.get(personId);
        if (person == null) {
            throw new NotFoundException("PERSON_NOT_FOUND", "Person is not evaluable for academic signature");
        }
        ResearcherIdentitySignatureDetailDto dto = new ResearcherIdentitySignatureDetailDto();
        dto.personId = person.personId;
        dto.fullName = person.fullName();
        dto.position = person.position;
        dto.evaluatedPublications = person.outputIds.size();
        dto.distinctSignatureCount = person.variants.size();
        List<SignatureVariant> variants = new ArrayList<>(person.variants.values());
        variants.sort(Comparator
                .comparingInt((SignatureVariant variant) -> variant.outputIds.size())
                .reversed()
                .thenComparing(variant -> variant.display, String.CASE_INSENSITIVE_ORDER));
        for (SignatureVariant variant : variants) {
            ResearcherIdentitySignatureVariantDto item = new ResearcherIdentitySignatureVariantDto();
            item.displaySignature = variant.display;
            item.normalizedSignature = variant.key;
            item.publicationCount = variant.outputIds.size();
            dto.variants.add(item);
        }
        return dto;
    }

    private List<ResearcherIdentityCaseDto> signatureCases(int minDistinct, int maxDistinct) {
        SignatureIndex signatures = loadSignatures();
        List<ResearcherIdentityCaseDto> items = new ArrayList<>();
        for (SignaturePerson person : signatures.evaluable.values()) {
            int distinct = person.variants.size();
            if (distinct < minDistinct || distinct > maxDistinct) {
                continue;
            }
            ResearcherIdentityCaseDto dto = basePerson(person);
            dto.evaluatedPublications = person.outputIds.size();
            dto.distinctSignatureCount = distinct;
            dto.primarySignature = person.primaryDisplay();
            items.add(dto);
        }
        return items;
    }

    private List<ResearcherIdentityCaseDto> orcidCases(String classification) {
        OrcidIndex index = loadOrcid();
        OrcidAffiliationClass filter = parseClassification(classification);
        List<ResearcherIdentityCaseDto> items = new ArrayList<>();
        for (OrcidPerson person : index.people.values()) {
            if (filter != null && person.classification != filter) {
                continue;
            }
            ResearcherIdentityCaseDto dto = new ResearcherIdentityCaseDto();
            dto.personId = person.personId;
            dto.pureId = person.pureId;
            dto.firstName = person.firstName;
            dto.lastName = person.lastName;
            dto.fullName = fullName(person.firstName, person.lastName);
            dto.position = person.position;
            dto.orcid = person.orcid;
            dto.classification = person.classification.name();
            dto.employmentOrganizationName = person.displayOrganization();
            dto.employmentOrganizationNames.addAll(person.organizationNames);
            items.add(dto);
        }
        return items;
    }

    private SignatureIndex loadSignatures() {
        SignatureIndex index = new SignatureIndex();
        for (SignatureContributionRow row : identityRepository.listSignatureContributions()) {
            String key = AuthorNameMatcher.signatureKey(row.signatureFirst, row.signatureLast);
            if (key.isEmpty()) {
                continue;
            }
            SignaturePerson person = index.evaluable.computeIfAbsent(row.personId, id -> {
                SignaturePerson created = new SignaturePerson();
                created.personId = row.personId;
                created.pureId = row.pureId;
                created.firstName = row.firstName;
                created.lastName = row.lastName;
                created.position = row.position;
                created.orcid = row.orcid;
                return created;
            });
            person.outputIds.add(row.researchOutputId);
            String display = AuthorNameMatcher.signatureDisplay(row.signatureFirst, row.signatureLast);
            SignatureVariant variant = person.variants.computeIfAbsent(key, ignored -> {
                SignatureVariant created = new SignatureVariant();
                created.key = key;
                created.display = display;
                return created;
            });
            variant.outputIds.add(row.researchOutputId);
            if (display.length() > variant.display.length()) {
                variant.display = display;
            }
        }
        return index;
    }

    private OrcidIndex loadOrcid() {
        OrcidIndex index = new OrcidIndex();
        for (PersonIdentityRow row : identityRepository.listActivePersons()) {
            OrcidPerson person = fromPerson(row);
            person.classification = OrcidAffiliationClass.NOT_EVALUABLE;
            index.people.put(row.personId, person);
        }
        if (!employmentRepository.available()) {
            return index;
        }
        Map<Long, List<StoredEmployment>> byPerson = new HashMap<>();
        for (StoredEmployment row : employmentRepository.listForActivePersons()) {
            byPerson.computeIfAbsent(row.personId, ignored -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<Long, List<StoredEmployment>> entry : byPerson.entrySet()) {
            OrcidPerson person = index.people.get(entry.getKey());
            if (person == null) {
                continue;
            }
            person.classification = classifyPerson(entry.getValue(), person);
        }
        return index;
    }

    private static OrcidAffiliationClass classifyPerson(List<StoredEmployment> employments, OrcidPerson person) {
        if (employments == null || employments.isEmpty()) {
            return OrcidAffiliationClass.NOT_EVALUABLE;
        }
        String normalizedName = null;
        String variantName = null;
        boolean evaluable = false;
        for (StoredEmployment employment : employments) {
            if (employment.organizationName != null && !employment.organizationName.isBlank()) {
                person.organizationNames.add(employment.organizationName.trim());
            }
            if (!isEvaluableEmployment(employment)) {
                continue;
            }
            evaluable = true;
            UssOrganizationIdentity.Kind kind = UssOrganizationIdentity.classifyEmployment(
                    employment.organizationName,
                    employment.organizationIdentifier,
                    employment.organizationIdentifierType
            );
            if (kind == UssOrganizationIdentity.Kind.NORMALIZED && normalizedName == null) {
                normalizedName = displayName(employment);
            } else if (kind == UssOrganizationIdentity.Kind.USS_VARIANT && variantName == null) {
                variantName = displayName(employment);
            }
        }
        if (normalizedName != null) {
            person.preferredOrganization = normalizedName;
            return OrcidAffiliationClass.NORMALIZED;
        }
        if (variantName != null) {
            person.preferredOrganization = variantName;
            return OrcidAffiliationClass.USS_VARIANT;
        }
        if (!evaluable) {
            return OrcidAffiliationClass.NOT_EVALUABLE;
        }
        if (person.organizationNames.size() == 1) {
            person.preferredOrganization = person.organizationNames.get(0);
        }
        return OrcidAffiliationClass.USS_NOT_FOUND;
    }

    private static boolean isEvaluableEmployment(StoredEmployment employment) {
        return (employment.organizationName != null && !employment.organizationName.isBlank())
                || (employment.organizationIdentifier != null && !employment.organizationIdentifier.isBlank());
    }

    private static String displayName(StoredEmployment employment) {
        if (employment.organizationName != null && !employment.organizationName.isBlank()) {
            return employment.organizationName.trim();
        }
        return employment.organizationIdentifier;
    }

    private static OrcidPerson fromPerson(PersonIdentityRow row) {
        OrcidPerson person = new OrcidPerson();
        person.personId = row.personId;
        person.pureId = row.pureId;
        person.firstName = row.firstName;
        person.lastName = row.lastName;
        person.position = row.position;
        person.orcid = row.orcid;
        return person;
    }

    private static ResearcherIdentityCaseDto basePerson(SignaturePerson person) {
        ResearcherIdentityCaseDto dto = new ResearcherIdentityCaseDto();
        dto.personId = person.personId;
        dto.pureId = person.pureId;
        dto.firstName = person.firstName;
        dto.lastName = person.lastName;
        dto.fullName = person.fullName();
        dto.position = person.position;
        dto.orcid = person.orcid;
        return dto;
    }

    private static List<ResearcherIdentityCaseDto> filterSearch(List<ResearcherIdentityCaseDto> items, String search) {
        if (search == null || search.isBlank()) {
            return items;
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<ResearcherIdentityCaseDto> filtered = new ArrayList<>();
        for (ResearcherIdentityCaseDto item : items) {
            if (contains(item.fullName, needle)
                    || contains(item.position, needle)
                    || contains(item.orcid, needle)
                    || contains(item.primarySignature, needle)
                    || contains(item.employmentOrganizationName, needle)
                    || (item.pureId != null && String.valueOf(item.pureId).contains(needle))) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static Comparator<ResearcherIdentityCaseDto> caseComparator(String sort, boolean descending) {
        Comparator<ResearcherIdentityCaseDto> comparator = switch (sort) {
            case "position" -> Comparator.comparing(
                    (ResearcherIdentityCaseDto item) -> nullToEmpty(item.position),
                    String.CASE_INSENSITIVE_ORDER
            );
            case "orcid" -> Comparator.comparing(
                    (ResearcherIdentityCaseDto item) -> nullToEmpty(item.orcid),
                    String.CASE_INSENSITIVE_ORDER
            );
            case "evaluatedPublications" -> Comparator.comparingInt(
                    (ResearcherIdentityCaseDto item) -> item.evaluatedPublications == null ? 0 : item.evaluatedPublications
            );
            case "distinctSignatureCount" -> Comparator.comparingInt(
                    (ResearcherIdentityCaseDto item) -> item.distinctSignatureCount == null ? 0 : item.distinctSignatureCount
            );
            case "classification" -> Comparator.comparing(
                    (ResearcherIdentityCaseDto item) -> nullToEmpty(item.classification),
                    String.CASE_INSENSITIVE_ORDER
            );
            default -> Comparator.comparing(
                    (ResearcherIdentityCaseDto item) -> nullToEmpty(item.lastName),
                    String.CASE_INSENSITIVE_ORDER
            ).thenComparing(
                    item -> nullToEmpty(item.firstName),
                    String.CASE_INSENSITIVE_ORDER
            );
        };
        comparator = comparator.thenComparingLong(item -> item.personId);
        return descending ? comparator.reversed() : comparator;
    }

    private static String requireIndicator(String indicator) {
        if (indicator == null || indicator.isBlank()) {
            throw new ValidationException("INVALID_INDICATOR", "indicator is required");
        }
        return switch (indicator.trim()) {
            case INDICATOR_SCOPUS_COVERAGE, "scopus-coverage" -> INDICATOR_SCOPUS_COVERAGE;
            case INDICATOR_MULTIPLE_SCOPUS, "multiple-scopus" -> INDICATOR_MULTIPLE_SCOPUS;
            case INDICATOR_CONTROLLED_SIGNATURE, "controlled-signature" -> INDICATOR_CONTROLLED_SIGNATURE;
            case INDICATOR_HIGH_FRAGMENTATION, "high-signature-fragmentation" -> INDICATOR_HIGH_FRAGMENTATION;
            case INDICATOR_ORCID_AFFILIATION, "orcid-affiliation" -> INDICATOR_ORCID_AFFILIATION;
            default -> throw new ValidationException("INVALID_INDICATOR", "Unknown researcher-identity indicator");
        };
    }

    private static OrcidAffiliationClass parseClassification(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return OrcidAffiliationClass.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "INVALID_CLASSIFICATION",
                    "classification must be ALL, NORMALIZED, USS_VARIANT, USS_NOT_FOUND or NOT_EVALUABLE"
            );
        }
    }

    private static String fullName(String firstName, String lastName) {
        return AuthorNameMatcher.displayName(firstName, lastName);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class SignatureIndex {
        private final Map<Long, SignaturePerson> evaluable = new LinkedHashMap<>();

        private long countWithDistinct(int expected) {
            long count = 0;
            for (SignaturePerson person : evaluable.values()) {
                if (person.variants.size() == expected) {
                    count++;
                }
            }
            return count;
        }

        private long countWithDistinctAtLeast(int minimum) {
            long count = 0;
            for (SignaturePerson person : evaluable.values()) {
                if (person.variants.size() >= minimum) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final class SignaturePerson {
        private long personId;
        private Long pureId;
        private String firstName;
        private String lastName;
        private String position;
        private String orcid;
        private final Set<Long> outputIds = new HashSet<>();
        private final Map<String, SignatureVariant> variants = new LinkedHashMap<>();

        private String fullName() {
            return AuthorNameMatcher.displayName(firstName, lastName);
        }

        private String primaryDisplay() {
            SignatureVariant best = null;
            for (SignatureVariant variant : variants.values()) {
                if (best == null
                        || variant.outputIds.size() > best.outputIds.size()
                        || (variant.outputIds.size() == best.outputIds.size()
                        && variant.display.compareToIgnoreCase(best.display) < 0)) {
                    best = variant;
                }
            }
            return best == null ? null : best.display;
        }
    }

    private static final class SignatureVariant {
        private String key;
        private String display;
        private final Set<Long> outputIds = new HashSet<>();
    }

    private static final class OrcidIndex {
        private final Map<Long, OrcidPerson> people = new LinkedHashMap<>();

        private long count(OrcidAffiliationClass classification) {
            long count = 0;
            for (OrcidPerson person : people.values()) {
                if (person.classification == classification) {
                    count++;
                }
            }
            return count;
        }

        private long evaluableCount() {
            return count(OrcidAffiliationClass.NORMALIZED)
                    + count(OrcidAffiliationClass.USS_VARIANT)
                    + count(OrcidAffiliationClass.USS_NOT_FOUND);
        }
    }

    private static final class OrcidPerson {
        private long personId;
        private Long pureId;
        private String firstName;
        private String lastName;
        private String position;
        private String orcid;
        private OrcidAffiliationClass classification = OrcidAffiliationClass.NOT_EVALUABLE;
        private final List<String> organizationNames = new ArrayList<>();
        private String preferredOrganization;

        private String displayOrganization() {
            if (preferredOrganization != null && !preferredOrganization.isBlank()) {
                return preferredOrganization;
            }
            return organizationNames.isEmpty() ? null : organizationNames.get(0);
        }
    }
}
