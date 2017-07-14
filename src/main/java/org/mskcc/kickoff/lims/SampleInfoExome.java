package org.mskcc.kickoff.lims;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.velox.util.VeloxConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SampleInfoExome extends SampleInfoImpact {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    public SampleInfoExome(User apiUser, DataRecordManager drm, DataRecord rec, Request request, Sample sample) {
        super(apiUser, drm, rec, request, sample);
    }

    /*
            Capture Info Pulling
     */
    protected String optionallySetDefault(String currentVal, String defaultVal) {
        if (currentVal == null || currentVal.startsWith("#") || Objects.equals(currentVal, Constants.NULL)) {
            return defaultVal;
        }
        return currentVal;
    }

    @Override
    protected void getSpreadOutInfo(User apiUser, DataRecordManager drm, DataRecord rec, Request request, Sample sample) {
        // Spread out information available for IMPACT includes this.BARCODE_ID, this.BARCODE_INDEX
        // this.LIBRARY_YIELD this.LIBRARY_INPUT
        // this.CAPTURE_NAME this.CAPTURE_CONCENTRATION
        // this.CAPTURE_INPUT
        // TODO: Find IGO_ID of pool sample that is between
        getBarcodeInfo(drm, apiUser, request, sample);

        if (this.LIBRARY_INPUT == null || this.LIBRARY_INPUT.startsWith("#")) {
            this.LIBRARY_INPUT = Constants.EMPTY;
            grabLibInput(drm, rec, apiUser, false, sample.isPooledNormal());
            if (this.LIBRARY_INPUT.startsWith("#") && sample.isTransfer()) {
                grabLibInputFromPrevSamps(drm, rec, apiUser, sample.isPooledNormal());
            }
            if (this.LIBRARY_INPUT.startsWith("#")) {
                logWarning(String.format("Unable to find DNA Lib Protocol for Library Input method (sample %s)", this.CMO_SAMPLE_ID));
                this.LIBRARY_INPUT = "-2";
            }
        }

        // Capture concentration
        grabCaptureConc(rec, apiUser);

        if (this.LIBRARY_YIELD == null || this.LIBRARY_YIELD.startsWith("#")) {
            this.LIBRARY_YIELD = Constants.EMPTY;
        }
        // If Nimblgen Hybridization was created BEFORE May 5th, ASSUME
        // That Library Yield, and Capture INPUT is correct on Nimblgen data record
        // After that date we MUST get the information from elsewhere
        boolean afterDate = nimbAfterMay5(drm, rec, apiUser, "KAPAAgilentCaptureProtocol1");
        if (afterDate) {
            double libVol = getLibraryVolume(rec, apiUser, false, "DNALibraryPrepProtocol3");
            if (libVol <= 0) {
                libVol = getLibraryVolume(rec, apiUser, false, "DNALibraryPrepProtocol2");
            }
            if (libVol <= 0 && sample.isTransfer()) {
                libVol = getLibraryVolumeFromPrevSamps(rec, apiUser, "DNALibraryPrepProtocol3");
                if (libVol <= 0) {
                    libVol = getLibraryVolumeFromPrevSamps(rec, apiUser, "DNALibraryPrepProtocol2");
                }
            }

            double libConc = getLibraryConcentration(rec, drm, apiUser, sample.isTransfer(), "KAPAAgilentCaptureProtocol1");
            if (libVol <= 0 && (this.LIBRARY_YIELD == null || this.LIBRARY_YIELD.startsWith("#"))) {
                // No matter what I cannot get LIBRARY_YIELD
                this.LIBRARY_YIELD = "-2";
                logWarning("Unable to calculate Library Yield because I cannot retrieve either Lib Conc or Lib Output Vol");
            } else if (libConc > 0 && (this.LIBRARY_YIELD == null || this.LIBRARY_YIELD.startsWith("#"))) {
                this.LIBRARY_YIELD = String.valueOf(libVol * libConc);
            }
            processCaptureInfo(drm, rec, apiUser, libConc, true);
        } else {
            // Here I can just be simple like the good old times and pull stuff from the
            // Nimb protocol
            processCaptureInfo(drm, rec, apiUser, -1, false);
        }
        if (this.LIBRARY_YIELD.startsWith("#")) {
            this.LIBRARY_YIELD = "-2";
        }
    }

    private void processCaptureInfo(DataRecordManager drm, DataRecord rec, User apiUser, double libConc, boolean afterDate) {
        // This should set Lib Yield, Capt Input, Capt Name, Bait Set
        List<DataRecord> requestAsList = new ArrayList<>();

        List<List<Map<String, Object>>> kapa1FieldsList = null;
        List<List<Map<String, Object>>> kapa2FieldsList = null;
        try {
            requestAsList.add(rec);
            kapa1FieldsList = drm.getFieldsForDescendantsOfType(requestAsList, VeloxConstants.KAPA_AGILENT_CAPTURE_PROTOCOL_1, apiUser);
            kapa2FieldsList = drm.getFieldsForDescendantsOfType(requestAsList, VeloxConstants.KAPA_AGILENT_CAPTURE_PROTOCOL_2, apiUser);
        } catch (Exception e) {
            DEV_LOGGER.error("Exception thrown while retrieving information about process capture", e);
        }

        if (kapa1FieldsList == null || kapa1FieldsList.size() == 0 || kapa1FieldsList.get(0) == null || kapa1FieldsList.get(0).size() == 0) {
            this.CAPTURE_INPUT = this.CAPTURE_BAIT_SET = "#NoKAPACaptureProtocol1";
            logError(String.format("No Valid KAPACaptureProtocol for sample %s. The baitset, Capture Input, Library Yield will be unavailable. ", this.CMO_SAMPLE_ID));
        } else if (kapa2FieldsList == null || kapa2FieldsList.size() == 0 || kapa2FieldsList.get(0) == null || kapa2FieldsList.get(0).size() == 0) {
            this.CAPTURE_INPUT = this.CAPTURE_BAIT_SET = "#NoKAPACaptureProtocol2";
            logError(String.format("No Valid KAPACaptureProtocol2 for sample %s(Should be present). The baitset, Capture Input, Library Yield will be unavailable. ", this.CMO_SAMPLE_ID));
        } else {
            // there was only one sample in the reqeusts as list so you just need the first list of maps.
            List<Map<String, Object>> kapa2Fields = kapa2FieldsList.get(0);
            List<Map<String, Object>> kapa1Fields = kapa1FieldsList.get(0);

            String ExID = "#NONE";
            // For each KAPA 2, check valid. If valid grab kapa1 that matches it.
            // Using named block so I can break out of kapa2 loop
            kapa2Loop:
            {
                for (Map<String, Object> kapa2Map : kapa2Fields) {
                    if (!kapa2Map.containsKey(VeloxConstants.VALID) || kapa2Map.get(VeloxConstants.VALID) == null || !(Boolean) kapa2Map.get(VeloxConstants.VALID)) {
                        continue;
                    }
                    this.CAPTURE_BAIT_SET = (String) kapa2Map.get(VeloxConstants.AGILENT_CAPTURE_BAIT_SET);
                    ExID = (String) kapa2Map.get(VeloxConstants.EXPERIMENT_ID);
                    break kapa2Loop;
                }
            }
            // Now cycle through Kapa1 fields and find record with the same Experiment Id. Once you find the experiment ID matches
            kapa1Loop:
            {
                for (Map<String, Object> kapa1Map : kapa1Fields) {
                    // if kapa2 had valid in one of their records, match the kapa1 exp id with that
                    if (!ExID.equals("#NONE")) {
                        String Kapa1ExId = (String) kapa1Map.get(VeloxConstants.EXPERIMENT_ID);
                        if (!ExID.equals(Kapa1ExId)) {
                            continue;
                        }
                    } else {
                        // if KAPA2 isn't being used, make sure KAPA 1 is valid and then pull all the info you need from it.
                        if (!kapa1Map.containsKey(VeloxConstants.VALID) || kapa1Map.get(VeloxConstants.VALID) == null || !(Boolean) kapa1Map.get(VeloxConstants.VALID)) {
                            continue;
                        }
                        this.CAPTURE_BAIT_SET = (String) kapa1Map.get(VeloxConstants.AGILENT_CAPTURE_BAIT_SET);
                        if (afterDate) {
                            String message = "No KAPAAgilentCaptureProtocol2 had a valid key, but it should!";
                            logWarning(message);
                        }
                    }

                    if (afterDate) {
                        if (libConc > 0) {
                            Double volumeToUse;
                            volumeToUse = (Double) kapa1Map.get(VeloxConstants.ALIQ_1_SOURCE_VOLUME_TO_USE);
                            if (volumeToUse != null && volumeToUse > 0) {
                                this.CAPTURE_INPUT = String.valueOf(Math.round(volumeToUse * libConc));
                            }
                        } else {
                            // Ambiguous
                            this.CAPTURE_INPUT = "-2";
                        }
                    } else {
                        // Before Date -  grab vol and concentration to amek library yield
                        this.CAPTURE_INPUT = String.valueOf(kapa1Map.get(VeloxConstants.ALIQ_1_TARGET_MASS));
                        Double StartVol = (Double) kapa1Map.get(VeloxConstants.ALIQ_1_STARTING_VOLUME);
                        Double StartConc = (Double) kapa1Map.get(VeloxConstants.ALIQ_1_STARTING_CONCENTRATION);
                        if ((StartVol == null || StartConc == null || StartVol == 0.0 || StartConc == 0.0) && this.LIBRARY_YIELD.startsWith("#")) {
                            this.LIBRARY_YIELD = "#ExomeCALCERROR";
                        } else if (this.LIBRARY_YIELD.equals(Constants.EMPTY)) {
                            this.LIBRARY_YIELD = String.valueOf(Math.round(StartVol * StartConc));
                        }
                        break kapa1Loop;
                    }
                }
            }
        }

        if (this.CAPTURE_BAIT_SET.equals("51MB_Human")) {
            this.CAPTURE_BAIT_SET = "AgilentExon_51MB_b37_v3";
        } else if (this.CAPTURE_BAIT_SET.equals("51MB_Mouse")) {
            this.CAPTURE_BAIT_SET = "Agilent_MouseAllExonV1_mm10_v1";
        }
        this.BAIT_VERSION = this.CAPTURE_BAIT_SET;
    }

}
