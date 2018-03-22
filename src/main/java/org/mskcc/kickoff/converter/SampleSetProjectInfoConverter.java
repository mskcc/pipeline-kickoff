package org.mskcc.kickoff.converter;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.KickoffSampleSet;
import org.mskcc.kickoff.util.Constants;

import java.util.*;
import java.util.stream.Collectors;

public class SampleSetProjectInfoConverter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    public Map<String, String> convert(KickoffSampleSet sampleSet) {
        Map<String, String> projectInfo = new LinkedHashMap<>();

        projectInfo.put(Constants.ProjectInfo.LAB_HEAD, getLabHead(sampleSet));
        projectInfo.put(Constants.ProjectInfo.LAB_HEAD_E_MAIL, getLabHeadEmail(sampleSet));
        projectInfo.put(Constants.ProjectInfo.REQUESTOR, getRequestor(sampleSet));
        projectInfo.put(Constants.ProjectInfo.REQUESTOR_E_MAIL, getRequestorEmail(sampleSet));
        projectInfo.put(Constants.ProjectInfo.PLATFORM, getPlatform(sampleSet));
        projectInfo.put(Constants.ProjectInfo.ALTERNATE_EMAILS, getAlternateEmails(sampleSet));
        projectInfo.put(Constants.ProjectInfo.IGO_PROJECT_ID, getIgoProjectId(sampleSet));
        projectInfo.put(Constants.ProjectInfo.FINAL_PROJECT_TITLE, getFinalProjectTitle(sampleSet));
        projectInfo.put(Constants.ProjectInfo.CMO_PROJECT_ID, getCmoProjectId(sampleSet));
        projectInfo.put(Constants.ProjectInfo.CMO_PROJECT_BRIEF, getCmoProjectBrief(sampleSet));
        projectInfo.put(Constants.ProjectInfo.PROJECT_MANAGER, getProjectManager(sampleSet));
        projectInfo.put(Constants.ProjectInfo.PROJECT_MANAGER_EMAIL, getProjectManagerEmail(sampleSet));
        projectInfo.put(Constants.ProjectInfo.README_INFO, getReadmeInfo(sampleSet));
        projectInfo.put(Constants.ProjectInfo.DATA_ANALYST, getDataAnalyst(sampleSet));
        projectInfo.put(Constants.ProjectInfo.DATA_ANALYST_EMAIL, getDataAnalystEmail(sampleSet));
        projectInfo.put(Constants.ProjectInfo.NUMBER_OF_SAMPLES, getNumberOfSamples(sampleSet));
        projectInfo.put(Constants.ProjectInfo.SPECIES, getSpecies(sampleSet));
        projectInfo.put(Constants.ProjectInfo.BIOINFORMATIC_REQUEST, getBioinformaticRequest(sampleSet));

        projectInfo.put(Constants.ProjectInfo.ASSAY, sampleSet.getBaitSet());

        setOptionalProjectProperty(projectInfo, sampleSet, Constants.ProjectInfo.DESIGN_FILE);
        setOptionalProjectProperty(projectInfo, sampleSet, Constants.ProjectInfo.SPIKEIN_DESIGN_FILE);
//        setTumorType(projectInfo, sampleSet);

