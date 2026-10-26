package cl.uss.ca.service;

import cl.uss.ca.api.dto.PureCorrectionResultDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.exception.ConflictException;
import cl.uss.ca.exception.ErrorLog;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.pure.HttpPureClient;
import cl.uss.ca.external.pure.PersonMapper;
import cl.uss.ca.external.pure.PureClient;
import cl.uss.ca.external.pure.ResearchOutputMapper;
import cl.uss.ca.external.pure.json.PersonItem;
import cl.uss.ca.external.pure.json.ResearchOutputItem;
import cl.uss.ca.external.pure.model.ResearchOutput;
import cl.uss.ca.external.pure.PurePerson;
import cl.uss.ca.repository.PersonSyncRepository;
import cl.uss.ca.repository.PureCorrectionRepository;
import cl.uss.ca.repository.ResearchOutputSyncRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.security.CurrentUserService;
import cl.uss.ca.sync.PureCorrectionTarget;
import cl.uss.ca.sync.SyncPresenceRepository;
import cl.uss.ca.sync.diff.AggregateOutcome;
import cl.uss.ca.sync.diff.PersonAggregateComparer;
import cl.uss.ca.sync.diff.PersonSnapshot;
import cl.uss.ca.sync.diff.ResearchOutputAggregateComparer;
import cl.uss.ca.sync.diff.ResearchOutputSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class PureCorrectionService {
    private static final Logger LOG = Logger.getLogger(PureCorrectionService.class.getName());
    public static final String ACTION = "PURE_CHILD_REMOVED";
    public static final String ACTION_LOCAL_RECONCILE = "PURE_LOCAL_RECONCILE";
    public static final String ACTION_ORCID = "ORCID_ASSIGN";
    public static final String SCOPE_ORCID = "ORCID";
    public static final String STATUS_REMOVED = "REMOVED";
    public static final String STATUS_ALREADY_REMOVED = "ALREADY_REMOVED";
    public static final String STATUS_LOCAL_FAILED = "REMOTE_SUCCESS_LOCAL_SYNC_FAILED";
    public static final String STATUS_LOCAL_RECONCILED = "LOCAL_RECONCILED";
    public static final String STATUS_REMOTE_CHANGED = "CONFLICT_REMOTE_CHANGED";
    public static final String STATUS_ORCID_UPDATED = "UPDATED";
    public static final String STATUS_ORCID_ALREADY_PRESENT = "ALREADY_PRESENT";
    public static final String EVIDENCE_OPENALEX = "OPENALEX";
    private static final String MANUAL_SYNC_ID = "pure-correction";

    @Inject
    PureClient pureClient;

    @Inject
    PersonMapper personMapper;

    @Inject
    ResearchOutputMapper researchOutputMapper;

    @Inject
    PersonAggregateComparer personComparer;

    @Inject
    ResearchOutputAggregateComparer outputComparer;

    @Inject
    PersonSyncRepository personSyncRepository;

    @Inject
    ResearchOutputSyncRepository researchOutputSyncRepository;

    @Inject
    SyncPresenceRepository presenceRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    PureCorrectionRepository correctionRepository;

    @Inject
    CurrentUserService currentUserService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PureCorrectionResultDto removeScopusIdentifier(long personId, long identifierPureId, String expectedValue) {
        assertCanCorrect(cl.uss.ca.auth.AppModule.PEOPLE);
        assertNoFullSync("PEOPLE");
        PureCorrectionTarget target = correctionRepository.loadPersonIdentifier(personId, identifierPureId);
        target.childPureId = identifierPureId;
        if (expectedValue != null && !expectedValue.isBlank()) {
            target.childValue = expectedValue.trim();
        }
        validateLocalScopus(target, expectedValue);
        return execute(
                "PEOPLE",
                "SCOPUS_IDENTIFIER",
                target,
                "identifiers",
                identifierPureId,
                expectedValue,
                this::isScopusIdentifier,
                this::reconcilePerson
        );
    }

    public PureCorrectionResultDto removeDoiVersion(long researchOutputId, long versionPureId, String expectedDoi) {
        assertCanCorrect(cl.uss.ca.auth.AppModule.RESEARCH_OUTPUTS);
        assertNoFullSync("RESEARCH_OUTPUT");
        PureCorrectionTarget target = correctionRepository.loadDoiVersion(researchOutputId, versionPureId);
        target.childPureId = versionPureId;
        if (expectedDoi != null && !expectedDoi.isBlank()) {
            target.childValue = expectedDoi.trim();
        }
        validateLocalDoi(target, expectedDoi);
        return execute(
                "RESEARCH_OUTPUT",
                "DOI_ELECTRONIC_VERSION",
                target,
                "electronicVersions",
                versionPureId,
                expectedDoi,
                this::isDoiElectronicVersion,
                this::reconcileResearchOutput
        );
    }

    private PureCorrectionResultDto execute(
            String entity,
            String scope,
            PureCorrectionTarget target,
            String collectionField,
            long childPureId,
            String expectedValue,
            ChildMatcher matcher,
            Reconciler reconciler
    ) {
        try {
            JsonNode current = getParent(entity, target.parentUuid);
            int index = findChildIndex(current, collectionField, childPureId, expectedValue, matcher);
            if (index < 0) {
                PureCorrectionResultDto already = result(STATUS_ALREADY_REMOVED, entity, scope, target);
                reconcileSafely(reconciler, current, target, already);
                audit(target, entity, scope, already.status,
                        STATUS_LOCAL_FAILED.equals(already.status) ? "LOCAL_SYNC_FAILED" : null,
                        STATUS_LOCAL_FAILED.equals(already.status) ? already.message : null);
                return already;
            }
            ObjectNode mutable = current.deepCopy();
            ((ArrayNode) mutable.get(collectionField)).remove(index);
            putParent(entity, target.parentUuid, mutable);
            JsonNode verified = getParent(entity, target.parentUuid);
            if (findChildIndex(verified, collectionField, childPureId, expectedValue, matcher) >= 0) {
                audit(target, entity, scope, "PURE_VERIFICATION_FAILED", "PURE_VERIFICATION_FAILED",
                        "Child still present after PUT");
                throw new ConflictException(
                        "PURE_VERIFICATION_FAILED",
                        "The removal could not be confirmed in PURE."
                );
            }
            PureCorrectionResultDto dto = result(STATUS_REMOVED, entity, scope, target);
            reconcileSafely(reconciler, verified, target, dto);
            audit(target, entity, scope, dto.status,
                    STATUS_LOCAL_FAILED.equals(dto.status) ? "LOCAL_SYNC_FAILED" : null,
                    STATUS_LOCAL_FAILED.equals(dto.status) ? dto.message : null);
            return dto;
        } catch (ConflictException | ValidationException ex) {
            audit(target, entity, scope, ex instanceof ConflictException
                    ? ((ConflictException) ex).getCode()
                    : ((ValidationException) ex).getCode(),
                    ex instanceof ConflictException
                            ? ((ConflictException) ex).getCode()
                            : ((ValidationException) ex).getCode(),
                    ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            audit(target, entity, scope, "FAILED", "PURE_UPDATE_FAILED", ex.getMessage());
            throw ex;
        } finally {
            HttpPureClient.clearRequestConcurrencyToken();
        }
    }

    public PureCorrectionResultDto reconcileLocalScopusIdentifier(long personId, long identifierPureId) {
        assertCanCorrect(cl.uss.ca.auth.AppModule.PEOPLE);
        assertNoFullSync("PEOPLE");
        PureCorrectionTarget target = correctionRepository.requirePerson(personId);
        target.childPureId = identifierPureId;
        return reconcileLocalOnly("PEOPLE", "SCOPUS_IDENTIFIER", target, this::reconcilePerson);
    }

    public PureCorrectionResultDto applyOrcid(
            long personId,
            String expectedUuid,
            String candidateOrcid,
            Long candidateId,
            String matchLevel,
            int score
    ) {
        return applyOrcid(personId, expectedUuid, candidateOrcid, candidateId, matchLevel, score, null, null);
    }

    public PureCorrectionResultDto applyOrcid(
            long personId,
            String expectedUuid,
            String candidateOrcid,
            Long candidateId,
            String matchLevel,
            int score,
            String evidenceSource,
            String evidenceRef
    ) {
        assertCanCorrect(cl.uss.ca.auth.AppModule.PEOPLE);
        assertNoFullSync("PEOPLE");
        String incomingOrcid = cl.uss.ca.domain.OrcidFormat.normalizeValidOrNull(candidateOrcid);
        if (incomingOrcid == null) {
            throw new ValidationException("ORCID_INVALID", "The candidate ORCID is empty");
        }
        PureCorrectionTarget target = correctionRepository.requirePerson(personId);
        if (target.parentUuid == null || target.parentUuid.isBlank()) {
            throw new ValidationException("PERSON_UUID_MISSING", "The person has no PURE UUID.");
        }
        if (expectedUuid != null && !expectedUuid.isBlank() && !expectedUuid.equals(target.parentUuid)) {
            throw new ValidationException("PERSON_UUID_MISMATCH", "The selected person does not match the expected UUID.");
        }
        target.childValue = incomingOrcid;
        target.extra = "source=" + (evidenceSource == null ? "" : evidenceSource)
                + ";evidenceRef=" + (evidenceRef == null ? "" : evidenceRef)
                + ";candidateId=" + candidateId
                + ";matchLevel=" + (matchLevel == null ? "" : matchLevel)
                + ";score=" + score
                + ";previousOrcid=";
        try {
            JsonNode current = getParent("PEOPLE", target.parentUuid);
            String remoteUuid = text(current, "uuid");
            if (remoteUuid == null || !target.parentUuid.equals(remoteUuid)) {
                throw new ValidationException("PERSON_UUID_MISMATCH", "The PURE person does not match the expected UUID.");
            }
            String remoteOrcid = text(current, "orcid");
            target.extra = target.extra + (remoteOrcid == null ? "" : remoteOrcid);
            String remoteNormalized = cl.uss.ca.domain.OrcidFormat.normalize(remoteOrcid);
            if (remoteNormalized != null) {
                if (incomingOrcid.equals(remoteNormalized)) {
                    PureCorrectionResultDto already = result(STATUS_ORCID_ALREADY_PRESENT, "PEOPLE", SCOPE_ORCID, target);
                    already.message = "The ORCID is already present in PURE.";
                    reconcileSafely(this::reconcilePerson, current, target, already);
                    auditWithAction(ACTION_ORCID, target, "PEOPLE", SCOPE_ORCID, already.status, null, already.message);
                    return already;
                }
                throw new ConflictException(
                        "ORCID_CONFLICT",
                        "PURE already has a different ORCID. The institutional ORCID was not overwritten."
                );
            }
            if (!(current instanceof ObjectNode)) {
                throw new ValidationException("PURE_PAYLOAD_INVALID", "The PURE person payload is not a JSON object.");
            }
            ObjectNode mutable = ((ObjectNode) current).deepCopy();
            mutable.put("orcid", incomingOrcid);
            putParent("PEOPLE", target.parentUuid, mutable);
            JsonNode verified = getParent("PEOPLE", target.parentUuid);
            String verifiedOrcid = cl.uss.ca.domain.OrcidFormat.normalize(text(verified, "orcid"));
            if (verifiedOrcid == null || !incomingOrcid.equals(verifiedOrcid)) {
                auditWithAction(ACTION_ORCID, target, "PEOPLE", SCOPE_ORCID, "PURE_VERIFICATION_FAILED",
                        "PURE_VERIFICATION_FAILED", "ORCID was not present after PUT");
                throw new ConflictException("PURE_VERIFICATION_FAILED", "The ORCID could not be confirmed in PURE.");
            }
            PureCorrectionResultDto dto = result(STATUS_ORCID_UPDATED, "PEOPLE", SCOPE_ORCID, target);
            dto.message = "The ORCID was added in PURE and the local replica was updated.";
            reconcileSafely(this::reconcilePerson, verified, target, dto);
            if (STATUS_LOCAL_FAILED.equals(dto.status)) {
                dto.message = "The ORCID was added in PURE, but the local replica could not be updated.";
            }
            auditWithAction(ACTION_ORCID, target, "PEOPLE", SCOPE_ORCID, dto.status,
                    STATUS_LOCAL_FAILED.equals(dto.status) ? "LOCAL_SYNC_FAILED" : null,
                    STATUS_LOCAL_FAILED.equals(dto.status) ? dto.message : null);
            return dto;
        } catch (ConflictException | ValidationException ex) {
            auditWithAction(ACTION_ORCID, target, "PEOPLE", SCOPE_ORCID,
                    ex instanceof ConflictException ? ((ConflictException) ex).getCode() : ((ValidationException) ex).getCode(),
                    ex instanceof ConflictException ? ((ConflictException) ex).getCode() : ((ValidationException) ex).getCode(),
                    ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            auditWithAction(ACTION_ORCID, target, "PEOPLE", SCOPE_ORCID, "FAILED", "PURE_UPDATE_FAILED", ex.getMessage());
            throw ex;
        } finally {
            HttpPureClient.clearRequestConcurrencyToken();
        }
    }

    public PureCorrectionResultDto reconcileLocalOrcid(long personId) {
        assertCanCorrect(cl.uss.ca.auth.AppModule.PEOPLE);
        assertNoFullSync("PEOPLE");
        return reconcileLocalOnly("PEOPLE", SCOPE_ORCID, correctionRepository.requirePerson(personId), this::reconcilePerson);
    }

    public PureCorrectionResultDto applyDataQuality(cl.uss.ca.quality.DataQualityPatch patch) {
        assertCanCorrect(cl.uss.ca.auth.AppModule.DATA_QUALITY);
        if (patch == null || patch.entity == null || patch.recordUuid == null) {
            throw new ValidationException("QUALITY_PATCH_INVALID", "The quality patch is incomplete.");
        }
        assertNoFullSync(patch.entity);
        String scope = patch.ruleCode == null ? "DATA_QUALITY" : patch.ruleCode;
        PureCorrectionTarget target = "PEOPLE".equals(patch.entity)
                ? correctionRepository.requirePerson(patch.localRecordId)
                : correctionRepository.requireResearchOutput(patch.localRecordId);
        target.childPureId = patch.childPureId;
        target.childValue = patch.expectedValue;
        target.extra = "findingId=" + patch.findingId + ";rule=" + patch.ruleCode
                + ";field=" + patch.field + ";issue=" + patch.issueKind;
        try {
            JsonNode current = getParent(patch.entity, target.parentUuid);
            String remoteUuid = text(current, "uuid");
            if (remoteUuid == null || !target.parentUuid.equals(remoteUuid)) {
                throw new ValidationException("UUID_MISMATCH", "The PURE record does not match the expected UUID.");
            }
            String remoteValue = cl.uss.ca.quality.DataQualityJsonPatch.readCurrent(current, patch);
            if (!valuesMatch(patch.expectedValue, remoteValue) && !"REMOVE_CONTRIBUTOR".equalsIgnoreCase(patch.field)) {
                PureCorrectionResultDto changed = result(STATUS_REMOTE_CHANGED, patch.entity, scope, target);
                changed.message = "El registro cambió en PURE. Revise nuevamente.";
                auditWithAction("DATA_QUALITY_PATCH", target, patch.entity, scope, changed.status,
                        STATUS_REMOTE_CHANGED, changed.message);
                return changed;
            }
            if ("REMOVE_CONTRIBUTOR".equalsIgnoreCase(patch.field)) {
                for (Long childId : patch.removeChildPureIds) {
                    if (findChildIndex(current, "contributors", childId, null, (item, expected) -> true) < 0) {
                        PureCorrectionResultDto changed = result(STATUS_REMOTE_CHANGED, patch.entity, scope, target);
                        changed.message = "El registro cambió en PURE. Revise nuevamente.";
                        auditWithAction("DATA_QUALITY_PATCH", target, patch.entity, scope, changed.status,
                                STATUS_REMOTE_CHANGED, changed.message);
                        return changed;
                    }
                }
            }
            if (!(current instanceof ObjectNode)) {
                throw new ValidationException("PURE_PAYLOAD_INVALID", "The PURE payload is not a JSON object.");
            }
            ObjectNode mutable = ((ObjectNode) current).deepCopy();
            cl.uss.ca.quality.DataQualityJsonPatch.apply(mutable, patch);
            putParent(patch.entity, target.parentUuid, mutable);
            JsonNode verified = getParent(patch.entity, target.parentUuid);
            if ("REMOVE_CONTRIBUTOR".equalsIgnoreCase(patch.field)) {
                for (Long childId : patch.removeChildPureIds) {
                    if (findChildIndex(verified, "contributors", childId, null, (item, expected) -> true) >= 0) {
                        throw new ConflictException("PURE_VERIFICATION_FAILED", "The change could not be confirmed in PURE.");
                    }
                }
            } else {
                String verifiedValue = cl.uss.ca.quality.DataQualityJsonPatch.readCurrent(verified, patch);
                if (!valuesMatch(patch.newValue, verifiedValue)) {
                    throw new ConflictException("PURE_VERIFICATION_FAILED", "The change could not be confirmed in PURE.");
                }
            }
            PureCorrectionResultDto dto = result("UPDATED", patch.entity, scope, target);
            dto.message = "The correction was applied in PURE and the local replica was updated.";
            dto.childValue = patch.newValue;
            Reconciler reconciler = "PEOPLE".equals(patch.entity) ? this::reconcilePerson : this::reconcileResearchOutput;
            reconcileSafely(reconciler, verified, target, dto);
            if (STATUS_LOCAL_FAILED.equals(dto.status)) {
                dto.message = "PURE fue actualizado, pero la copia local quedó pendiente.";
            }
            auditWithAction("DATA_QUALITY_PATCH", target, patch.entity, scope, dto.status,
                    STATUS_LOCAL_FAILED.equals(dto.status) ? "LOCAL_SYNC_FAILED" : null,
                    STATUS_LOCAL_FAILED.equals(dto.status) ? dto.message : null);
            return dto;
        } catch (ConflictException | ValidationException ex) {
            auditWithAction("DATA_QUALITY_PATCH", target, patch.entity, scope,
                    ex instanceof ConflictException ? ((ConflictException) ex).getCode() : ((ValidationException) ex).getCode(),
                    ex instanceof ConflictException ? ((ConflictException) ex).getCode() : ((ValidationException) ex).getCode(),
                    ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            auditWithAction("DATA_QUALITY_PATCH", target, patch.entity, scope, "FAILED", "PURE_UPDATE_FAILED", ex.getMessage());
            throw ex;
        } finally {
            HttpPureClient.clearRequestConcurrencyToken();
        }
    }

    public PureCorrectionResultDto reconcileLocalRecord(String entity, long localRecordId) {
        assertCanCorrect("PEOPLE".equals(entity) ? cl.uss.ca.auth.AppModule.PEOPLE : cl.uss.ca.auth.AppModule.RESEARCH_OUTPUTS);
        assertNoFullSync(entity);
        PureCorrectionTarget target = "PEOPLE".equals(entity)
                ? correctionRepository.requirePerson(localRecordId)
                : correctionRepository.requireResearchOutput(localRecordId);
        Reconciler reconciler = "PEOPLE".equals(entity) ? this::reconcilePerson : this::reconcileResearchOutput;
        return reconcileLocalOnly(entity, "DATA_QUALITY", target, reconciler);
    }

    public PureCorrectionResultDto reconcileLocalDoiVersion(long researchOutputId, long versionPureId) {
        assertCanCorrect(cl.uss.ca.auth.AppModule.RESEARCH_OUTPUTS);
        assertNoFullSync("RESEARCH_OUTPUT");
        PureCorrectionTarget target = correctionRepository.requireResearchOutput(researchOutputId);
        target.childPureId = versionPureId;
        return reconcileLocalOnly("RESEARCH_OUTPUT", "DOI_ELECTRONIC_VERSION", target, this::reconcileResearchOutput);
    }

    private PureCorrectionResultDto reconcileLocalOnly(
            String entity,
            String scope,
            PureCorrectionTarget target,
            Reconciler reconciler
    ) {
        try {
            JsonNode current = getParent(entity, target.parentUuid);
            PureCorrectionResultDto dto = result(STATUS_LOCAL_RECONCILED, entity, scope, target);
            dto.message = "The local replica was updated successfully.";
            try {
                reconciler.reconcile(current, target);
                auditWithAction(ACTION_LOCAL_RECONCILE, target, entity, scope, dto.status, null, null);
                return dto;
            } catch (Exception ex) {
                ErrorLog.unexpected(LOG, "Local-only reconcile failed uuid=" + target.parentUuid, ex);
                dto.status = STATUS_LOCAL_FAILED;
                dto.message = "The record was removed from PURE, but the local replica could not be updated.";
                auditWithAction(ACTION_LOCAL_RECONCILE, target, entity, scope, dto.status, "LOCAL_SYNC_FAILED", ex.getMessage());
                return dto;
            }
        } catch (ConflictException | ValidationException ex) {
            String code = ex instanceof ConflictException
                    ? ((ConflictException) ex).getCode()
                    : ((ValidationException) ex).getCode();
            auditWithAction(ACTION_LOCAL_RECONCILE, target, entity, scope, code, code, ex.getMessage());
            throw ex;
        } finally {
            HttpPureClient.clearRequestConcurrencyToken();
        }
    }

    private JsonNode getParent(String entity, String uuid) {
        return "PEOPLE".equals(entity) ? pureClient.getPerson(uuid) : pureClient.getResearchOutput(uuid);
    }

    private void putParent(String entity, String uuid, JsonNode body) {
        if ("PEOPLE".equals(entity)) {
            pureClient.putPerson(uuid, body);
        } else {
            pureClient.putResearchOutput(uuid, body);
        }
    }

    private int findChildIndex(
            JsonNode parent,
            String collectionField,
            long childPureId,
            String expectedValue,
            ChildMatcher matcher
    ) {
        JsonNode collection = parent == null ? null : parent.get(collectionField);
        if (collection == null || !collection.isArray()) {
            return -1;
        }
        for (int i = 0; i < collection.size(); i++) {
            JsonNode item = collection.get(i);
            if (item == null || item.path("pureId").asLong(Long.MIN_VALUE) != childPureId) {
                continue;
            }
            if (!matcher.matches(item, expectedValue)) {
                throw new ValidationException(
                        "CHILD_MISMATCH",
                        "The selected PURE child does not match the expected value."
                );
            }
            return i;
        }
        return -1;
    }

    private boolean isScopusIdentifier(JsonNode item, String expectedValue) {
        String value = firstNonBlank(text(item, "value"), text(item, "id"));
        if (expectedValue != null && !expectedValue.isBlank()
                && (value == null || !expectedValue.trim().equals(value.trim()))) {
            return false;
        }
        String type = identifierType(item);
        return type == null || type.toLowerCase(Locale.ROOT).contains("scopus");
    }

    private boolean isDoiElectronicVersion(JsonNode item, String expectedValue) {
        String discriminator = text(item, "typeDiscriminator");
        if (discriminator != null && !"DoiElectronicVersion".equals(discriminator)) {
            return false;
        }
        String doi = text(item, "doi");
        return expectedValue == null || expectedValue.isBlank()
                || (doi != null && expectedValue.trim().equals(doi.trim()));
    }

    private String identifierType(JsonNode item) {
        JsonNode type = item.get("type");
        if (type == null || type.isNull()) {
            return text(item, "typeDiscriminator");
        }
        JsonNode term = type.get("term");
        if (term != null && term.isObject()) {
            String preferred = firstNonBlank(text(term, "es_CL"), text(term, "en_US"), text(term, "en_GB"));
            if (preferred != null) {
                return preferred;
            }
        }
        return firstNonBlank(text(type, "uri"), text(item, "typeDiscriminator"));
    }

    private void reconcileSafely(
            Reconciler reconciler,
            JsonNode verified,
            PureCorrectionTarget target,
            PureCorrectionResultDto dto
    ) {
        try {
            reconciler.reconcile(verified, target);
        } catch (Exception ex) {
            ErrorLog.unexpected(LOG, "Local reconcile failed after PURE correction uuid=" + target.parentUuid, ex);
            dto.status = STATUS_LOCAL_FAILED;
            dto.message = "The record was removed from PURE, but the local replica could not be updated.";
        }
    }

    private void reconcilePerson(JsonNode verified, PureCorrectionTarget target) throws Exception {
        PersonItem item = objectMapper.convertValue(verified, PersonItem.class);
        PurePerson incoming = personMapper.mapearPersona(item);
        try (Connection connection = personSyncRepository.begin()) {
            PersonSnapshot existing = personSyncRepository
                    .loadSnapshots(connection, List.of(target.parentUuid))
                    .get(target.parentUuid);
            AggregateOutcome outcome = personComparer.compare(incoming, existing, true);
            Long id = personSyncRepository.apply(connection, incoming, outcome, true);
            personSyncRepository.stampOrcidFirstSeen(
                    connection,
                    id,
                    existing == null ? null : existing.orcid,
                    existing == null ? null : existing.orcidFirstSeenAt,
                    incoming.getOrcid()
            );
            presenceRepository.markPersonSeen(connection, target.parentUuid, MANUAL_SYNC_ID, outcome.touchChangeDetected());
            connection.commit();
        }
    }

    private void reconcileResearchOutput(JsonNode verified, PureCorrectionTarget target) throws Exception {
        ResearchOutputItem item = objectMapper.convertValue(verified, ResearchOutputItem.class);
        ResearchOutput incoming = researchOutputMapper.mapear(item);
        try (Connection connection = researchOutputSyncRepository.begin()) {
            ResearchOutputSnapshot existing = researchOutputSyncRepository
                    .loadSnapshots(connection, List.of(target.parentUuid))
                    .get(target.parentUuid);
            AggregateOutcome outcome = outputComparer.compare(incoming, existing);
            researchOutputSyncRepository.apply(connection, incoming, outcome);
            presenceRepository.markResearchOutputSeen(
                    connection,
                    target.parentUuid,
                    MANUAL_SYNC_ID,
                    outcome.touchChangeDetected()
            );
            connection.commit();
        }
    }

    private void validateLocalScopus(PureCorrectionTarget target, String expectedValue) {
        if (target.parentUuid == null || target.parentUuid.isBlank()) {
            throw new ValidationException("PERSON_UUID_MISSING", "The person has no PURE UUID.");
        }
        if (target.childType != null && !target.childType.toLowerCase(Locale.ROOT).contains("scopus")) {
            throw new ValidationException("CHILD_MISMATCH", "The selected identifier is not a Scopus ID.");
        }
        if (expectedValue != null && !expectedValue.isBlank()
                && target.childValue != null
                && !expectedValue.trim().equals(target.childValue.trim())) {
            throw new ValidationException("CHILD_MISMATCH", "The selected identifier does not match the expected Scopus ID.");
        }
    }

    private void validateLocalDoi(PureCorrectionTarget target, String expectedDoi) {
        if (target.parentUuid == null || target.parentUuid.isBlank()) {
            throw new ValidationException("RESEARCH_OUTPUT_UUID_MISSING", "The research output has no PURE UUID.");
        }
        if (target.childType != null && !"DoiElectronicVersion".equals(target.childType)) {
            throw new ValidationException("CHILD_MISMATCH", "The selected version is not a DoiElectronicVersion.");
        }
        if (expectedDoi != null && !expectedDoi.isBlank()
                && target.childValue != null
                && !expectedDoi.trim().equals(target.childValue.trim())) {
            throw new ValidationException("CHILD_MISMATCH", "The selected version does not match the expected DOI.");
        }
    }

    private void assertCanCorrect(cl.uss.ca.auth.AppModule module) {
        pureClient.assertConfigured();
        currentUserService.require(module, cl.uss.ca.auth.AppPermission.ADMINISTER);
    }

    private void assertNoFullSync(String entity) {
        for (SyncRunDto run : syncRunRepository.findRunning()) {
            if (entity.equals(PureSyncService.normalizeEntity(run.entity))) {
                throw new ConflictException(
                        "SYNC_IN_PROGRESS",
                        "A PURE synchronization for this entity is already running.",
                        entity,
                        run.syncId,
                        run.startedAt,
                        run.startedBy
                );
            }
        }
    }

    private void audit(
            PureCorrectionTarget target,
            String entity,
            String scope,
            String status,
            String errorCode,
            String errorMessage
    ) {
        auditWithAction(ACTION, target, entity, scope, status, errorCode, errorMessage);
    }

    private void auditWithAction(
            String action,
            PureCorrectionTarget target,
            String entity,
            String scope,
            String status,
            String errorCode,
            String errorMessage
    ) {
        try {
            correctionRepository.recordAudit(
                    currentUserService.current().id,
                    entity,
                    scope,
                    action,
                    status,
                    target,
                    errorCode,
                    errorMessage
            );
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Unable to persist correction audit", ex);
        }
    }

    private static PureCorrectionResultDto result(
            String status,
            String entity,
            String scope,
            PureCorrectionTarget target
    ) {
        PureCorrectionResultDto dto = new PureCorrectionResultDto();
        dto.status = status;
        dto.entity = entity;
        dto.scope = scope;
        dto.localParentId = target.localParentId;
        dto.parentUuid = target.parentUuid;
        dto.childPureId = target.childPureId;
        dto.childValue = target.childValue;
        dto.message = STATUS_ALREADY_REMOVED.equals(status)
                ? "The record was no longer present in PURE. The local replica was updated."
                : "The record was removed from PURE and the local replica was updated.";
        return dto;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean valuesMatch(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return actual == null || actual.isBlank();
        }
        return actual != null && expected.equals(actual);
    }

    @FunctionalInterface
    private interface ChildMatcher {
        boolean matches(JsonNode item, String expectedValue);
    }

    @FunctionalInterface
    private interface Reconciler {
        void reconcile(JsonNode verified, PureCorrectionTarget target) throws Exception;
    }
}
