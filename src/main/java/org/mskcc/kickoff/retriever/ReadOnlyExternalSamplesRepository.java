package org.mskcc.kickoff.retriever;

import org.mskcc.domain.external.ExternalSample;

import java.util.Collection;

public interface ReadOnlyExternalSamplesRepository {
    ExternalSample getByExternalId(String externalId);

    Collection<ExternalSample> get();

    Collection<ExternalSample> getByPatientCmoId(String patientCmoId);
}
