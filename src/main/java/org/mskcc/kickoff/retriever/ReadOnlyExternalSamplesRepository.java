package org.mskcc.kickoff.retriever;

import org.mskcc.domain.external.ExternalSample;

public interface ReadOnlyExternalSamplesRepository {
    ExternalSample getByExternalId(String externalId);
}
