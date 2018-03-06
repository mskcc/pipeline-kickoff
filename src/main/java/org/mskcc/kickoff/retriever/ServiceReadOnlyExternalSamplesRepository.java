package org.mskcc.kickoff.retriever;

import org.mskcc.domain.external.ExternalSample;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class ServiceReadOnlyExternalSamplesRepository implements ReadOnlyExternalSamplesRepository {
    private String externalSampleRestUrl;
    private String samplesEndpoint;
    private RestTemplate restTemplate;

    public ServiceReadOnlyExternalSamplesRepository(String externalSampleRestUrl, String samplesEndpoint,
                                                    RestTemplate restTemplate) {
        this.externalSampleRestUrl = externalSampleRestUrl;
        this.samplesEndpoint = samplesEndpoint;
        this.restTemplate = restTemplate;
    }

    @Override
    public ExternalSample getByExternalId(String externalId) {
        String url = String.format("%s/%s/%s", externalSampleRestUrl, samplesEndpoint, externalId);

        ResponseEntity<ExternalSample> externalSampleResponse = restTemplate.getForEntity(url, ExternalSample.class);

        ExternalSample externalSample = externalSampleResponse.getBody();

        if (externalSample == null)
            throw new RuntimeException(String.format("External Sample with id %s not found", externalId));

        return externalSample;
    }
}
