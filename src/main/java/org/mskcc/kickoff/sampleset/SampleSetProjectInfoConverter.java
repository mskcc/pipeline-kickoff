package org.mskcc.kickoff.sampleset;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.KickoffSampleSet;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.ConverterUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.util.Constants.ProjectInfo;

public class SampleSetProjectInfoConverter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    public Map<String, String> convert(KickoffSampleSet sampleSet) {
        Map<String, String> projectInfo = new LinkedHashMap<>();
        projectInfo.put(ProjectInfo.LAB_HEAD, getLabHead(sampleSet));
        projectInfo.put(ProjectInfo.LAB_HEAD_E_MAIL, getLabHeadEmail(sampleSet));
        projectInfo.put(ProjectInfo.PI_EMAIL, getPIEmail(sampleSet));
        projectInfo.put(ProjectInfo.CONTACT_NAME, getContactName(sampleSet));
        projectInfo.put(ProjectInfo.PI_FIRSTNAME, getPIFirstName(sampleSet));
        projectInfo.put(ProjectInfo.PI_LASTNAME, getPILastName(sampleSet));
        projectInfo.put(ProjectInfo.REQUESTOR, getRequestor(sampleSet));
        projectInfo.put(ProjectInfo.REQUESTOR_E_MAIL, getRequestorEmail(sampleSet));
        projectInfo.put(ProjectInfo.PLATFORM, getPlatform(sampleSet));
        projectInfo.put(ProjectInfo.IGO_PROJECT_ID, getIgoProjectId(sampleSet));
        projectInfo.put(ProjectInfo.FINAL_PROJECT_TITLE, getFinalProjectTitle(sampleSet));
        projectInfo.put(ProjectInfo.CMO_PROJECT_ID, getCmoProjectId(sampleSet));
        projectInfo.put(ProjectInfo.CMO_PROJECT_BRIEF, getCmoProjectBrief(sampleSet));
        projectInfo.put(ProjectInfo.PROJECT_MANAGER, getProjectManager(sampleSet));
        projectInfo.put(ProjectInfo.PROJECT_MANAGER_EMAIL, getProjectManagerEmail(sampleSet));
        projectInfo.put(ProjectInfo.README_INFO, getReadmeInfo(sampleSet));
        projectInfo.put(ProjectInfo.DATA_ANALYST, getDataAnalyst(sampleSet));
        projectInfo.put(ProjectInfo.DATA_ANALYST_EMAIL, getDataAnalystEmail(sampleSet));
        projectInfo.put(ProjectInfo.NUMBER_OF_SAMPLES, getNumberOfSamples(sampleSet));
        projectInfo.put(ProjectInfo.SPECIES, getSpecies(sampleSet));
        projectInfo.put(ProjectInfo.BIOINFORMATIC_REQUEST, getBioinformaticRequest(sampleSet));
        projectInfo.put(ProjectInfo.PROJECT_APPLICATIONS, getProjectApplications(sampleSet));
        projectInfo.put(ProjectInfo.MAIL_TO, getMailTo(sampleSet));
        projectInfo.put(ProjectInfo.INVESTIGATOR_EMAIL, getInvestigatorEmail(sampleSet));

        projectInfo.put(ProjectInfo.ASSAY, sampleSet.getBaitSet());

        setTumorType(projectInfo, sampleSet);

        setOptionalProjectProperty(projectInfo, sampleSet, ProjectInfo.DESIGN_FILE);
        setOptionalProjectProperty(projectInfo, sampleSet, ProjectInfo.SPIKEIN_DESIGN_FILE);

