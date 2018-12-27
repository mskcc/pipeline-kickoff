package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.user.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.PairingInfo;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.util.VeloxConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

public class VeloxPairingsRetriever {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final User user;
    private ErrorRepository errorRepository;

    public VeloxPairingsRetriever(User user, ErrorRepository errorRepository) {
        this.user = user;
        this.errorRepository = errorRepository;
    }

    public List<PairingInfo> retrieve(DataRecord dataRecord, KickoffRequest kickoffRequest, BiPredicate<Sample,
            Sample> pairingValidPredicate) {
        try {
            List<DataRecord> pairingRecords = Arrays.asList(dataRecord.getChildrenOfType(VeloxConstants.PAIRING_INFO,
                    user));

            DEV_LOGGER.info(String.format("Found %d pairing infos for request: %s", pairingRecords.size(),
                    kickoffRequest.getId()));

            List<PairingInfo> pairings = new ArrayList<>();
            for (DataRecord pairingRecord : pairingRecords) {
                try {
                    String tumorId = pairingRecord.getStringVal(VeloxConstants.TUMOR_ID, user);
                    String normalId = pairingRecord.getStringVal(VeloxConstants.NORMAL_ID, user);

                    DEV_LOGGER.info(String.format("Found pairing for request: %s. Tumor: \"%s\", normal: \"%s\"",
                            kickoffRequest.getId(), tumorId, normalId));

                    if (StringUtils.isEmpty(tumorId) || StringUtils.isEmpty(normalId))
                        continue;

                    Sample tumor = getSample(kickoffRequest, tumorId);
                    Sample normal = getSample(kickoffRequest, normalId);

                    if (pairingValidPredicate.test(tumor, normal))
                        pairings.add(new PairingInfo(tumor, normal));
                    else {
                        String error = String.format("Pairing between sample %s and sample %s is not valid", tumor
                                .getIgoId(), normal.getIgoId());
                        errorRepository.add(new GenerationError(error, ErrorCode.PAIRING_NOT_VALID));
                    }

                } catch (Exception e) {
                    throw new RuntimeException(String.format("Unable to retrieve pairing with Record id: %s ",
                            pairingRecord.getRecordId()));
                }
            }

            return pairings;
        } catch (Exception e) {
            throw new VeloxPairingsRetriever.PairingInfoRetrievalException(e);
        }
    }

    private Sample getSample(KickoffRequest kickoffRequest, String sampleId) {
        if (kickoffRequest.containsSample(sampleId)) {
            return kickoffRequest.getSample(sampleId);
        }

        DEV_LOGGER.warn(String.format("Sample: %s from pairing info is not part of given request: %s", sampleId,
                kickoffRequest.getId()));
        return Sample.getNotAvailableSample();
    }

    private class PairingInfoRetrievalException extends RuntimeException {
        public PairingInfoRetrievalException(Exception e) {
            super(e);
        }
    }
}
