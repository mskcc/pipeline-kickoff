package org.mskcc.kickoff.converter;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.*;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.PairingInfo;
import org.mskcc.kickoff.domain.SampleSet;
import org.mskcc.kickoff.process.ForcedProcessingType;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.util.CommonUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SampleSetToRequestConverter {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private SampleSetProjectInfoConverter sampleSetProjectInfoConverter;

    public SampleSetToRequestConverter(SampleSetProjectInfoConverter sampleSetProjectInfoConverter) {
        this.sampleSetProjectInfoConverter = sampleSetProjectInfoConverter;
    }

    public KickoffRequest convert(SampleSet sampleSet) {
        KickoffRequest kickoffRequest = new KickoffRequest(sampleSet.getName(), getProcessingType(sampleSet));

        if (hasRequests(sampleSet)) {
            setRequests(kickoffRequest, sampleSet);
            setProcessingType(kickoffRequest, sampleSet);
            setSamples(kickoffRequest, sampleSet);
            setPools(kickoffRequest, sampleSet);
            setLibTypes(kickoffRequest, sampleSet);
            setStrand(kickoffRequest, sampleSet);
            setRequestType(kickoffRequest, sampleSet);
            setReadMe(kickoffRequest, sampleSet);
            setMannualDemux(kickoffRequest, sampleSet);
            setRecipe(kickoffRequest, sampleSet);
            setBicAutorunnable(kickoffRequest, sampleSet);
            setReadmeInfo(kickoffRequest, sampleSet);
            setRunNumbers(kickoffRequest, sampleSet);
            setExtraReadMe(kickoffRequest, sampleSet);
            setSpecies(kickoffRequest, sampleSet);
            setRunIdList(kickoffRequest, sampleSet);
            setProjectInvestigators(kickoffRequest, sampleSet);
            setInvest(kickoffRequest, sampleSet);
            setAmplificationType(kickoffRequest, sampleSet);
            setBaitVersion(kickoffRequest, sampleSet);
            setPatients(kickoffRequest, sampleSet);

            setProjectInfo(kickoffRequest, sampleSet);
        }

        return kickoffRequest;
    }

    private void setRequests(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.addRequests(sampleSet.getRequests());
    }

    private boolean hasRequests(SampleSet sampleSet) {
        DEV_LOGGER.info(String.format("Found %d requests for SampleSet: %s [%s]", sampleSet.getRequests().size(), sampleSet.getName(), getRequestsList(sampleSet)));

        return sampleSet.getRequests().size() > 0;
    }

    private String getRequestsList(SampleSet sampleSet) {
        return sampleSet.getRequests().stream().map(Request::getId).collect(Collectors.joining(","));
    }

    private void setPatients(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        Patient.resetGroupCounter();
        List<Patient> allRequestsPatients = sampleSet.getRequests().stream().flatMap(r -> r.getPatients().values().stream()).collect(Collectors.toList());

        for (Patient originalPatient : allRequestsPatients) {
            Patient patient = kickoffRequest.putPatientIfAbsent(originalPatient.getPatientId());
            patient.addSamples(originalPatient.getSamples());
        }

        DEV_LOGGER.info(String.format("Sample set: %s has %d patients [%s]", sampleSet.getName(), kickoffRequest.getPatients().size(), Utils.getJoinedCollection(kickoffRequest.getPatients().keySet())));
    }

    private void setAmplificationType(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        Set<String> amplificationTypes = sampleSet.getRequests().stream()
                .flatMap(r -> r.getAmpTypes().stream())
                .collect(Collectors.toSet());

        DEV_LOGGER.info(String.format("Sample set: %s has amplification types: %s", sampleSet.getName(), Utils.getJoinedCollection(amplificationTypes)));
        kickoffRequest.setAmpTypes(amplificationTypes);
    }

    private void setRunIdList(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        Set<String> runIdList = sampleSet.getRequests().stream()
                .flatMap(r -> r.getRunIds().stream())
                .collect(Collectors.toSet());

        runIdList.forEach(kickoffRequest::addRunID);
    }

    private void setReadmeInfo(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.setReadmeInfo(ConverterUtils.getJoinedRequestProperty(sampleSet, Request::getReadmeInfo, System.lineSeparator()));
    }

    private void setPools(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        sampleSet.getRequests().stream()
                .flatMap(r -> r.getPools().entrySet().stream())
                .forEach(pool -> kickoffRequest.putPoolIfAbsent(pool.getValue()));
    }

    private void setStrand(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        Set<Strand> allStrands = sampleSet.getRequests().stream()
                .flatMap(r -> r.getStrands().stream()).collect(Collectors.toSet());

        if (allStrands.size() > 1)
            throw new AmbiguousStrandException(String.format("request: %s has ambiguous strand: %s", kickoffRequest.getId(), StringUtils.join(allStrands, ",")));

        kickoffRequest.setStrands(allStrands);
    }

    private void setProcessingType(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        if (sampleSet.getRequests().stream().anyMatch(KickoffRequest::isForced)) {
            DEV_LOGGER.info(String.format("Setting forced processing type for sample set: %s", sampleSet.getName()));
            kickoffRequest.setProcessingType(new ForcedProcessingType());
        }
    }

    private ProcessingType getProcessingType(SampleSet sampleSet) {
        List<KickoffRequest> kickoffRequests = sampleSet.getRequests();
        Optional<KickoffRequest> request = kickoffRequests.stream().filter(r -> !r.isForced()).findAny();
        return request.map(KickoffRequest::getProcessingType).orElseGet(ForcedProcessingType::new);
    }

    private void setSamples(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        Map<String, Sample> sampleSetSamples = sampleSet.getRequests().stream()
                .flatMap(r -> r.getSamples().entrySet().stream())
                .distinct()
                .collect(CommonUtils.getLinkedHashMapCollector());

        DEV_LOGGER.info(String.format("Samples found for sample set: %s [%s]", sampleSet.getName(), Utils.getJoinedCollection(sampleSetSamples.keySet())));

        setPairings(sampleSet, sampleSetSamples);
        kickoffRequest.setSamples(sampleSetSamples);
    }

    private void setPairings(SampleSet sampleSet, Map<String, Sample> sampleSetSamples) {
        DEV_LOGGER.info(String.format("Found %s pairings for sample set: %s", sampleSet.getPairings().size(), sampleSet.getName()));

        for (PairingInfo pairingInfo : sampleSet.getPairings()) {
            Sample normalSample = sampleSetSamples.get(pairingInfo.getNormalIgoId());
            Sample tumorSample = sampleSetSamples.get(pairingInfo.getTumorIgoId());

            DEV_LOGGER.info(String.format("Found pairing for sample set: %s. Tumor: %s - normal: %s", sampleSet.getName(), tumorSample, normalSample));

            normalSample.setPairing(tumorSample);
            tumorSample.setPairing(normalSample);
        }
    }

    private void setProjectInfo(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        Map<String, String> projectInfo = sampleSetProjectInfoConverter.convert(sampleSet);
        kickoffRequest.setProjectInfo(projectInfo);
    }

    private void setRecipe(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.setRecipe(sampleSet.getRecipe());
    }

    private void setInvest(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.setInvest(ConverterUtils.getJoinedRequestProperty(sampleSet, r -> r.getInvest()));
    }

    private void setExtraReadMe(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.setExtraReadMeInfo(ConverterUtils.getJoinedRequestProperty(sampleSet, r -> r.getExtraReadMeInfo(), System.lineSeparator()));
    }

    private void setReadMe(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.setReadMe(ConverterUtils.getJoinedRequestProperty(sampleSet, r -> r.getReadMe(), System.lineSeparator()));
    }

    private void setBicAutorunnable(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.setBicAutorunnable(sampleSet.getRequests().stream()
                .allMatch(r -> r.isBicAutorunnable()));
    }

    private void setMannualDemux(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.setManualDemux(sampleSet.getRequests().stream()
                .anyMatch(r -> r.isManualDemux()));
    }

    private void setRunNumbers(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.setRunNumbers(ConverterUtils.getJoinedRequestProperty(sampleSet, r -> String.valueOf(r.getRunNumber())));
    }

    private void setRequestType(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        Set<String> requestsWithNotSetType = sampleSet.getRequests().stream()
                .filter(r -> r.getRequestType() == null)
                .map(r -> r.getId())
                .collect(Collectors.toSet());

        if (requestsWithNotSetType.size() > 0)
            throw new NoRequestType(String.format("Project: %s has requests: %s for which it is impossible to figure out request type", kickoffRequest.getId(), ConverterUtils.getJoinedRequestProperty(sampleSet, r -> r.getId(), ",")));

        Set<RequestType> requestTypes = sampleSet.getRequests().stream()
                .map(r -> r.getRequestType())
                .collect(Collectors.toSet());

        if (requestTypes.size() > 1) {
            String message = String.format("Project: %s has ambiguous request type: %s", kickoffRequest.getId(), ConverterUtils.getJoinedRequestProperty(sampleSet, r -> r.getRequestType().getName()));
            PM_LOGGER.error(message);
            throw new AmbiguousRequestType(message);
        }

        kickoffRequest.setRequestType(requestTypes.stream().findAny().get());
    }

    private void setBaitVersion(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.setBaitVersion(sampleSet.getBaitSet());
    }

    private void setLibTypes(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        Set<LibType> libTypes = sampleSet.getRequests().stream()
                .flatMap(r -> r.getLibTypes().stream())
                .collect(Collectors.toSet());
        libTypes.forEach(kickoffRequest::addLibType);
    }

    private void setProjectInvestigators(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.setPi(ConverterUtils.getRequiredSameForAllProperty(sampleSet, r -> r.getPi(), "project investigator", x -> !StringUtils.isEmpty(x)));
    }

    private void setSpecies(KickoffRequest kickoffRequest, SampleSet sampleSet) {
        kickoffRequest.setSpecies(ConverterUtils.getRequiredSameForAllProperty(sampleSet, r -> r.getSpecies(), "species"));
    }

    static class AmbiguousStrandException extends RuntimeException {
        public AmbiguousStrandException(String message) {
            super(message);
        }
    }

    static class AmbiguousRequestType extends RuntimeException {
        public AmbiguousRequestType(String message) {
            super(message);
        }
    }

    static class NoRequestType extends RuntimeException {
        public NoRequestType(String message) {
            super(message);
        }
    }

    static class AmbiguousPropertyException extends RuntimeException {
        public AmbiguousPropertyException(String message) {
            super(message);
        }
    }

    static class NoPropertySetException extends RuntimeException {
        public NoPropertySetException(String message) {
            super(message);
        }
    }

}
