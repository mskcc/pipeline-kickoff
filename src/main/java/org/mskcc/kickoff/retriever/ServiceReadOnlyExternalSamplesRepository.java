package org.mskcc.kickoff.retriever;

import org.apache.log4j.Logger;
import org.mskcc.domain.external.ExternalSample;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class ServiceReadOnlyExternalSamplesRepository implements ReadOnlyExternalSamplesRepository {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private String externalSampleRestUrl;
    private String samplesEndpoint;
    private RestTemplate restTemplate;
    private ObserverManager observerManager;

    public ServiceReadOnlyExternalSamplesRepository(String externalSampleRestUrl,
                                                    String samplesEndpoint,
                                                    RestTemplate restTemplate,
                                                    ObserverManager observerManager) {
        this.externalSampleRestUrl = externalSampleRestUrl;
        this.samplesEndpoint = samplesEndpoint;
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
}