        return projectInfo;
    }

    private void setPropertyFromPrimaryRequest(KickoffSampleSet sampleSet, Map<String, String> projectInfo, String
            propertyName) {
        projectInfo.put(propertyName, getPropertyFromPrimaryRequest(sampleSet, propertyName));
    }

    private String getLabHead(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.LAB_HEAD);
    }

    private String getInvestigatorEmail(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.INVESTIGATOR_EMAIL);
    }

    private String getMailTo(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.MAIL_TO);
    }

    private String getLabHeadEmail(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.LAB_HEAD_E_MAIL);
    }

    private String getPIEmail(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.PI_EMAIL);
    }

    private String getContactName(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.CONTACT_NAME);
    }

    private String getPILastName(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.PI_LASTNAME);
    }

    private String getPIFirstName(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.PI_FIRSTNAME);
    }

    private String getProjectApplications(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.PROJECT_APPLICATIONS);
    }

    private String getRequestor(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.REQUESTOR);
    }

    private String getRequestorEmail(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.REQUESTOR_E_MAIL);
    }

    private String getPlatform(KickoffSampleSet sampleSet) {
        return sampleSet.getBaitSet();
    }

    private String getIgoProjectId(KickoffSampleSet sampleSet) {
        return sampleSet.getName();
    }

    private String getFinalProjectTitle(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.FINAL_PROJECT_TITLE);
    }

    private String getCmoProjectId(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.CMO_PROJECT_ID);
    }

    private void setTumorType(Map<String, String> projectInfo, KickoffSampleSet sampleSet) {
        Set<String> tumorTypes = sampleSet.getKickoffRequests().stream()
                .map(r -> r.getProjectInfo().get(ProjectInfo.TUMOR_TYPE))
                .collect(Collectors.toSet());

        if (tumorTypes.size() > 1)
            projectInfo.put(ProjectInfo.TUMOR_TYPE, org.mskcc.kickoff.util.Constants.MIXED);
        else if (hasTumorTypeFilledIn(sampleSet))
            setPropertyFromPrimaryRequest(sampleSet, projectInfo, ProjectInfo.TUMOR_TYPE);
        else
            DEV_LOGGER.warn(String.format("Primary request %s is not of type %s and tumor type is not available for " +
                            "it " +
                            "thus tumor type will not be available for sample set %s", sampleSet.getPrimaryRequestId(),
                    RequestType.IMPACT, sampleSet.getName()));
    }

    private boolean hasTumorTypeFilledIn(KickoffSampleSet sampleSet) {
        return sampleSet.getPrimaryRequest().isImpact() || sampleSet.getPrimaryRequest().isExome();
    }

    private String getPropertyFromPrimaryRequest(KickoffSampleSet sampleSet, String propertyName) {
        KickoffRequest primeKickoffRequest = sampleSet.getPrimaryRequest();

        if (!primeKickoffRequest.getProjectInfo().containsKey(propertyName)) {
            throw new PropertyInPrimaryRequestNotSetException(String.format("Primary request: %s of project: %s has " +
                    "no property: %s set", primeKickoffRequest.getId(), sampleSet.getName(), propertyName));
        }

        String value = primeKickoffRequest.getProjectInfo().get(propertyName);
        DEV_LOGGER.debug(String.format("Resolving sample set property %s from prime request %s with value %s",
                propertyName, primeKickoffRequest.getId(), value));

        return value;
    }

    private String getCmoProjectBrief(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.CMO_PROJECT_BRIEF);
    }

    private String getProjectManager(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.PROJECT_MANAGER);
    }

    private String getProjectManagerEmail(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.PROJECT_MANAGER_EMAIL);
    }

    private String getReadmeInfo(KickoffSampleSet sampleSet) {
        return ConverterUtils.getMergedPropertyValue(sampleSet, r -> r.getProjectInfo().get(ProjectInfo
                .README_INFO), ",");
    }

    private String getDataAnalyst(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.DATA_ANALYST);
    }

    private String getDataAnalystEmail(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.DATA_ANALYST_EMAIL);
    }

    private String getNumberOfSamples(KickoffSampleSet sampleSet) {
        List<KickoffRequest> nonEmpty = sampleSet.getKickoffRequests().stream()
                .filter(r -> !StringUtils.isEmpty(r.getProjectInfo().get(ProjectInfo.NUMBER_OF_SAMPLES)))
                .collect(Collectors.toList());

        if (nonEmpty.size() == 0)
            return "";

        int totalNumberOfSamples = nonEmpty.stream()
                .mapToInt(r -> Integer.valueOf(r.getProjectInfo().get(ProjectInfo.NUMBER_OF_SAMPLES)))
                .sum();

        totalNumberOfSamples += sampleSet.getExternalSamples().size();

        return String.valueOf(totalNumberOfSamples);
    }

    private String getSpecies(KickoffSampleSet sampleSet) {
        return ConverterUtils.getRequiredSameForAllProperty(sampleSet, r -> r.getProjectInfo().get(
                ProjectInfo.SPECIES), ProjectInfo.SPECIES);
    }

    private String getBioinformaticRequest(KickoffSampleSet sampleSet) {
        return getPropertyFromPrimaryRequest(sampleSet, ProjectInfo.BIOINFORMATIC_REQUEST);
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



