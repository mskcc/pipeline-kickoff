package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.user.User;
import org.mskcc.domain.external.ExternalSample;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffExternalSample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.ReadOnlyExternalSamplesRepository;
import org.mskcc.kickoff.retriever.SingleRequestRetriever;
import org.mskcc.kickoff.sampleset.SampleSetProxy;
import org.mskcc.util.Constants;
import org.mskcc.util.VeloxConstants;

import java.util.*;

import static org.mskcc.util.VeloxConstants.*;

public class VeloxSampleSetProxy implements SampleSetProxy {
    private static final org.apache.log4j.Logger DEV_LOGGER = org.apache.log4j.Logger.getLogger(Constants.DEV_LOGGER);

    private DataRecord sampleSetRecord;
    private User user;
    private SingleRequestRetriever singleRequestRetriever;
    private ReadOnlyExternalSamplesRepository externalSamplesRepository;

    public VeloxSampleSetProxy(DataRecord sampleSetRecord,
                               User user,
                               SingleRequestRetriever singleRequestRetriever,
                               ReadOnlyExternalSamplesRepository externalSamplesRepository) {
        this.sampleSetRecord = sampleSetRecord;
        this.user = user;
        this.singleRequestRetriever = singleRequestRetriever;
        this.externalSamplesRepository = externalSamplesRepository;
    }

    @Override
    public String getRecipe() throws Exception {
        return sampleSetRecord.getStringVal(VeloxConstants.RECIPE, user);
    }

    @Override
    public String getBaitVersion() throws Exception {
        return sampleSetRecord.getStringVal(VeloxConstants.BAIT_SET, user);
    }

    @Override
    public String getPrimaryRequestId() throws Exception {
        return sampleSetRecord.getStringVal(PRIME_REQUEST, user);
    }

    @Override
    public Collection<KickoffRequest> getRequests(ProcessingType processingType) throws Exception {
        List<KickoffRequest> kickoffRequests = new ArrayList<>();

        List<DataRecord> requestsDataRecords = Arrays.asList(sampleSetRecord.getChildrenOfType(REQUEST, user));
        for (DataRecord requestsDataRecord : requestsDataRecords) {
            String requestId = requestsDataRecord.getStringVal(REQUEST_ID, user);
            KickoffRequest kickoffRequest = singleRequestRetriever.retrieve(requestId, processingType);
            kickoffRequests.add(kickoffRequest);
        }

        return kickoffRequests;
    }

    @Override
    public Collection<Sample> getIgoSamples() throws Exception {
        List<Sample> samples = new LinkedList<>();

        try {
            List<DataRecord> sampleRecords = new LinkedList<>(Arrays.asList(sampleSetRecord.getChildrenOfType(SAMPLE,
                    user)));

            for (DataRecord sampleRecord : sampleRecords) {
                String sampleId = "";

                try {
                    sampleId = sampleRecord.getStringVal(SAMPLE_ID, user);
                    Sample sample = new Sample(sampleId);
                    String requestId = sampleRecord.getStringVal(REQUEST_ID, user);
                    sample.setRequestId(requestId);

                    samples.add(sample);
                } catch (Exception e) {
                    throw new RuntimeException(String.format("Error while retrieving IGO sample %s", sampleId), e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while retrieving IGO samples for sample set.", e);
        }

        List<Sample> igoSamples = samples;

        return igoSamples;
    }

    @Override
    public List<KickoffExternalSample> getExternalSamples() {
        List<KickoffExternalSample> externalSamples = new LinkedList<>();

        try {
            List<DataRecord> externalSampleRecords = new LinkedList<>(Arrays.asList(sampleSetRecord.getChildrenOfType
                    (EXTERNAL_SPECIMEN, user)));

            for (DataRecord externalSampleRecord : externalSampleRecords) {
                String externalId = "";
                try {
                    externalId = externalSampleRecord.getStringVal(EXTERNAL_ID, user);
                    ExternalSample externalSample = externalSamplesRepository.getByExternalId(externalId);

                    KickoffExternalSample kickoffExternalSample = convert(externalSample);
                    kickoffExternalSample.putRunIfAbsent(externalSample.getRunId());
                    externalSamples.add(kickoffExternalSample);
                } catch (Exception e) {
                    throw new RuntimeException(String.format("Error while retrieving external sample %s. Cause: %s",
                            externalId, e.getMessage()), e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while retrieving external samples for sample set.", e);
        }

        return externalSamples;
    }

    private KickoffExternalSample convert(ExternalSample externalSample) {
        KickoffExternalSample kickoffExternalSample = new KickoffExternalSample(externalSample.getCounter(),
                externalSample.getExternalId(), externalSample.getExternalPatientId(), externalSample.getFilePath(),
                externalSample.getRunId(), externalSample.getSampleClass(), externalSample.getSampleOrigin(),
                externalSample.getTumorNormal());

        kickoffExternalSample.setBaitVersion(externalSample.getBaitVersion());
        kickoffExternalSample.setCmoId(externalSample.getCmoId());
        kickoffExternalSample.setPatientCmoId(externalSample.getPatientCmoId());
        kickoffExternalSample.setPreservationType(externalSample.getPreservationType());
        kickoffExternalSample.setOncotreeCode(externalSample.getOncotreeCode());
        kickoffExternalSample.setSex(externalSample.getSex());
        kickoffExternalSample.setTissueSite(externalSample.getTissueSite());
        kickoffExternalSample.setCounter(externalSample.getCounter());
        kickoffExternalSample.setNucleidAcid(externalSample.getNucleidAcid());
        kickoffExternalSample.setSpecimenType(externalSample.getSpecimenType());

        DEV_LOGGER.info(String.format("Converted external sample %s to kickoff external sample", externalSample,
                kickoffExternalSample));

        return kickoffExternalSample;
    }
}
