package org.mskcc.kickoff.lims;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.util.VeloxConstants;

import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.util.*;

import static org.mskcc.util.VeloxConstants.*;

public class SampleInfoExome extends SampleInfoImpact {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private Map<String, String> baitSetToDesignFileMapping;

    public SampleInfoExome(User apiUser, DataRecordManager drm, DataRecord rec, KickoffRequest kickoffRequest, Sample
            sample, LocalDateTime kapaProtocolStartDate) {
        super(apiUser, drm, rec, kickoffRequest, sample, kapaProtocolStartDate);
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
    protected void getSpreadOutInfo(User apiUser, DataRecordManager drm, DataRecord rec, KickoffRequest
            kickoffRequest, Sample sample) {
        // Spread out information available for IMPACT includes this.BARCODE_ID, this.BARCODE_INDEX
        // this.LIBRARY_YIELD this.LIBRARY_INPUT
        // this.CAPTURE_NAME this.CAPTURE_CONCENTRATION
        // this.CAPTURE_INPUT
        // TODO: Find IGO_ID of pool sample that is between
        getBarcodeInfo(drm, apiUser, kickoffRequest, sample);

        if (this.LIBRARY_INPUT == null || this.LIBRARY_INPUT.startsWith("#")) {
            this.LIBRARY_INPUT = Constants.EMPTY;
            grabLibInput(drm, rec, apiUser, false, sample.isPooledNormal());
            if (this.LIBRARY_INPUT.startsWith("#") && sample.isTransfer()) {
                grabLibInputFromPrevSamps(drm, rec, apiUser, sample.isPooledNormal());
            }
            if (this.LIBRARY_INPUT.startsWith("#")) {
                logWarning(String.format("Unable to find DNA Lib Protocol for Library Input method (sample %s)", this
                        .CMO_SAMPLE_ID));
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
        boolean afterDate = nimbAfterMay5(drm, rec, apiUser, VeloxConstants.KAPA_AGILENT_CAPTURE_PROTOCOL_1);
        if (afterDate) {
            double libVol = getLibraryVolume(rec, apiUser, false, VeloxConstants.DNA_LIBRARY_PREP_PROTOCOL_3);
            if (libVol <= 0) {
                libVol = getLibraryVolume(rec, apiUser, false, VeloxConstants.DNA_LIBRARY_PREP_PROTOCOL_2);
            }
            if (libVol <= 0 && sample.isTransfer()) {
                libVol = getLibraryVolumeFromPrevSamps(rec, apiUser, VeloxConstants.DNA_LIBRARY_PREP_PROTOCOL_3);
                if (libVol <= 0) {
                    libVol = getLibraryVolumeFromPrevSamps(rec, apiUser, VeloxConstants.DNA_LIBRARY_PREP_PROTOCOL_2);
                }
            }

            double libConc = getLibraryConcentration(rec, drm, apiUser, sample.isTransfer(), VeloxConstants
                    .KAPA_AGILENT_CAPTURE_PROTOCOL_1);
            if (libVol <= 0 && (this.LIBRARY_YIELD == null || this.LIBRARY_YIELD.startsWith("#"))) {
                // No matter what I cannot get LIBRARY_YIELD
                this.LIBRARY_YIELD = "-2";
                logWarning("Unable to calculate Library Yield because I cannot retrieve either Lib Conc or Lib Output" +
                        " Vol");
            } else if (libConc > 0 && (this.LIBRARY_YIELD == null || this.LIBRARY_YIELD.startsWith("#"))) {
                this.LIBRARY_YIELD = String.valueOf(libVol * libConc);
            }
            processCaptureInfo(drm, rec, apiUser, libConc, true, kickoffRequest, sample);
        } else {
            // Here I can just be simple like the good old times and pull stuff from the
            // Nimb protocol
            processCaptureInfo(drm, rec, apiUser, -1, false, kickoffRequest, sample);
        }
        if (this.LIBRARY_YIELD.startsWith("#")) {
            this.LIBRARY_YIELD = "-2";
        }
    }

    private void processCaptureInfo(DataRecordManager drm, DataRecord rec, User apiUser, double libConc, boolean
            afterDate, KickoffRequest kickoffRequest, Sample sample) {

        if (shouldValidateKapaProtocols(kickoffRequest)) {
            // This should set Lib Yield, Capt Input, Capt Name, Bait Set
            List<DataRecord> requestAsList = new ArrayList<>();

            List<List<Map<String, Object>>> kapa1FieldsList = new ArrayList<>();
            List<List<Map<String, Object>>> kapa2FieldsList = new ArrayList<>();
            try {
                requestAsList.add(rec);
                kapa1FieldsList = drm.getFieldsForDescendantsOfType(requestAsList, KAPA_AGILENT_CAPTURE_PROTOCOL_1,
                        apiUser);
                kapa2FieldsList = drm.getFieldsForDescendantsOfType(requestAsList, KAPA_AGILENT_CAPTURE_PROTOCOL_2,
                        apiUser);
            } catch (Exception e) {
                DEV_LOGGER.error("Exception thrown while retrieving information about process capture", e);
            }

            if (sample.isTransfer()) {
                if (!hasKapaAgilentCaptureProtocol(kapa1FieldsList)) {
                    kapa1FieldsList = getKapaAgilentCaptureProtocolFromPrevSamps(
                            drm, rec, apiUser, KAPA_AGILENT_CAPTURE_PROTOCOL_1);
                }
                if (!hasKapaAgilentCaptureProtocol(kapa2FieldsList)) {
                    kapa2FieldsList = getKapaAgilentCaptureProtocolFromPrevSamps(
                            drm, rec, apiUser, KAPA_AGILENT_CAPTURE_PROTOCOL_2);
                }
                String msg = String.format("Sample <%s> is a transfer from another request, " +
                                "fetched <%d> kapa1 protocol(s), <%d> kapa2 protocol(s) from ancestor sample(s).",
                        sample.getIgoId(), kapa1FieldsList.size(), kapa2FieldsList.size());
                DEV_LOGGER.info(msg);
            }

            if (!hasKapaAgilentCaptureProtocol(kapa1FieldsList)) {
                this.CAPTURE_INPUT = this.CAPTURE_BAIT_SET = Constants.NoKAPACaptureProtocol1;
                String message = String.format("No Valid KAPACaptureProtocol1 for sample %s (Should be present). " +
                        "The baitset, Capture Input, Library Yield will be unavailable. ", this.CMO_SAMPLE_ID);
                PM_LOGGER.log(Level.WARN, message);
                DEV_LOGGER.log(Level.WARN, message);
            } else if (!hasKapaAgilentCaptureProtocol(kapa2FieldsList)) {
                this.CAPTURE_INPUT = this.CAPTURE_BAIT_SET = Constants.NoKAPACaptureProtocol2;
                String message = String.format("No Valid KAPACaptureProtocol2 for sample %s (Should be present). " +
                        "The baitset, Capture Input, Library Yield will be unavailable. ", this.CMO_SAMPLE_ID);
                PM_LOGGER.log(Level.WARN, message);
                DEV_LOGGER.log(Level.WARN, message);
            } else {
                // there was only one sample in the requests as list so you just need the first list of maps.
                List<Map<String, Object>> kapa2Fields = kapa2FieldsList.get(0);
                List<Map<String, Object>> kapa1Fields = kapa1FieldsList.get(0);

                String ExID = "#NONE";
                // For each KAPA 2, check valid. If valid grab kapa1 that matches it.
                // Using named block so I can break out of kapa2 loop
                kapa2Loop:
                {
                    for (Map<String, Object> kapa2Map : kapa2Fields) {
                        if (!kapa2Map.containsKey(VALID) || kapa2Map.get(VALID) == null || !(Boolean) kapa2Map.get
                                (VALID)) {
                            continue;
                        }
                        this.CAPTURE_BAIT_SET = (String) kapa2Map.get(AGILENT_CAPTURE_BAIT_SET);
                        ExID = (String) kapa2Map.get(EXPERIMENT_ID);
                        break kapa2Loop;
                    }
                }
                // Now cycle through Kapa1 fields and find record with the same Experiment Id. Once you find the
                // experiment ID matches
                kapa1Loop:
                {
                    for (Map<String, Object> kapa1Map : kapa1Fields) {
                        // if kapa2 had valid in one of their records, match the kapa1 exp id with that
                        if (!ExID.equals("#NONE")) {
                            String Kapa1ExId = (String) kapa1Map.get(EXPERIMENT_ID);
                            if (!ExID.equals(Kapa1ExId)) {
                                continue;
                            }
                        } else {
                            // if KAPA2 isn't being used, make sure KAPA 1 is valid and then pull all the info you need
                            // from it.
                            if (!kapa1Map.containsKey(VALID) || kapa1Map.get(VALID) == null || !(Boolean) kapa1Map.get
                                    (VALID)) {
                                continue;
                            }
                            this.CAPTURE_BAIT_SET = (String) kapa1Map.get(AGILENT_CAPTURE_BAIT_SET);
                            if (afterDate) {
                                String message = "No KAPAAgilentCaptureProtocol2 had a valid key, but it should!";
                                DEV_LOGGER.info(message);
                            }
                        }

                        if (afterDate) {
                            if (libConc > 0) {
                                Double volumeToUse;
                                volumeToUse = (Double) kapa1Map.get(ALIQ_1_SOURCE_VOLUME_TO_USE);
                                if (volumeToUse != null && volumeToUse > 0) {
                                    this.CAPTURE_INPUT = String.valueOf(Math.round(volumeToUse * libConc));
                                }
                            } else {
                                // Ambiguous
                                this.CAPTURE_INPUT = "-2";
                            }
                        } else {
                            // Before Date -  grab vol and concentration to amek library yield
                            this.CAPTURE_INPUT = String.valueOf(kapa1Map.get(ALIQ_1_TARGET_MASS));
                            Double StartVol = (Double) kapa1Map.get(ALIQ_1_STARTING_VOLUME);
                            Double StartConc = (Double) kapa1Map.get(ALIQ_1_STARTING_CONCENTRATION);
                            if ((StartVol == null || StartConc == null || StartVol == 0.0 || StartConc == 0.0) && this
                                    .LIBRARY_YIELD.startsWith("#")) {
                                this.LIBRARY_YIELD = "#ExomeCALCERROR";
                            } else if (this.LIBRARY_YIELD.equals(Constants.EMPTY)) {
                                this.LIBRARY_YIELD = String.valueOf(Math.round(StartVol * StartConc));
                            }
                            break kapa1Loop;
                        }
                    }
                }
            }
        }

        this.CAPTURE_BAIT_SET = getCaptureBaitSet(drm, apiUser);
        this.BAIT_VERSION = this.CAPTURE_BAIT_SET;
    }

    private List<List<Map<String, Object>>> getKapaAgilentCaptureProtocolFromPrevSamps(DataRecordManager drm, DataRecord sampleRec, User apiUser, String kapaProtocolVersion) {
        Set<List<Map<String, Object>>> kapaFieldsSet = new HashSet<>();
        try {
            // use Ancestors instead of parents
            List<DataRecord> ancestors = sampleRec.getAncestorsOfType(VeloxConstants.SAMPLE, apiUser);
            List<List<Map<String, Object>>> kapaFieldsList = drm.getFieldsForDescendantsOfType(ancestors, kapaProtocolVersion, apiUser);
            for (List<Map<String, Object>> list: kapaFieldsList) {
                if (list != null && list.size() != 0) {
                    kapaFieldsSet.add(list);
                }
            }
        } catch (RemoteException | ServerException e) {
            String msg = "Exception thrown while retrieving Kapa Agilent Capture Protocol from ancestor samples:";
            DEV_LOGGER.error(msg, e);
            PM_LOGGER.error(msg, e);
        }

        return new ArrayList<>(kapaFieldsSet);
    }

    private boolean shouldValidateKapaProtocols(KickoffRequest kickoffRequest) {
        return kickoffRequest.getCreationDate() == null || kickoffRequest.getCreationDate().isAfter
                (kapaProtocolStartDate);
    }

    private boolean hasKapaAgilentCaptureProtocol(List<List<Map<String, Object>>> kapaFieldsList) {
        if (kapaFieldsList.size() == 0 || kapaFieldsList.get(0) == null || kapaFieldsList.get(0).size() == 0) {
            return false;
        } else {
            return true;
        }
    }

    private String getCaptureBaitSet(DataRecordManager dataRecordManager, User apiUser) {
        try {
            if (baitSetToDesignFileMapping == null)
                baitSetToDesignFileMapping = getBaitSetToDesignFile(dataRecordManager, apiUser);

            CaptureBaitSetRetriever captureBaitSetRetriever = new CaptureBaitSetRetriever();
            return captureBaitSetRetriever.retrieve(this.CAPTURE_BAIT_SET, baitSetToDesignFileMapping, this.IGO_ID);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Unable to retrieve Bait set to design file name mapping. Original Capture " +
                    "Bait set: %s will be used", this.CAPTURE_BAIT_SET), e);
        }
        return this.CAPTURE_BAIT_SET;
    }

    private Map<String, String> getBaitSetToDesignFile(DataRecordManager dataRecordManager, User user) {
        Map<String, String> baitSetToDesignFile = new HashMap<>();

        try {
            List<DataRecord> baitSetToDesignFileMappings = dataRecordManager.queryDataRecords(Constants
                    .BAIT_SET_TO_DESIGN_FILE_MAPPING, null, user);

            for (DataRecord baitSetToDesignFileMapping : baitSetToDesignFileMappings) {
                String baitSet = baitSetToDesignFileMapping.getStringVal(Constants.BAIT_SET, user);
                String designFileName = baitSetToDesignFileMapping.getStringVal(Constants.DESIGN_FILE_NAME, user);
                baitSetToDesignFile.put(baitSet, designFileName);
            }
        } catch (Exception e) {
            String msg = String.format("Unable to retrieve Bait set to design file name mapping from record: %s",
                    Constants.BAIT_SET_TO_DESIGN_FILE_MAPPING);
            DEV_LOGGER.warn(msg, e);
        }

        return baitSetToDesignFile;
    }
}