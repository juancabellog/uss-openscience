package cl.uss.ca.sync;

import cl.uss.ca.api.dto.SyncModePolicyDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.repository.SyncRunRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class SyncModeAdvisor {

    @Inject
    SyncRunRepository syncRunRepository;

    public SyncModePolicyDto policy(String entity) {
        SyncEntityPolicy catalog = SyncEntityPolicy.ofEntity(entity);
        SyncRunDto latest = syncRunRepository.findLatest(catalog.entity());
        List<SyncRunDto> completedFull = syncRunRepository.findCompletedFullRuns(catalog.entity(), 50);
        SyncRunDto lastFinishedFull = syncRunRepository.findLatestFinishedFull(catalog.entity());
        return SyncModePolicy.evaluate(catalog, latest, completedFull, lastFinishedFull);
    }

    public SyncMode resolveStart(String entity, String modeRaw) {
        SyncEntityPolicy catalog = SyncEntityPolicy.ofEntity(entity);
        return SyncModePolicy.resolveStart(catalog, modeRaw, policy(catalog.entity()));
    }
}
