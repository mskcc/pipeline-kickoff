package org.mskcc.kickoff.retriever;

import org.apache.log4j.Logger;
import org.mskcc.domain.external.ExternalSample;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ServiceReadOnlyExternalSamplesRepository implements ReadOnlyExternalSamplesRepository {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private String externalSampleRestUrl;
    private String samplesEndpoint;
    private String patientCmoEndpoint;
    private RestTemplate restTemplate;
    private ObserverManager observerManager;

    public ServiceReadOnlyExternalSamplesRepository(String externalSampleRestUrl,
                                                    String samplesEndpoint,
                                                    String patientCmoEndpoint,
                                                    RestTemplate restTemplate,
                                                    ObserverManager observerManager) {
        this.externalSampleRestUrl = externalSampleRestUrl;
        this.samplesEndpoint = samplesEndpoint;
        this.patientCmoEndpoint = patientCmoEndpoint;
        this.restTemplate = restTemplate;
        this.observerManager = observerManager;
    }

    @Override
    public ExternalSample getByExternalId(String externalId) {
        String url = String.format("%s/%s/%s", externalSampleRestUrl, samplesEndpoint, externalId);

        ResponseEntity<ExternalSample> externalSampleResponse = restTemplate.getForEntity(url, ExternalSample.class);

        ExternalSample externalSample = externalSampleResponse.getBody();

        DEV_LOGGER.info(String.format("Retrieved External sample: %s", externalSample));

        if (externalSampleResponse.getStatusCode() != HttpStatus.OK || externalSample == null) {
            String message = String.format("External Sample with id %s not found", externalId);
            GenerationError error = new GenerationError(message, ErrorCode.EXTERNAL_SAMPLE_NOT_FOUND);
            observerManager.notifyObserversOfError(ManifestFile.MAPPING, error);

            throw new RuntimeException(message);
        }

        return externalSample;
    }

    @Override
    public Collection<ExternalSample> getByPatientCmoId(String patientCmoId) {
        String url = String.format("%s/%s/%s/%s", externalSampleRestUrl, samplesEndpoint, patientCmoEndpoint,
                patientCmoId);

        ResponseEntity<Collection<ExternalSample>> externalSampleResponse = restTemplate.exchange(url, HttpMethod.GET,
                null, new ParameterizedTypeReference<Collection<ExternalSample>>() {
                });

        Collection<ExternalSample> externalSamples = externalSampleResponse.getBody();

        DEV_LOGGER.info(String.format("Retrieved %d External samples for CMO patient id: %s", externalSamples.size(),
                patientCmoId));

        if (externalSampleResponse.getStatusCode() != HttpStatus.OK) {
            String message = String.format("Error while retrieving External Samples for CMO patient id %s not found",
                    patientCmoId);
            return Collections.emptyList();
        }

        return externalSamples;
    }

    @Override
    public Collection<ExternalSample> get() {
        String url = String.format("%s/%s", externalSampleRestUrl, samplesEndpoint);

        ResponseEntity<List<ExternalSample>> externalSamplesResponse = restTemplate.exchange(url, HttpMethod.GET,
                null, new ParameterizedTypeReference<List<ExternalSample>>() {
                });

        if (externalSamplesResponse.getStatusCode() != HttpStatus.OK) {
            DEV_LOGGER.warn("External Samples not found");
        }

        List<ExternalSample> externalSamples = externalSamplesResponse.getBody();

        return externalSamples;
    }
}
