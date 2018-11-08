package org.mskcc.kickoff.validator;

import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.util.function.Predicate;

public class BarcodeValidator implements Predicate<KickoffRequest> {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Override
    public boolean test(KickoffRequest kickoffRequest) {
        boolean valid = true;
        for (Sample sample : kickoffRequest.getValidNonPooledNormalSamples().values()) {
            if ((kickoffRequest.isExome() || kickoffRequest.isImpact()) && !sample.hasBarcode()) {
                Utils.setExitLater(true);
                String message = String.format("Unable to get barcode for %s AKA: %s", sample.getIgoId(), sample
                        .getCmoSampleId());
                DEV_LOGGER.error(message);
                PM_LOGGER.error(message);
                valid = false;

                //@TODO I think barcode ID should be checked but done as above to have same results as prod version
/*
                String barcodeId = sample.getProperties().get(Constants.BARCODE_ID);
                if (StringUtils.isEmpty(barcodeId) || Objects.equals(barcodeId, Constants.EMPTY))
                    logError(String.format("Unable to get barcode for %s AKA: %s", sample.getProperties().get
                    (Constants.IGO_ID), sample.getProperties().get(Constants.CMO_SAMPLE_ID))); //" there must be a
                    sample specific QC data record that I can search up from");
*/
            }
        }

        return valid;
    }
}
