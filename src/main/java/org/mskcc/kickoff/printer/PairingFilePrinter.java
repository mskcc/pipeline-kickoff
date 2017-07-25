package org.mskcc.kickoff.printer;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.Patient;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

import static org.mskcc.kickoff.config.Arguments.krista;
import static org.mskcc.kickoff.config.Arguments.shiny;
import static org.mskcc.kickoff.printer.OutputFilesPrinter.filesCreated;
import static org.mskcc.kickoff.util.Utils.sampleNormalization;

public class PairingFilePrinter implements FilePrinter  {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private Map<String, String> pairingInfo;

    @Override
    public void print(Request request) {
        String filename = String.format("%s/%s_sample_pairing.txt", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(request.getId()));;

        Set<String> normalCMOids = new HashSet<>();
        // Generate normal CMO IDs
        for (Sample sample : request.getAllValidSamples().values()) {
            if (sample.get(Constants.SAMPLE_CLASS).contains(Constants.NORMAL)) {
                normalCMOids.add(sample.get(Constants.CORRECTED_CMO_ID));
            }
        }

        Set<String> missingNormalsToBeAdded = new HashSet<>();
        if (Objects.equals(request.getRequestType(), Constants.EXOME)) {
            // Make a list of all the unmatched normals, so they can be added to the end of the pairing
            HashSet<String> normalsPairedWithThings = new HashSet<>(pairingInfo.values());
            missingNormalsToBeAdded = new HashSet<>(normalCMOids);
            missingNormalsToBeAdded.removeAll(normalsPairedWithThings);
        }
        try {
            //Done going through all igo ids, now print
            if (pairingInfo != null && pairingInfo.size() > 0) {
                File pairing_file = new File(filename);
                PrintWriter pW = new PrintWriter(new FileWriter(pairing_file, false), false);
                filesCreated.add(pairing_file);
                for (String tum : pairingInfo.keySet()) {
                    String norm = pairingInfo.get(tum);
                    pW.write(sampleNormalization(norm) + "\t" + sampleNormalization(tum) + "\n");
                }
                for (String unmatchedNorm : missingNormalsToBeAdded) {
                    String tum = "na";
                    pW.write(sampleNormalization(unmatchedNorm) + "\t" + sampleNormalization(tum) + "\n");
                }

                pW.close();

                if (shiny || krista) {
                    printPairingExcel(request, filename, pairingInfo, missingNormalsToBeAdded);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn("Exception thrown: ", e);
        }
    }

    @Override
    public boolean shouldPrint(Request request) {
        //@TODO to remove, added only to pas test which compares logs, move to print method after tests
        pairingInfo = getPairingInfo(request);
        return !(Utils.isExitLater() && !krista && !request.isInnovationProject() && !request.getRequestType().equals(Constants.OTHER) && !request.getRequestType().equals(Constants.RNASEQ))
                && (!request.getRequestType().equals(Constants.RNASEQ) && !request.getRequestType().equals(Constants.OTHER));
    }

    private Map<String, String> getPairingInfo(Request request) {
        // First, grab "PairingInfo" data records that contain the correct igo ids.
        // If those are unavailable, grab "SamplePairing" of the same thing.
        // Lastly, if that doesn't work, then try smart pairing (for impact, not exome)

        LinkedHashMap<String, String> tumorIgoToCmoId = new LinkedHashMap<>();
        LinkedHashMap<String, String> normalIgoToCmoId = new LinkedHashMap<>();

        // Make a list of IGO ids to give to printPairingFile script
        //ArrayList<String> igoIDs = new ArrayList<String>();
        for (Sample sample : request.getAllValidSamples().values()) {
            if (!sample.get("SAMPLE_CLASS").contains("Normal")) {
                tumorIgoToCmoId.put(sample.get("IGO_ID"), sample.get("CORRECTED_CMO_ID"));
            } else {
                normalIgoToCmoId.put(sample.get("IGO_ID"), sample.get("CORRECTED_CMO_ID"));
            }
        }

        // Generate Pool Normal Map
        Map<String, String> pair_info = grabPairingInfo(tumorIgoToCmoId, request);
        if (pair_info != null && pair_info.size() > 0) {
            return pair_info;
        }

        //Last, Do smart Pairing.
        if (!request.getPatients().isEmpty()) {
            String message = "Pairing records not found, trying smart Pairing";
            PM_LOGGER.info(message);
            DEV_LOGGER.info(message);
            pair_info = smartPairing(request);
            return pair_info;
        } else {
            PM_LOGGER.log(PmLogPriority.WARNING, "No Pairing File will be output.");
            DEV_LOGGER.warn("No Pairing File will be output.");
            return Collections.emptyMap();
        }
    }

    private Map<String, String> grabPairingInfo(Map<String, String> tumorIgoToCmoId, Request request) {
        // Grab all IGO IDs and normals from the list of data records
        Map<String, String> pairings = new LinkedHashMap<>();
        for (Sample sample : getValidTumorSamples(request)) {
            if (sample.getCmoSampleId().startsWith("CTRL-")) {
                String message = String.format("A CTRL- igo ID is listed as Tumor?? " + sample.getCmoSampleId());
                Utils.setExitLater(true);
                PM_LOGGER.error(message);
                DEV_LOGGER.error(message);
                continue;
            }

            Sample pairingSample = sample.getPairing();
            if(pairingSample == null)
                continue;

            String tumorIgoId = sample.getIgoId();
            String normalCmoId = getInitialNormalId(pairingSample);

            if(StringUtils.isEmpty(pairingSample.getIgoId()) && (StringUtils.isEmpty(pairingSample.getCmoSampleId()) || Objects.equals(pairingSample.getCmoSampleId(), Constants.UNDEFINED))) {
                if(Objects.equals(request.getRequestType(), Constants.EXOME))
                    normalCmoId = Constants.NA_LOWER_CASE;
                else continue;
            } else {
                if(!isSampleFromRequest(request, pairingSample)){
                    String message = String.format("Normal matching with this tumor is NOT a valid sample in this request: Tumor: %s Norm: %s. The normal will be changed to na.", tumorIgoToCmoId.get(tumorIgoId), normalCmoId);
                    PM_LOGGER.log(PmLogPriority.WARNING, message);
                    DEV_LOGGER.warn(message);
                    normalCmoId = Constants.NA_LOWER_CASE;
                } else {
                    normalCmoId = getNormalCmoId(request, pairingSample);
                }
            }

            if (pairings.keySet().contains(tumorIgoId)) {
                String message = String.format("Multiple pairing records for %s! This is not supposed to happen.", tumorIgoId);
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
                String message1 = String.format("Tumor is matched with two different normals. I have no idea how this happened! Tumor: %s Normal: %s", sample.getCmoSampleId(), normalCmoId);
                Utils.setExitLater(true);
                PM_LOGGER.error(message1);
                DEV_LOGGER.error(message1);
                continue;
            }

            pairings.put(sample.get(Constants.CORRECTED_CMO_ID), normalCmoId);
        }

        // checking to see if all exome records are empty!
        if (isPairingInfo(request) && Objects.equals(request.getRequestType(), Constants.EXOME)) {
            Set<String> normals = new HashSet<>(pairings.values());
            if (normals.size() == 1 && normals.contains(Constants.NA_LOWER_CASE)) {
                return Collections.emptyMap();
            }
        }

        if (isPairingInfo(request) && pairings.size() > 0) {
            Set<String> temp1 = new HashSet<>(tumorIgoToCmoId.values());
            Set<String> temp2 = new HashSet<>(pairings.keySet());
            temp1.removeAll(temp2);
            if (temp1.size() > 0) {
                String message = String.format("one or more pairing records was not found! %s", Arrays.toString(temp1.toArray()));
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
            }
        }

        return pairings;
    }

    private String getInitialNormalId(Sample pairingSample) {
        if(!Objects.equals(pairingSample.getCmoSampleId(), Constants.UNDEFINED))
            return pairingSample.getCmoSampleId();
        if(!StringUtils.isEmpty(pairingSample.getIgoId()))
            return pairingSample.getIgoId();
        return Constants.NA_LOWER_CASE;
    }

    private boolean isPairingInfo(Request request) {
        return request.getSamples().values().stream()
                .allMatch(s -> s.getPairing() == null || (s.getPairing() != null && Objects.equals(s.getPairing().getCmoSampleId(), Constants.UNDEFINED)));
    }

    private String getNormalCmoId(Request request, Sample pairingSample) {
        if(isPairingInfo(request))
            return request.getSample(pairingSample.getIgoId()).get(Constants.CORRECTED_CMO_ID);

        Optional<Sample> sample = request.getSampleByCorrectedCmoId(pairingSample.getCmoSampleId());
        if(sample.isPresent())
            return pairingSample.getCmoSampleId();
        return "";
    }

    private boolean isSampleFromRequest(Request request, Sample pairingSample) {
        if(isPairingInfo(request))
            return request.getSamples().containsKey(pairingSample.getIgoId());
        return request.getSampleByCorrectedCmoId(pairingSample.getCmoSampleId()).isPresent();
    }

    private String getNormal(Sample pairingSample) {
        return pairingSample == null || StringUtils.isEmpty(pairingSample.getIgoId()) ? Constants.NA_LOWER_CASE : pairingSample.getIgoId();
    }

    private Collection<Sample> getValidTumorSamples(Request request) {
        return request.getAllValidSamples(s -> s.isTumor()).values();
    }

    private Map<String, String> smartPairing(Request request) {
        // for each patient sample, go through the set of things and find out if they are tumor or normal.
        // for each tumor, match with the first normal.
        // no normal? put na
        // no tumor? don't add to pair_Info
        Map<String, String> pair_Info = new LinkedHashMap<>();
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
                    pair_Info.put(corrected_t, norm.get(Constants.CORRECTED_CMO_ID));
                } else {
                    // no normal, for now put NA
                    pair_Info.put(corrected_t, Constants.NA_LOWER_CASE);
                }
            }
        }

        return pair_Info;
    }

    private void printPairingExcel(Request request, String pairing_filename, Map<String, String> pair_Info, Set<String> missingNormalsToBeAdded) {
        new PairingXlsxPrinter(pairing_filename, pair_Info, missingNormalsToBeAdded).print(request);
    }

}
