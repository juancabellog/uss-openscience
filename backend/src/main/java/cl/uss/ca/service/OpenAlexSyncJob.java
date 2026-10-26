package cl.uss.ca.service;

import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.domain.OpenAlexPersonMatcher;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.openalex.OpenAlexAuthorshipRecord;
import cl.uss.ca.external.openalex.OpenAlexClient;
import cl.uss.ca.external.openalex.OpenAlexFetchResult;
import cl.uss.ca.external.openalex.OpenAlexMapper;
import cl.uss.ca.external.openalex.OpenAlexStatus;
import cl.uss.ca.quality.DataQualityIdentifiers;
import cl.uss.ca.repository.OpenAlexDoiLink;
import cl.uss.ca.repository.OpenAlexReconcileResult;
import cl.uss.ca.repository.OpenAlexRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.sync.DoiIncrementalClassifier;
import cl.uss.ca.sync.IncrementalReason;
import cl.uss.ca.sync.OpenAlexAuthorRecovery;
import cl.uss.ca.sync.OpenAlexSyncPhase;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncMode;
import cl.uss.ca.sync.SyncOwnershipLostException;
import cl.uss.ca.sync.SyncRunState;
import cl.uss.ca.sync.WorkSyncState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class OpenAlexSyncJob {
    private static final Logger LOG = Logger.getLogger(OpenAlexSyncJob.class.getName());
    static final String ENTITY = "OPENALEX";

    @Inject
    OpenAlexService openAlexService;

    @Inject
    OpenAlexClient openAlexClient;

    @Inject
    OpenAlexRepository openAlexRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncJobSupport jobSupport;

    @Inject
    @ConfigProperty(name = "openalex.sync.delayMs", defaultValue = "80")
    long delayMs;

    @Inject
    @ConfigProperty(name = "openalex.maxConcurrency", defaultValue = "1")
    int maxConcurrency;

    @Inject
    @ConfigProperty(name = "openalex.refreshDays", defaultValue = "30")
    int refreshDays;

    private final OpenAlexPersonMatcher matcher = new OpenAlexPersonMatcher();
    private final ObjectMapper json = new ObjectMapper();

    public void runFull(SyncRunState run) throws Exception {
        run.syncMode = cl.uss.ca.sync.SyncMode.FULL;
        run(run);
    }

    public void runIncremental(SyncRunState run) throws Exception {
        run.syncMode = cl.uss.ca.sync.SyncMode.INCREMENTAL;
        run(run);
    }

    public void run(SyncRunState run) throws Exception {
        openAlexService.assertTables();
        enterPhase(run, OpenAlexSyncPhase.WORKS, false);
        Map<String, List<OpenAlexDoiLink>> unique = uniqueDois(openAlexRepository.listDoiLinks(run.isIncremental()));
        int skippedInvalid = unique.size();
        unique = fetchableDois(unique);
        skippedInvalid -= unique.size();
        if (skippedInvalid > 0) {
            run.invalid.addAndGet(skippedInvalid);
        }
        if (run.isIncremental()) {
            unique = retainIncremental(
                    unique,
                    openAlexRepository.loadWorkStates(),
                    openAlexRepository.loadMappedNormalizedDois(),
                    refreshDays,
                    OffsetDateTime.now()
            );
        }
        run.totalExpected = unique.size();
        LOG.info("Starting OPENALEX sync id=" + run.syncId
                + " mode=" + run.syncMode
                + " candidates=" + unique.size()
                + " concurrency=" + Math.max(1, maxConcurrency));
        persistProgress(run);
        Set<String> authorIds = new LinkedHashSet<>();
        int index = 0;
        for (Map.Entry<String, List<OpenAlexDoiLink>> entry : unique.entrySet()) {
            index++;
            String normalized = entry.getKey();
            List<OpenAlexDoiLink> links = entry.getValue();
            String displayDoi = links.get(0).doi;
            try {
                processOne(run, normalized, displayDoi, links, authorIds);
            } catch (Exception ex) {
                jobSupport.record(
                        run,
                        ENTITY,
                        SyncJobSupport.STAGE_OPENALEX_FETCH,
                        null,
                        links.get(0).pureId,
                        firstUuid(links),
                        normalized,
                        ex
                );
                run.processed.incrementAndGet();
            }
            if (index % 10 == 0 || index == unique.size()) {
                persistProgress(run);
            }
            throttle();
        }
        enterPhase(run, OpenAlexSyncPhase.WORKS, true);
        refreshAuthors(run, authorIds, run.isIncremental());
        markConflicts(run);
        finalizeRun(run);
    }

    public void runRecovery(SyncRunState run, SyncRunDto source) throws Exception {
        openAlexService.assertTables();
        run.syncMode = SyncMode.RECOVERY;
        if (source != null) {
            // Provenance of the recovery event. Does not restrict the author set.
            run.sourceSyncExecutionId = source.syncId;
            if (run.message == null) {
                run.message = OpenAlexAuthorRecovery.recoveredFromMessage(source.syncId);
            }
        }
        List<String> pending = openAlexRepository.listPendingAuthors();
        Set<String> authorIds = new LinkedHashSet<>(pending);
        run.totalExpected = authorIds.size();
        run.authorsTotal = authorIds.size();
        enterPhase(run, OpenAlexSyncPhase.AUTHORS, false);
        persistProgress(run);
        refreshAuthors(run, authorIds, false);
        markConflicts(run);
        finalizeRun(run);
    }

    private void processOne(
            SyncRunState run,
            String normalized,
            String displayDoi,
            List<OpenAlexDoiLink> links,
            Set<String> authorIds
    ) {
        if (!DataQualityIdentifiers.isValidDoi(normalized)) {
            run.invalid.incrementAndGet();
            run.processed.incrementAndGet();
            return;
        }
        OpenAlexRepository.StoredWork stored = openAlexRepository.findByNormalizedDoi(normalized);
        OpenAlexFetchResult fetch = openAlexClient.fetchWork(normalized);
        if (OpenAlexStatus.FOUND.equals(fetch.status)) {
            run.oa.incrementAndGet();
        } else if (OpenAlexStatus.NOT_FOUND.equals(fetch.status)) {
            run.notFound.incrementAndGet();
        } else if (OpenAlexStatus.INVALID_DOI.equals(fetch.status)) {
            run.invalid.incrementAndGet();
        } else if (fetch.retryable()) {
            jobSupport.record(
                    run,
                    ENTITY,
                    SyncJobSupport.STAGE_OPENALEX_FETCH,
                    stored == null ? null : stored.id,
                    links.isEmpty() ? null : links.get(0).pureId,
                    firstUuid(links),
                    normalized,
                    new ValidationException(
                            fetch.errorCode == null ? "OPENALEX_HTTP_ERROR" : fetch.errorCode,
                            fetch.errorMessage == null ? "OpenAlex request failed" : fetch.errorMessage
                    )
            );
            run.processed.incrementAndGet();
            return;
        }
        persist(run, normalized, displayDoi, fetch, links, authorIds);
        run.processed.incrementAndGet();
    }

    private void persist(
            SyncRunState run,
            String normalized,
            String displayDoi,
            OpenAlexFetchResult fetch,
            List<OpenAlexDoiLink> links,
            Set<String> authorIds
    ) {
        try {
            OpenAlexReconcileResult result = openAlexRepository.reconcile(
                    normalized, displayDoi, fetch, links, run.syncId
            );
            if ("CREATED".equals(result.kind)) {
                run.created.incrementAndGet();
            } else if ("UPDATED".equals(result.kind)) {
                run.updated.incrementAndGet();
            } else {
                run.unchanged.incrementAndGet();
            }
            syncChangeRepository.save(run.syncId, result.changes);
            if (fetch.found() && fetch.work != null) {
                for (OpenAlexAuthorshipRecord authorship : fetch.work.authorships) {
                    if (authorship.openalexAuthorId != null) {
                        authorIds.add(authorship.openalexAuthorId);
                        openAlexRepository.ensureAuthorStub(
                                authorship.openalexAuthorId, authorship.authorDisplayName, authorship.authorOrcid
                        );
                    }
                }
                matchWork(run, result.workId, fetch.work.openalexWorkId, normalized, links);
            }
        } catch (Exception ex) {
            jobSupport.record(
                    run,
                    ENTITY,
                    SyncJobSupport.STAGE_OPENALEX_UPSERT,
                    null,
                    links.isEmpty() ? null : links.get(0).pureId,
                    firstUuid(links),
                    normalized,
                    ex
            );
        }
    }

    private void matchWork(
            SyncRunState run,
            long workId,
            String openalexWorkOaId,
            String normalized,
            List<OpenAlexDoiLink> links
    ) {
        try {
            List<Long> outputIds = new ArrayList<>();
            for (OpenAlexDoiLink link : links) {
                outputIds.add(link.researchOutputId);
            }
            List<OpenAlexAuthorshipRecord> authorships = openAlexRepository.authorshipsOf(workId);
            List<OpenAlexRepository.MatchContributor> contributors = openAlexRepository.contributorsForOutputs(outputIds);
            Map<String, List<OpenAlexPersonMatcher.WorkEvidence>> grouped = new LinkedHashMap<>();
            Map<String, OpenAlexPersonMatcher.PersonInput> persons = new LinkedHashMap<>();
            Set<Long> personIds = new LinkedHashSet<>();
            Set<String> keptPersonAuthor = new LinkedHashSet<>();
            for (OpenAlexRepository.MatchContributor contributor : contributors) {
                personIds.add(contributor.personId);
                OpenAlexPersonMatcher.PersonInput person = new OpenAlexPersonMatcher.PersonInput();
                person.personId = contributor.personId;
                person.firstName = contributor.personFirst;
                person.lastName = contributor.personLast;
                person.orcid = contributor.orcid;
                person.uuid = contributor.personUuid;
                person.pureId = contributor.pureId;
                persons.put(String.valueOf(contributor.personId), person);
                OpenAlexPersonMatcher.ContributorInput input = new OpenAlexPersonMatcher.ContributorInput();
                input.researchOutputId = contributor.researchOutputId;
                input.contributorId = contributor.contributorId;
                input.doi = normalized;
                input.firstName = contributor.firstName;
                input.lastName = contributor.lastName;
                input.position = contributor.contributorPosition;
                input.contributorCount = contributor.contributorCount;
                input.personUuid = contributor.personUuid;
                for (OpenAlexAuthorshipRecord authorship : authorships) {
                    if (authorship.openalexAuthorId == null) {
                        continue;
                    }
                    OpenAlexPersonMatcher.AuthorshipInput remote = new OpenAlexPersonMatcher.AuthorshipInput();
                    remote.authorshipId = authorship.id;
                    remote.openAlexAuthorId = authorship.openalexAuthorId;
                    remote.openalexWorkOaId = openalexWorkOaId;
                    remote.displayName = authorship.authorDisplayName;
                    remote.rawAuthorName = authorship.rawAuthorName;
                    remote.authorPosition = authorship.authorPosition;
                    remote.sourceOrder = authorship.sourceOrder;
                    remote.orcid = authorship.authorOrcid;
                    remote.observedOrcid = authorship.observedOrcid;
                    remote.ussAffiliation = OpenAlexMapper.ussAffiliation(authorship);
                    if (!authorship.institutions.isEmpty()) {
                        remote.affiliationDisplay = authorship.institutions.get(0).displayName;
                        remote.ror = authorship.institutions.get(0).ror;
                    }
                    if (remote.affiliationDisplay == null) {
                        remote.affiliationDisplay = authorship.rawAffiliationString;
                    }
                    OpenAlexPersonMatcher.WorkEvidence evidence = matcher.evaluateWork(person, input, remote);
                    if (!matcher.isIdentityCandidate(evidence)) {
                        continue;
                    }
                    grouped.computeIfAbsent(contributor.personId + "|" + authorship.openalexAuthorId, key -> new ArrayList<>())
                            .add(evidence);
                }
            }
            for (Map.Entry<String, List<OpenAlexPersonMatcher.WorkEvidence>> entry : grouped.entrySet()) {
                String[] parts = entry.getKey().split("\\|", 2);
                OpenAlexPersonMatcher.PersonInput person = persons.get(parts[0]);
                OpenAlexPersonMatcher.MatchDecision decision = matcher.decide(entry.getValue());
                if (!matcher.shouldPersist(decision)) {
                    continue;
                }
                keptPersonAuthor.add(entry.getKey());
                openAlexRepository.upsertMatch(person, parts[1], decision, evidenceJson(decision));
            }
            openAlexRepository.pruneStaleMatchWorks(personIds, outputIds, keptPersonAuthor);
        } catch (Exception ex) {
            jobSupport.record(run, ENTITY, SyncJobSupport.STAGE_OPENALEX_MATCH, workId, null, normalized, ex);
        }
    }

    private void refreshAuthors(SyncRunState run, Set<String> authorIds, boolean incrementalFilter) {
        Map<String, WorkSyncState> states = openAlexRepository.loadAuthorStates();
        OffsetDateTime now = OffsetDateTime.now();
        Set<String> selected = new LinkedHashSet<>();
        for (String authorId : authorIds) {
            WorkSyncState state = states.getOrDefault(authorId, WorkSyncState.missing());
            IncrementalReason reason = DoiIncrementalClassifier.classifyOpenAlex(state, refreshDays, now);
            if (incrementalFilter && reason == null) {
                continue;
            }
            selected.add(authorId);
        }
        run.authorsTotal = selected.size();
        if (run.isRecovery()) {
            run.totalExpected = selected.size();
        }
        enterPhase(run, OpenAlexSyncPhase.AUTHORS, false);
        persistProgress(run);
        int index = 0;
        long lastBeat = System.nanoTime();
        int sinceBeat = 0;
        for (String authorId : selected) {
            index++;
            sinceBeat++;
            WorkSyncState state = states.getOrDefault(authorId, WorkSyncState.missing());
            try {
                OpenAlexFetchResult fetch = openAlexClient.fetchAuthor(authorId);
                if (fetch.retryable()) {
                    jobSupport.record(
                            run,
                            ENTITY,
                            SyncJobSupport.STAGE_OPENALEX_AUTHOR,
                            null,
                            null,
                            authorId,
                            new ValidationException(
                                    fetch.errorCode == null ? "OPENALEX_HTTP_ERROR" : fetch.errorCode,
                                    fetch.errorMessage == null ? "OpenAlex author request failed" : fetch.errorMessage
                            )
                    );
                } else {
                    boolean wrote = openAlexRepository.upsertAuthor(fetch, run.syncId);
                    if (wrote) {
                        run.authorsProcessed.incrementAndGet();
                        if (OpenAlexAuthorRecovery.isAuthorCreate(state)) {
                            run.authorsCreated.incrementAndGet();
                        } else {
                            run.authorsUpdated.incrementAndGet();
                        }
                    }
                }
            } catch (Exception ex) {
                jobSupport.record(run, ENTITY, SyncJobSupport.STAGE_OPENALEX_AUTHOR, null, null, authorId, ex);
            }
            if (run.isRecovery()) {
                run.processed.set(index);
            }
            long nowNanos = System.nanoTime();
            if (OpenAlexAuthorRecovery.heartbeatDue(sinceBeat, nowNanos - lastBeat) || index == selected.size()) {
                persistProgress(run);
                lastBeat = nowNanos;
                sinceBeat = 0;
            }
            throttle();
        }
        enterPhase(run, OpenAlexSyncPhase.AUTHORS, true);
        persistProgress(run);
    }

    private void markConflicts(SyncRunState run) {
        enterPhase(run, OpenAlexSyncPhase.CONFLICTS, false);
        persistProgress(run);
        openAlexRepository.markConflicts();
        enterPhase(run, OpenAlexSyncPhase.CONFLICTS, true);
        persistProgress(run);
    }

    private void finalizeRun(SyncRunState run) {
        enterPhase(run, OpenAlexSyncPhase.FINALIZING, false);
        persistProgress(run);
        LOG.info("syncId=" + run.syncId
                + " OpenAlex"
                + (run.isRecovery() ? " recovery" : "")
                + " status=" + run.status
                + " processed=" + run.processed.get()
                + " authorsProcessed=" + run.authorsProcessed.get()
                + " authorsCreated=" + run.authorsCreated.get()
                + " authorsUpdated=" + run.authorsUpdated.get()
                + " created=" + run.created.get()
                + " updated=" + run.updated.get()
                + " unchanged=" + run.unchanged.get()
                + " invalid=" + run.invalid.get()
                + " errors=" + run.errors.get());
        enterPhase(run, OpenAlexSyncPhase.FINALIZING, true);
        persistProgress(run);
    }

    private void enterPhase(SyncRunState run, OpenAlexSyncPhase phase, boolean completed) {
        run.phase = phase.name();
        if (completed) {
            LOG.info("syncId=" + run.syncId
                    + " OpenAlex"
                    + (run.isRecovery() ? " recovery" : "")
                    + " phase=" + phase
                    + " completed processed="
                    + (phase.isAuthors() ? run.authorsProcessed.get() : run.processed.get()));
        } else {
            LOG.info("syncId=" + run.syncId
                    + " OpenAlex"
                    + (run.isRecovery() ? " recovery" : "")
                    + " phase=" + phase
                    + " started"
                    + (phase.isAuthors() && run.authorsTotal != null ? " total=" + run.authorsTotal : ""));
        }
    }

    static String firstUuid(List<OpenAlexDoiLink> links) {
        if (links == null) {
            return null;
        }
        for (OpenAlexDoiLink link : links) {
            if (link != null && link.uuid != null && !link.uuid.isBlank()) {
                return link.uuid.trim();
            }
        }
        return null;
    }

    public static Map<String, List<OpenAlexDoiLink>> fetchableDois(Map<String, List<OpenAlexDoiLink>> unique) {
        Map<String, List<OpenAlexDoiLink>> fetchable = new LinkedHashMap<>();
        if (unique == null) {
            return fetchable;
        }
        for (Map.Entry<String, List<OpenAlexDoiLink>> entry : unique.entrySet()) {
            if (DataQualityIdentifiers.isValidDoi(entry.getKey())) {
                fetchable.put(entry.getKey(), entry.getValue());
            }
        }
        return fetchable;
    }

    public static Map<String, List<OpenAlexDoiLink>> uniqueDois(List<OpenAlexDoiLink> links) {
        Map<String, List<OpenAlexDoiLink>> unique = new LinkedHashMap<>();
        if (links == null) {
            return unique;
        }
        for (OpenAlexDoiLink link : links) {
            String normalized = DoiNormalizer.normalize(link.doi);
            if (normalized == null) {
                continue;
            }
            unique.computeIfAbsent(normalized.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(link);
        }
        return unique;
    }

    public static <V> Map<String, V> retainIncremental(
            Map<String, V> unique,
            Map<String, WorkSyncState> storedByDoi,
            Set<String> mappedDois,
            int refreshDays,
            OffsetDateTime now
    ) {
        Map<String, V> selected = new LinkedHashMap<>();
        if (unique == null) {
            return selected;
        }
        for (Map.Entry<String, V> entry : unique.entrySet()) {
            WorkSyncState state = storedByDoi == null ? null : storedByDoi.get(entry.getKey());
            if (state == null) {
                state = WorkSyncState.missing();
            }
            state.mapped = mappedDois != null && mappedDois.contains(entry.getKey());
            IncrementalReason reason = DoiIncrementalClassifier.classifyOpenAlex(state, refreshDays, now);
            if (reason != null) {
                selected.put(entry.getKey(), entry.getValue());
            }
        }
        return selected;
    }

    private String evidenceJson(OpenAlexPersonMatcher.MatchDecision decision) {
        ObjectNode root = json.createObjectNode();
        root.put("source", "OPENALEX");
        root.put("status", decision.status);
        root.put("matchMethod", decision.matchMethod);
        root.put("matchingWorkCount", decision.matchingWorkCount);
        root.put("orcidMatch", decision.orcidMatch);
        root.put("orcidConflict", decision.orcidConflict);
        root.put("ussAffiliation", decision.ussAffiliation);
        ArrayNode works = root.putArray("works");
        for (OpenAlexPersonMatcher.WorkEvidence work : decision.works) {
            ObjectNode node = works.addObject();
            node.put("doi", work.normalizedDoi);
            if (work.researchOutputId != null) {
                node.put("researchOutputId", work.researchOutputId);
            }
            node.put("rawAuthorName", work.rawAuthorName);
            node.put("authorDisplayName", work.authorDisplayName);
            if (work.sourceOrder != null) {
                node.put("sourceOrder", work.sourceOrder);
            }
            node.put("authorPosition", work.authorPosition);
            node.put("nameCompatible", Boolean.TRUE.equals(work.nameCompatible));
            node.put("positionCode", work.positionCode);
            node.put("ussAffiliation", work.ussAffiliation);
            node.put("affiliationDisplay", work.affiliationDisplay);
            node.put("ror", work.ror);
            node.put("reasonCodes", String.join(",", work.reasonCodes));
        }
        return root.toString();
    }

    private void persistProgress(SyncRunState run) {
        try {
            syncRunRepository.requireOwnership(run);
            openAlexRepository.saveRunStats(run);
        } catch (SyncOwnershipLostException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warning("syncId=" + run.syncId + " OpenAlex heartbeat failed: " + ex.getMessage());
        }
    }

    private void throttle() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ValidationException("OPENALEX_INTERRUPTED", "OpenAlex sync was interrupted");
        }
    }
}
