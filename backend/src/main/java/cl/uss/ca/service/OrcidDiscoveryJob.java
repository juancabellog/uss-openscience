package cl.uss.ca.service;

import cl.uss.ca.api.dto.OrcidCandidateDto;
import cl.uss.ca.api.dto.OrcidCandidateEvidenceDto;
import cl.uss.ca.domain.AuthorNameMatcher;
import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.domain.OrcidCandidateMatcher;
import cl.uss.ca.domain.OrcidCandidateMatcher.EvidenceRow;
import cl.uss.ca.domain.OrcidCandidateMatcher.Evaluation;
import cl.uss.ca.domain.OrcidCandidateMatcher.LocalPublicationInput;
import cl.uss.ca.domain.OrcidCandidateMatcher.OrcidAuthorInput;
import cl.uss.ca.domain.OrcidExternalIdNormalizer;
import cl.uss.ca.external.orcid.OrcidClient;
import cl.uss.ca.external.orcid.OrcidContributorRecord;
import cl.uss.ca.external.orcid.OrcidExternalIdRecord;
import cl.uss.ca.external.orcid.OrcidSearchPage;
import cl.uss.ca.external.orcid.OrcidWorkGroupRecord;
import cl.uss.ca.external.orcid.OrcidWorkRecord;
import cl.uss.ca.repository.OrcidDiscoveryRepository;
import cl.uss.ca.repository.OrcidDiscoveryRepository.CandidateWrite;
import cl.uss.ca.repository.OrcidDiscoveryRepository.EvidenceWrite;
import cl.uss.ca.repository.OrcidDiscoveryRepository.LocalPublication;
import cl.uss.ca.repository.OrcidDiscoveryRepository.PersonSeed;
import cl.uss.ca.repository.OrcidDiscoveryRepository.RecalcPerson;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class OrcidDiscoveryJob {
    private static final Logger LOG = Logger.getLogger(OrcidDiscoveryJob.class.getName());

    @Inject
    OrcidDiscoveryRepository discoveryRepository;

    @Inject
    OrcidClient orcidClient;

    @Inject
    @ConfigProperty(name = "orcid.search.maxCandidates", defaultValue = "20")
    int maxCandidates;

    @Inject
    @ConfigProperty(name = "orcid.sync.delayMs", defaultValue = "250")
    long delayMs;

    private final OrcidCandidateMatcher matcher = new OrcidCandidateMatcher();

    public void run(long executionId, List<PersonSeed> people) {
        Map<Long, List<LocalPublication>> publications = discoveryRepository.loadPublications(
                people.stream().map(item -> item.personId).toList()
        );
        Map<String, List<OrcidWorkGroupRecord>> worksCache = new HashMap<>();
        int errors = 0;
        for (PersonSeed person : people) {
            try {
                processPerson(executionId, person, publications.getOrDefault(person.personId, List.of()), worksCache);
            } catch (RuntimeException ex) {
                errors++;
                LOG.log(Level.WARNING, "ORCID discovery failed personId=" + person.personId, ex);
                long rowId = discoveryRepository.insertPerson(
                        executionId, person, AuthorNameMatcher.firstToken(person.firstName),
                        AuthorNameMatcher.firstToken(person.lastName), "ERROR", clip(ex.getMessage())
                );
                discoveryRepository.updatePersonSummary(rowId, "ERROR", 0, 0);
                discoveryRepository.incrementProcessed(executionId, "ERROR");
            }
            pause();
        }
        discoveryRepository.finish(executionId, errors > 0 ? "COMPLETED_WITH_WARNINGS" : "COMPLETED");
    }

    /**
     * Re-scores existing candidates from stored evidence and current PURE
     * person/contributor names. Does not call ORCID.
     */
    public void recalculate(long executionId) {
        List<RecalcPerson> people = discoveryRepository.listRecalculablePeople(executionId);
        Map<Long, List<LocalPublication>> publications = discoveryRepository.loadPublications(
                people.stream().map(item -> item.personId).toList()
        );
        for (RecalcPerson person : people) {
            List<LocalPublication> localPubs = publications.getOrDefault(person.personId, List.of());
            List<OrcidCandidateDto> candidates = discoveryRepository.listCandidates(person.discoveryPersonId);
            int strong = 0;
            int doiMatches = 0;
            int conflicts = 0;
            for (OrcidCandidateDto candidate : candidates) {
                CandidateEvaluation evaluation = evaluateFromStoredEvidence(
                        person, localPubs, candidate
                );
                evaluation.write.executionId = executionId;
                evaluation.write.personId = person.personId;
                evaluation.write.discoveryPersonId = person.discoveryPersonId;
                evaluation.write.searchGiven = person.searchGiven;
                evaluation.write.searchFamily = person.searchFamily;
                long candidateId = discoveryRepository.upsertCandidate(evaluation.write);
                discoveryRepository.replaceEvidence(candidateId, evaluation.evidence);
                doiMatches += evaluation.write.doiMatchCount;
                if ("STRONG".equals(evaluation.write.matchLevel)) {
                    strong++;
                }
                if ("CONFLICT".equals(evaluation.write.matchLevel)) {
                    conflicts++;
                }
            }
            String status = classifyPerson(candidates.size(), strong, conflicts);
            discoveryRepository.updatePersonSummary(person.discoveryPersonId, status, candidates.size(), doiMatches);
        }
        discoveryRepository.recountExecution(executionId);
    }

    private void processPerson(
            long executionId,
            PersonSeed person,
            List<LocalPublication> localPubs,
            Map<String, List<OrcidWorkGroupRecord>> worksCache
    ) {
        String given = AuthorNameMatcher.firstToken(person.firstName);
        String family = AuthorNameMatcher.firstToken(person.lastName);
        if (given == null || family == null) {
            long rowId = discoveryRepository.insertPerson(executionId, person, given, family, "NO_CANDIDATE", null);
            discoveryRepository.updatePersonSummary(rowId, "NO_CANDIDATE", 0, 0);
            discoveryRepository.incrementProcessed(executionId, "NO_CANDIDATE");
            return;
        }
        OrcidSearchPage page = orcidClient.searchByName(given, family);
        if (page.numFound == 0 || page.orcids.isEmpty()) {
            long rowId = discoveryRepository.insertPerson(executionId, person, given, family, "NO_CANDIDATE", null);
            discoveryRepository.updatePersonSummary(rowId, "NO_CANDIDATE", 0, 0);
            discoveryRepository.incrementProcessed(executionId, "NO_CANDIDATE");
            return;
        }
        if (page.numFound > maxCandidates) {
            long rowId = discoveryRepository.insertPerson(
                    executionId, person, given, family, "TOO_MANY_CANDIDATES",
                    "num-found=" + page.numFound
            );
            discoveryRepository.updatePersonSummary(rowId, "TOO_MANY_CANDIDATES", 0, 0);
            discoveryRepository.incrementProcessed(executionId, "TOO_MANY_CANDIDATES");
            return;
        }
        long rowId = discoveryRepository.insertPerson(executionId, person, given, family, "MANUAL_REVIEW", null);
        int strong = 0;
        int doiMatches = 0;
        int conflicts = 0;
        for (String orcid : page.orcids) {
            List<OrcidWorkGroupRecord> groups = worksCache.computeIfAbsent(orcid, orcidClient::fetchWorks);
            CandidateEvaluation evaluation = evaluate(person, orcid, localPubs, groups);
            doiMatches += evaluation.write.doiMatchCount;
            if ("STRONG".equals(evaluation.write.matchLevel)) {
                strong++;
            }
            if ("CONFLICT".equals(evaluation.write.matchLevel)) {
                conflicts++;
            }
            evaluation.write.executionId = executionId;
            evaluation.write.personId = person.personId;
            evaluation.write.discoveryPersonId = rowId;
            evaluation.write.searchGiven = given;
            evaluation.write.searchFamily = family;
            long candidateId = discoveryRepository.upsertCandidate(evaluation.write);
            discoveryRepository.replaceEvidence(candidateId, evaluation.evidence);
            pause();
        }
        String status = classifyPerson(page.orcids.size(), strong, conflicts);
        discoveryRepository.updatePersonSummary(rowId, status, page.orcids.size(), doiMatches);
        discoveryRepository.incrementProcessed(executionId, status);
    }

    private CandidateEvaluation evaluate(
            PersonSeed person,
            String orcid,
            List<LocalPublication> localPubs,
            List<OrcidWorkGroupRecord> groups
    ) {
        Map<String, Set<WorkRef>> orcidDois = doisOf(groups);
        List<EvidenceRow> rows = new ArrayList<>();
        boolean anyDoi = false;
        for (LocalPublication local : localPubs) {
            String doi = DoiNormalizer.normalize(local.doi);
            if (doi == null || !orcidDois.containsKey(doi)) {
                continue;
            }
            anyDoi = true;
            Set<WorkRef> works = orcidDois.get(doi);
            boolean verifiedThisDoi = false;
            for (WorkRef ref : works) {
                OrcidWorkRecord detail = loadDetail(orcid, ref);
                rows.add(matcher.matchPublication(
                        person.firstName,
                        person.lastName,
                        toInput(local),
                        toAuthors(detail)
                ));
                verifiedThisDoi = true;
            }
            if (!verifiedThisDoi) {
                rows.add(matcher.matchPublication(
                        person.firstName,
                        person.lastName,
                        toInput(local),
                        List.of()
                ));
            }
        }
        Long alreadyUsed = discoveryRepository.findPersonIdByOrcid(orcid, person.personId);
        return toCandidate(orcid, matcher.decide(rows, anyDoi, alreadyUsed), alreadyUsed);
    }

    private CandidateEvaluation evaluateFromStoredEvidence(
            RecalcPerson person,
            List<LocalPublication> localPubs,
            OrcidCandidateDto candidate
    ) {
        Map<Long, LocalPublication> byOutput = new HashMap<>();
        Map<String, LocalPublication> byDoi = new HashMap<>();
        for (LocalPublication local : localPubs) {
            byOutput.put(local.researchOutputId, local);
            String doi = DoiNormalizer.normalize(local.doi);
            if (doi != null) {
                byDoi.putIfAbsent(doi, local);
            }
        }
        List<EvidenceRow> rows = new ArrayList<>();
        boolean anyDoi = false;
        List<OrcidCandidateEvidenceDto> stored = candidate.evidence == null ? List.of() : candidate.evidence;
        for (OrcidCandidateEvidenceDto item : stored) {
            String doi = DoiNormalizer.normalize(item.doi);
            if (doi == null) {
                continue;
            }
            anyDoi = true;
            LocalPublication local = item.researchOutputId != null
                    ? byOutput.get(item.researchOutputId)
                    : byDoi.get(doi);
            if (local == null) {
                local = fromStored(item, doi);
            }
            List<OrcidAuthorInput> authors = new ArrayList<>();
            if (item.orcidAuthor != null && !item.orcidAuthor.isBlank()) {
                OrcidAuthorInput author = new OrcidAuthorInput();
                author.creditName = item.orcidAuthor;
                author.position = item.orcidPosition;
                authors.add(author);
            }
            rows.add(matcher.matchPublication(person.firstName, person.lastName, toInput(local), authors));
        }
        return toCandidate(candidate.orcid, matcher.decide(rows, anyDoi, candidate.alreadyUsedPersonId),
                candidate.alreadyUsedPersonId);
    }

    private static LocalPublication fromStored(OrcidCandidateEvidenceDto item, String doi) {
        LocalPublication local = new LocalPublication();
        local.researchOutputId = item.researchOutputId == null ? 0L : item.researchOutputId;
        local.doi = doi;
        local.contributorFirstName = item.contributorFirstName;
        local.contributorLastName = item.contributorLastName;
        local.author = item.contributorFullName != null
                ? item.contributorFullName
                : item.localAuthor;
        local.position = item.localPosition;
        return local;
    }

    private CandidateEvaluation toCandidate(String orcid, Evaluation evaluation, Long alreadyUsed) {
        CandidateEvaluation result = new CandidateEvaluation();
        result.write.orcid = orcid;
        result.write.alreadyUsedPersonId = alreadyUsed;
        result.write.score = evaluation.score;
        result.write.matchLevel = evaluation.matchLevel;
        result.write.status = evaluation.status;
        result.write.nameMatch = evaluation.nameMatch;
        result.write.authorMatch = evaluation.authorMatch;
        result.write.positionMatch = evaluation.positionMatch;
        result.write.doiMatchCount = evaluation.doiMatchCount;
        for (EvidenceRow row : evaluation.evidence) {
            result.evidence.add(toWrite(row));
        }
        return result;
    }

    private static EvidenceWrite toWrite(EvidenceRow row) {
        EvidenceWrite write = new EvidenceWrite();
        write.researchOutputId = row.researchOutputId;
        write.doi = row.doi;
        write.localAuthor = row.localAuthor;
        write.localPosition = row.localPosition;
        write.orcidAuthor = row.orcidAuthor;
        write.orcidPosition = row.orcidPosition;
        write.nameMatch = row.nameMatch;
        write.positionMatch = row.positionMatch;
        write.note = row.note;
        write.personFullName = row.personFullName;
        write.contributorFullName = row.contributorFullName;
        write.personFirstName = row.personFirstName;
        write.personLastName = row.personLastName;
        write.contributorFirstName = row.contributorFirstName;
        write.contributorLastName = row.contributorLastName;
        write.namePartitionDifferent = row.namePartitionDifferent;
        write.reasonCodes = OrcidCandidateMatcher.joinReasonCodes(List.copyOf(row.reasonCodes));
        return write;
    }

    private static LocalPublicationInput toInput(LocalPublication local) {
        LocalPublicationInput input = new LocalPublicationInput();
        if (local == null) {
            return input;
        }
        input.researchOutputId = local.researchOutputId;
        input.doi = local.doi;
        input.contributorFirstName = local.contributorFirstName;
        input.contributorLastName = local.contributorLastName;
        input.position = local.position;
        return input;
    }

    private static List<OrcidAuthorInput> toAuthors(OrcidWorkRecord detail) {
        if (detail == null || detail.contributors == null || detail.contributors.isEmpty()) {
            return List.of();
        }
        List<OrcidAuthorInput> authors = new ArrayList<>();
        for (OrcidContributorRecord contributor : detail.contributors) {
            OrcidAuthorInput input = new OrcidAuthorInput();
            input.creditName = contributor.creditName;
            input.position = contributor.position + 1;
            authors.add(input);
        }
        return authors;
    }

    private OrcidWorkRecord loadDetail(String orcid, WorkRef ref) {
        if (ref.detail != null && ref.detail.detailLoaded) {
            return ref.detail;
        }
        if (ref.putCode == null) {
            return ref.detail;
        }
        try {
            return orcidClient.fetchWorkDetail(orcid, ref.putCode);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "ORCID work detail failed orcid=" + orcid + " putCode=" + ref.putCode, ex);
            return ref.detail;
        }
    }

    private Map<String, Set<WorkRef>> doisOf(List<OrcidWorkGroupRecord> groups) {
        Map<String, Set<WorkRef>> map = new HashMap<>();
        if (groups == null) {
            return map;
        }
        for (OrcidWorkGroupRecord group : groups) {
            addDois(map, group.externalIds, group.summaries);
            for (OrcidWorkRecord summary : group.summaries) {
                addDois(map, summary.externalIds, List.of(summary));
            }
        }
        return map;
    }

    private void addDois(Map<String, Set<WorkRef>> map, List<OrcidExternalIdRecord> ids, List<OrcidWorkRecord> works) {
        if (ids == null) {
            return;
        }
        for (OrcidExternalIdRecord id : ids) {
            if (id.type == null || !"doi".equalsIgnoreCase(id.type) || !OrcidExternalIdNormalizer.isSelf(id.relationship)) {
                continue;
            }
            String doi = DoiNormalizer.normalize(id.normalizedValue != null ? id.normalizedValue : id.value);
            if (doi == null) {
                continue;
            }
            Set<WorkRef> refs = map.computeIfAbsent(doi, key -> new HashSet<>());
            if (works == null || works.isEmpty()) {
                refs.add(new WorkRef(null, null));
            } else {
                for (OrcidWorkRecord work : works) {
                    refs.add(new WorkRef(work.putCode, work));
                }
            }
        }
    }

    private static String classifyPerson(int candidates, int strong, int conflicts) {
        if (strong > 1) {
            return "CONFLICT";
        }
        if (strong == 1) {
            return "STRONG";
        }
        if (conflicts > 0) {
            return "CONFLICT";
        }
        return candidates > 0 ? "MANUAL_REVIEW" : "NO_CANDIDATE";
    }

    private void pause() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static String clip(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static final class CandidateEvaluation {
        private final CandidateWrite write = new CandidateWrite();
        private final List<EvidenceWrite> evidence = new ArrayList<>();
    }

    private static final class WorkRef {
        private final Long putCode;
        private final OrcidWorkRecord detail;

        private WorkRef(Long putCode, OrcidWorkRecord detail) {
            this.putCode = putCode;
            this.detail = detail;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WorkRef ref)) {
                return false;
            }
            return putCode == null ? ref.putCode == null : putCode.equals(ref.putCode);
        }

        @Override
        public int hashCode() {
            return putCode == null ? 0 : putCode.hashCode();
        }
    }
}
