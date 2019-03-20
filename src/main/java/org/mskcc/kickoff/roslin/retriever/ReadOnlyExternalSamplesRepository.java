package org.mskcc.kickoff.roslin.retriever;

import org.mskcc.domain.external.ExternalSample;

public interface ReadOnlyExternalSamplesRepository {
    ExternalSample getByExternalId(String externalId);
}
