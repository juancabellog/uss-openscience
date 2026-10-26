package cl.uss.ca.quality.consistency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ExternalMetadataCapabilityRegistry {
    private final List<ExternalEvidenceProvider> providers = new ArrayList<>();

    public ExternalMetadataCapabilityRegistry() {
    }

    @Inject
    void bindProviders(Instance<ExternalEvidenceProvider> instances) {
        providers.clear();
        if (instances == null) {
            return;
        }
        for (ExternalEvidenceProvider provider : instances) {
            providers.add(provider);
        }
    }

    public static ExternalMetadataCapabilityRegistry of(List<ExternalEvidenceProvider> explicit) {
        ExternalMetadataCapabilityRegistry registry = new ExternalMetadataCapabilityRegistry();
        if (explicit != null) {
            registry.providers.addAll(explicit);
        }
        return registry;
    }

    public List<ExternalEvidenceProvider> providers() {
        return List.copyOf(providers);
    }

    public List<ExternalEvidenceProvider> available() {
        List<ExternalEvidenceProvider> items = new ArrayList<>();
        for (ExternalEvidenceProvider provider : providers) {
            if (provider.available()) {
                items.add(provider);
            }
        }
        return items;
    }

    public Set<ConsistencyField> capabilities(String source) {
        for (ExternalEvidenceProvider provider : providers) {
            if (provider.source().equals(source)) {
                return provider.available() ? provider.capabilities() : Set.of();
            }
        }
        return Set.of();
    }

    public boolean supports(String source, ConsistencyField field) {
        return capabilities(source).contains(field);
    }

    public Map<String, Map<String, SourceSnapshot>> loadByDois(List<String> normalizedDois) {
        Map<String, Map<String, SourceSnapshot>> bySource = new LinkedHashMap<>();
        if (normalizedDois == null || normalizedDois.isEmpty()) {
            return bySource;
        }
        for (ExternalEvidenceProvider provider : available()) {
            bySource.put(provider.source(), provider.loadByDois(normalizedDois));
        }
        return bySource;
    }

    public List<SourceSnapshot> evidenceFor(Map<String, Map<String, SourceSnapshot>> loaded, String doi) {
        List<SourceSnapshot> items = new ArrayList<>();
        if (loaded == null || doi == null) {
            return items;
        }
        for (Map<String, SourceSnapshot> byDoi : loaded.values()) {
            SourceSnapshot snapshot = byDoi.get(doi);
            if (snapshot != null) {
                items.add(snapshot);
            }
        }
        return items;
    }
}
