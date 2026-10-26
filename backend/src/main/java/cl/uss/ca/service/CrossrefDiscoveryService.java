package cl.uss.ca.service;

import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.crossref.CrossrefClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Prepared missing-DOI discovery. Does not write PURE.
 * Candidates would land in {@code crossref_doi_candidate} after human confirmation
 * plus CorrectionService. Not started automatically from the Integrations UI.
 */
@ApplicationScoped
public class CrossrefDiscoveryService {

    @Inject
    CrossrefClient crossrefClient;

    @Inject
    @ConfigProperty(name = "crossref.discovery.enabled", defaultValue = "false")
    boolean discoveryEnabled;

    public void assertCanStart() {
        crossrefClient.assertConfigured();
        if (!discoveryEnabled) {
            throw new ValidationException(
                    "CROSSREF_DISCOVERY_DISABLED",
                    "Missing-DOI discovery is prepared and disabled. Set CROSSREF_DISCOVERY_ENABLED=true to enable."
            );
        }
    }
}