        return projectInfo;
    }

    private void setPropertyFromPrimaryRequest(KickoffSampleSet sampleSet, Map<String, String> projectInfo, String
            propertyName) {
        projectInfo.put(propertyName, getPropertyFromPrimaryRequest(sampleSet, propertyName));
    }

    private String getLabHead(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, Constants.ProjectInfo.LAB_HEAD);
    }

    private String getLabHeadEmail(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, Constants.ProjectInfo.LAB_HEAD_E_MAIL);
    }

    private String getRequestor(KickoffSampleSet sampleSet) {
        return ConverterUtils.getArbitraryPropertyValue(sampleSet, Constants.ProjectInfo.REQUESTOR);
    }

    private String getRequestorEmail(KickoffSampleSet sampleSet) {
        return ConverterUtils.getArbitraryPropertyValue(sampleSet, Constants.ProjectInfo.REQUESTOR_E_MAIL);
    }

    private String getPlatform(KickoffSampleSet sampleSet) {
        return sampleSet.getBaitSet();
    }

    private String getAlternateEmails(KickoffSampleSet sampleSet) {
        return ConverterUtils.getMergedPropertyValue(sampleSet, r -> r.getProjectInfo().get(Constants.ProjectInfo
                .ALTERNATE_EMAILS), ",");
    }

    private String getIgoProjectId(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, Constants.ProjectInfo.IGO_PROJECT_ID);
    }

    private String getFinalProjectTitle(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, Constants.ProjectInfo.FINAL_PROJECT_TITLE);
    }

    private String getCmoProjectId(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, Constants.ProjectInfo.CMO_PROJECT_ID);
    }

    private void setTumorType(Map<String, String> projectInfo, KickoffSampleSet sampleSet) {
        Set<String> tumorTypes = sampleSet.getKickoffRequests().stream()
                .map(r -> r.getProjectInfo().get(Constants.ProjectInfo.TUMOR_TYPE))
                .collect(Collectors.toSet());

        if (tumorTypes.size() > 1)
            projectInfo.put(Constants.ProjectInfo.TUMOR_TYPE, Constants.MIXED);
        else
            setPropertyFromPrimaryRequest(sampleSet, projectInfo, Constants.ProjectInfo.TUMOR_TYPE);
    }

    private String getPropertyFromPrimaryRequest(KickoffSampleSet sampleSet, String propertyName) {
        KickoffRequest primeKickoffRequest = sampleSet.getPrimaryRequest();

        if (!primeKickoffRequest.getProjectInfo().containsKey(propertyName))
            throw new PropertyInPrimaryRequestNotSetException(String.format("Primary request: %s of project: %s has " +
                    "no property: %s set", primeKickoffRequest.getId(), sampleSet.getName(), propertyName));

        return primeKickoffRequest.getProjectInfo().get(propertyName);
    }

    private String getCmoProjectBrief(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, Constants.ProjectInfo.CMO_PROJECT_BRIEF);
    }

    private String getProjectManager(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, Constants.ProjectInfo.PROJECT_MANAGER);
    }

    private String getProjectManagerEmail(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, Constants.ProjectInfo.PROJECT_MANAGER_EMAIL);
    }

    private String getReadmeInfo(KickoffSampleSet sampleSet) {
        return ConverterUtils.getMergedPropertyValue(sampleSet, r -> r.getProjectInfo().get(Constants.ProjectInfo
                .README_INFO), ",");
    }

    private String getDataAnalyst(KickoffSampleSet sampleSet) {
        return ConverterUtils.getMergedPropertyValue(sampleSet, r -> r.getProjectInfo().get(Constants.ProjectInfo
                .DATA_ANALYST), ",");
    }

    private String getDataAnalystEmail(KickoffSampleSet sampleSet) {
        return ConverterUtils.getMergedPropertyValue(sampleSet, r -> r.getProjectInfo().get(Constants.ProjectInfo
                .DATA_ANALYST_EMAIL), ",");
    }

    private String getNumberOfSamples(KickoffSampleSet sampleSet) {
        List<KickoffRequest> nonEmpty = sampleSet.getKickoffRequests().stream()
                .filter(r -> !StringUtils.isEmpty(r.getProjectInfo().get(Constants.ProjectInfo.NUMBER_OF_SAMPLES)))
                .collect(Collectors.toList());

        if (nonEmpty.size() == 0)
            return "";

        int totalNumberOfSamples = nonEmpty.stream()
                .mapToInt(r -> Integer.valueOf(r.getProjectInfo().get(Constants.ProjectInfo.NUMBER_OF_SAMPLES)))
                .sum();
        return String.valueOf(totalNumberOfSamples);
    }

    private String getSpecies(KickoffSampleSet sampleSet) {
        return ConverterUtils.getRequiredSameForAllProperty(sampleSet, r -> r.getProjectInfo().get(Constants
                .ProjectInfo.SPECIES), Constants.ProjectInfo.SPECIES);
    }

    private String getBioinformaticRequest(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, Constants.ProjectInfo.BIOINFORMATIC_REQUEST);
    }

    private void setOptionalProjectProperty(Map<String, String> projectInfo, KickoffSampleSet sampleSet, String
            propertyName) {
        Optional<String> optionalProperty = ConverterUtils.getOptionalProperty(sampleSet, r -> r.getProjectInfo().get
                (propertyName), propertyName);

        if (optionalProperty.isPresent())
            projectInfo.put(propertyName, optionalProperty.get());
    }

    public static class PrimaryRequestNotSetException extends RuntimeException {
        public PrimaryRequestNotSetException(String message) {
            super(message);
        }
    }

    public static class PropertyInPrimaryRequestNotSetException extends RuntimeException {
        public PropertyInPrimaryRequestNotSetException(String message) {
            super(message);
        }
    }
}



