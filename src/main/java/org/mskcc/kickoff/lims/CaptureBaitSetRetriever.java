package org.mskcc.kickoff.lims;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.util.Constants;

import java.util.Map;
import java.util.Objects;

class CaptureBaitSetRetriever {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);

    public String retrieve(String captureBaitSet, Map<String, String> baitSetToDesignFileMapping, String sampleId) {
        for (Map.Entry<String, String> baitSetToDesignFile : baitSetToDesignFileMapping.entrySet()) {
            String baitSet = baitSetToDesignFile.getKey();

            if (Objects.equals(captureBaitSet, baitSet)) {
                String designFileName = baitSetToDesignFile.getValue();
                String message = String.format("Overriding Capture Bait set for sample: %s, old value: %s, new value: %s", sampleId, baitSet, designFileName);
                DEV_LOGGER.info(message);
                PM_LOGGER.info(message);
                return designFileName;
            }
        }
        return captureBaitSet;
    }
}
