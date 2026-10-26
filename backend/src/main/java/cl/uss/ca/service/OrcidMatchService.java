package cl.uss.ca.service;

import cl.uss.ca.api.dto.OrcidWorkDto;
import cl.uss.ca.api.dto.OrcidWorkExternalIdDto;
import cl.uss.ca.api.dto.OrcidWorkGroupDto;
import cl.uss.ca.api.dto.PersonOrcidWorksDto;
import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.domain.OrcidExternalIdNormalizer;
import cl.uss.ca.repository.OrcidWorkRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class OrcidMatchService {

    @Inject
    OrcidWorkRepository orcidWorkRepository;

    /**
     * PURE Research Output matches an ORCID work group, not an individual assertion.
     * First version uses group-level self DOI only. ISSN part-of is ignored.
     */
    public void apply(PersonOrcidWorksDto dto, String personUuid) {
        List<String> pureDois = orcidWorkRepository.listPersonPureDois(personUuid);
        Map<String, Integer> pureCounts = new HashMap<>();
        for (String raw : pureDois) {
            String normalized = DoiNormalizer.normalize(raw);
            if (normalized != null) {
                pureCounts.merge(normalized, 1, Integer::sum);
            }
        }
        Set<String> orcidDois = new HashSet<>();
        int matched = 0;
        int orcidOnly = 0;
        int noDoi = 0;
        int ambiguous = 0;
        for (OrcidWorkGroupDto group : dto.groups) {
            if (Boolean.FALSE.equals(group.activeInOrcid)) {
                group.matchStatus = "INACTIVE";
                continue;
            }
            Set<String> groupDois = doisOf(group);
            if (groupDois.isEmpty()) {
                group.matchStatus = "NO_DOI";
                noDoi++;
                continue;
            }
            orcidDois.addAll(groupDois);
            int matches = 0;
            for (String doi : groupDois) {
                matches += pureCounts.getOrDefault(doi, 0);
            }
            if (matches == 0) {
                group.matchStatus = "ORCID_ONLY";
                orcidOnly++;
            } else if (matches == 1) {
                group.matchStatus = "MATCHED_BY_DOI";
                matched++;
            } else {
                group.matchStatus = "AMBIGUOUS";
                ambiguous++;
            }
            for (OrcidWorkDto assertion : group.assertions) {
                assertion.matchStatus = group.matchStatus;
            }
        }
        int pureOnly = 0;
        for (Map.Entry<String, Integer> entry : pureCounts.entrySet()) {
            if (!orcidDois.contains(entry.getKey())) {
                pureOnly += entry.getValue();
            }
        }
        dto.matchedByDoi = matched;
        dto.orcidOnly = orcidOnly;
        dto.noDoi = noDoi;
        dto.ambiguous = ambiguous;
        dto.pureOnly = pureOnly;
    }

    private static Set<String> doisOf(OrcidWorkGroupDto group) {
        Set<String> dois = new HashSet<>();
        String display = DoiNormalizer.normalize(group.doi);
        if (display != null) {
            dois.add(display);
        }
        addSelfDois(dois, group.externalIds);
        return dois;
    }

    private static void addSelfDois(Set<String> dois, List<OrcidWorkExternalIdDto> ids) {
        if (ids == null) {
            return;
        }
        for (OrcidWorkExternalIdDto id : ids) {
            if (id.type == null || !"doi".equalsIgnoreCase(id.type)) {
                continue;
            }
            if (!OrcidExternalIdNormalizer.isSelf(id.relationship)) {
                continue;
            }
            String normalized = id.normalizedValue != null
                    ? DoiNormalizer.normalize(id.normalizedValue)
                    : DoiNormalizer.normalize(id.value);
            if (normalized != null) {
                dois.add(normalized);
            }
        }
    }
}
