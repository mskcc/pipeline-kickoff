package org.mskcc.kickoff.generator;

import org.apache.log4j.Logger;
import org.mskcc.domain.Patient;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;

import java.util.*;

public class SmartPairingRetriever {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    public Map<String, String> retrieve(KickoffRequest request) {
        if (request.getPatients().isEmpty()) {
            PM_LOGGER.log(PmLogPriority.WARNING, "No Pairing File will be output.");
            DEV_LOGGER.warn("No Pairing File will be output.");
            return Collections.emptyMap();
        }

        // for each patient sample, go through the set of things and find out if they are tumor or normal.
        // for each tumor, match with the first normal.
        // no normal? put na
        // no tumor? don't add to pair_Info
        Map<String, String> smartPairings = new LinkedHashMap<>();
        for (Patient patient : request.getPatients().values()) {
            List<Sample> tumors = new ArrayList<>();
            List<String> allNormals = new ArrayList<>();
            // populating the tumors and normals for this patient
            for (Sample sample : patient.getSamples()) {
                Map<String, String> samp = sample.getProperties();
                if (samp.getOrDefault(Constants.SAMPLE_CLASS, "").contains(Constants.NORMAL)) {
                    allNormals.add(sample.getIgoId());
                } else {
                    tumors.add(sample);
                }
            }

            //go through each tumor, add it to the pair_Info with a normal if possible
            for (Sample tumor : tumors) {
                Map<String, String> tum = tumor.getProperties();
                String corrected_t = tum.get(Constants.CORRECTED_CMO_ID);
                String PRESERVATION = tum.get(Constants.SPECIMEN_PRESERVATION_TYPE);
                String SITE = tum.get(Constants.TISSUE_SITE);
                if (allNormals.size() > 0) {
                    List<String> normals = new ArrayList<>(allNormals);
                    List<String> preservationNormals = new ArrayList<>();
                    List<String> useTheseNormals = new ArrayList<>();
                    // cycle through and find out if you have normal with same tumor sample preservation type
                    for (String n : allNormals) {
                        Map<String, String> norm = request.getAllValidSamples().get(n).getProperties();
                        if (norm.get(Constants.SPECIMEN_PRESERVATION_TYPE).equals(PRESERVATION)) {
                            preservationNormals.add(n);
                        }
                    }
                    // Now if any match preservation type, use those normals to continue matching
                    if (preservationNormals.size() > 0) {
                        //print("Number that matched preservation: " + String.valueOf(preservationNormals.size()));
                        normals = new ArrayList<>(preservationNormals);
                        //print("New Normals to choose from " + normals);
                    }
                    // go through and see if any of the normals have the same tissue site.
                    for (String n : normals) {
                        Map<String, String> norm = request.getAllValidSamples().get(n).getProperties();
                        if (norm.get(Constants.TISSUE_SITE).equals(SITE)) {
                            useTheseNormals.add(n);
                        }
                    }
                    // If there are more than one, just pick the first.
                    String n;
                    if (useTheseNormals.size() > 0) {
                        n = useTheseNormals.get(0);
                    } else {
                        n = normals.get(0);
                    }
                    Map<String, String> norm = request.getAllValidSamples().get(n).getProperties();
                    smartPairings.put(corrected_t, norm.get(Constants.CORRECTED_CMO_ID));
                } else {
                    // no normal, for now put NA
                    smartPairings.put(corrected_t, Constants.NA_LOWER_CASE);
                }
            }
        }

        return smartPairings;
    }
}
