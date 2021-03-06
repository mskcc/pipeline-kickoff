package org.mskcc.kickoff.domain;

import org.apache.log4j.Logger;
import org.mskcc.domain.PairingInfo;
import org.mskcc.domain.RequestSpecies;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.Run;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.util.CommonUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class KickoffRequest extends org.mskcc.domain.Request {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final List<KickoffRequest> requests = new ArrayList<>();
    private ProcessingType processingType;
    private boolean mappingIssue;
    private int newMappingScheme = 0;
    private String runNumbers = "";
    private String outputPath = "";
    private List<PairingInfo> pairingInfos = new ArrayList<>();
    private boolean pairingError;
    private String rerunReason;
    private RequestTypeStrategy requestTypeStrategy;
    private RequestTypeStrategyFactory requestTypeStrategyFactory = new RequestTypeStrategyFactory();
    private Set<String> pairingSampleIds = new HashSet<>();
    private Map<String, KickoffExternalSample> externalSamples = new HashMap<>();
    private LocalDateTime creationDate;
    private Set<Set<RequestSpecies>> speciesSet = new HashSet<>();

    // normal samples from sibling requests of a given request (children of a given project). Only valid samples with
    // passed QC are kept
    private Map<String, Sample> validNormalsForProject = new HashMap<>();

    // Field added only to enable getting normals from sibling requests and not throw errors in SampleImpactInfo when
    // validating if same for which SampleInfo is retrieved is part of the request in
    // org/mskcc/kickoff/lims/SampleInfoImpact.java:436 & org/mskcc/kickoff/lims/SampleInfoImpact.java:441
    private Map<String, Sample> samplesForProjects = new HashMap<>();
    private String projectId;
    private Set<Sample> usedNormalsFromProject = new HashSet<>();

    public KickoffRequest(String id, ProcessingType processingType) {
        super(id);
        this.processingType = processingType;
    }

    public Set<Set<RequestSpecies>> getSpeciesSet() {
        return speciesSet;
    }

    public void setSpeciesSet(Set<Set<RequestSpecies>> speciesSet) {
        this.speciesSet = speciesSet;
    }

    public RequestTypeStrategy getRequestTypeStrategy() {
        return requestTypeStrategy;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public String getRunNumbers() {
        return runNumbers;
    }

    public void setRunNumbers(String runNumbers) {
        this.runNumbers = runNumbers;
    }

    public Map<String, Sample> getAllValidSamples() {
        return getAllValidSamples(s -> true);
    }

    public Map<String, Sample> getAllValidSamples(Predicate<Sample> samplePredicate) {
        return processingType.getAllValidSamples(getAllSamples(), samplePredicate);
    }

    public Map<String, Sample> getValidSamples(Map<String, Sample> samples, Predicate<Sample> samplePredicate) {
        return processingType.getAllValidSamples(samples, samplePredicate);
    }

    private Map<String, Sample> getAllSamples() {
        return getAllSamples(s -> true);
    }

    private Map<String, Sample> getAllSamples(Predicate<Sample> samplePredicate) {
        return getSamples(samplePredicate).entrySet().stream()
                .collect(CommonUtils.getLinkedHashMapCollector());
    }

    public Map<String, Sample> getValidNonPooledNormalSamples() {
        return getAllValidSamples(s -> !s.isPooledNormal());
    }

    public String getIncludeRunId(Collection<Run> runs) {
        return processingType.getIncludeRunId(runs);
    }

    public boolean isForced() {
        return processingType.isForced();
    }

    @Override
    public String toString() {
        return getId();
    }

    public boolean isMappingIssue() {
        return mappingIssue;
    }

    public void setMappingIssue(boolean mappingIssue) {
        this.mappingIssue = mappingIssue;
    }

    public int getNewMappingScheme() {
        return newMappingScheme;
    }

    public void setNewMappingScheme(int newMappingScheme) {
        this.newMappingScheme = newMappingScheme;
    }

    public Collection<Sample> getValidUniqueCmoIdSamples(Predicate<Sample> filter) {
        return getAllValidSamples(filter).values().stream()
                .collect(getUniqueCollector(getGetCmoSampleIdFunction()));
    }

    public List<Sample> getUniqueSamplesByCmoIdLastWin(Predicate<Sample> samplePredicate) {
        LinkedList<Sample> allSamples = new LinkedList<>(getAllValidSamples(samplePredicate).values());
        Collections.reverse(allSamples);
        List<Sample> samples = new LinkedList<>();

        Set<String> addedCmoIds = new HashSet<>();
        for (Sample sample : allSamples) {
            if (!addedCmoIds.contains(sample.getCmoSampleId()))
                samples.add(sample);
            addedCmoIds.add(sample.getCmoSampleId());
        }

        return samples;
    }

    public void archiveFilesToOld() {
        processingType.archiveFilesToOld(this);
    }

    private Function<Sample, String> getGetCmoSampleIdFunction() {
        return Sample::getCmoSampleId;
    }

    public List<Sample> getUniqueSamplesByCmoIdLastWin() {
        return getUniqueSamplesByCmoIdLastWin(s -> true);
    }

    public Collection<Sample> getNonPooledNormalUniqueSamples(Function<Sample, String> compareSamples) {
        return getAllSamples(s -> !s.isPooledNormal()).values().stream()
                .collect(getUniqueCollector(compareSamples));
    }

    private Collector<Sample, ?, TreeSet<Sample>> getUniqueCollector(Function<Sample, String> compareSamples) {
        return Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(compareSamples)));
    }

    public ProcessingType getProcessingType() {
        return processingType;
    }

    public void setProcessingType(ProcessingType processingType) {
        this.processingType = processingType;
    }

    public List<KickoffRequest> getRequests() {
        return requests;
    }

    public void addRequests(List<KickoffRequest> requests) {
        this.requests.addAll(requests);

    }

    public List<PairingInfo> getPairingInfos() {
        return pairingInfos;
    }

    public void setPairingInfos(List<PairingInfo> pairingInfos) {
        this.pairingInfos = pairingInfos;
    }

    public boolean isPairingError() {
        return pairingError;
    }

    public void setPairingError(boolean pairingError) {
        this.pairingError = pairingError;
    }

    public String getRerunReason() {
        return rerunReason;
    }

    public void setRerunReason(String rerunReason) {
        this.rerunReason = rerunReason;
    }

    @Override
    public void setRequestType(RequestType requestType) {
        this.requestTypeStrategy = requestTypeStrategyFactory.getRequestTypeStrategy(requestType);
        super.setRequestType(requestType);
    }

    public void validateHasSamples() {
        if (getAllValidSamples().isEmpty())
            throw new NoValidSamplesException(String.format("There are no valid samples in request: %s", getId()));

        Set<String> validSampleIds = new TreeSet<>(getAllValidSamples().keySet());
        DEV_LOGGER.info(String.format("Found %d valid samples: [%s]", getAllValidSamples().size(), Utils
                .getJoinedCollection(validSampleIds, ",")));
    }

    public Set<String> getPairingSampleIds() {
        return pairingSampleIds;
    }

    public void addPairingSampleId(String sampleId) {
        pairingSampleIds.add(sampleId);
    }

    public void addPairingSampleIds(Collection<String> sampleIds) {
        pairingSampleIds.addAll(sampleIds);
    }

    public boolean isExome() {
        return getRequestType() == RequestType.EXOME;
    }

    public boolean isImpact() {
        return getRequestType() == RequestType.IMPACT;
    }

    public Map<String, KickoffExternalSample> getExternalSamples() {
        return externalSamples;
    }

    public void setExternalSamples(Map<String, KickoffExternalSample> externalSamples) {
        this.externalSamples = externalSamples;
    }

    public void addExternalSample(KickoffExternalSample kickoffExternalSample) {
        externalSamples.put(kickoffExternalSample.getIgoId(), kickoffExternalSample);
    }

    public List<KickoffExternalSample> getTumorExternalSamples() {
        return getExternalSamples().values().stream()
                .filter(s -> s.isTumor())
                .collect(Collectors.toList());
    }

    public boolean isSampleFromRequest(String id) {
        return containsIgoSample(id) || containsExternalSample(id);
    }

    public boolean containsExternalSample(String sampleId) {
        return externalSamples.containsKey(sampleId);
    }

    public boolean containsIgoSample(String sampleId) {
        return getSamples().containsKey(sampleId);
    }

    public boolean containsSample(String sampleId) {
        return containsIgoSample(sampleId) || containsExternalSample(sampleId);
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    @Override
    public Sample getSample(String sampleId) {
        if (getSamples().containsKey(sampleId))
            return super.getSample(sampleId);
        if (!externalSamples.containsKey(sampleId))
            throw new RuntimeException(String.format("Request %s doesn't contain sample with id: %s", getId(),
                    sampleId));

        return externalSamples.get(sampleId);
    }

    public Map<String, Sample> getValidNormalsForProject() {
        return validNormalsForProject;
    }

    public void setValidNormalsForProject(Map<String, Sample> normalSamplesForProject) {
        this.validNormalsForProject = normalSamplesForProject;
    }

    public void addSamplesForProject(Sample sampleForProject) {
        samplesForProjects.put(sampleForProject.getIgoId(), sampleForProject);
    }

    public Map<String, Sample> getSamplesForProjects() {
        return samplesForProjects;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public void addUsedNormalFromProject(Sample normal) {
        usedNormalsFromProject.add(normal);
    }

    public Set<Sample> getUsedNormalsFromProject() {
        return usedNormalsFromProject;
    }

    public void setUsedNormalsFromProject(Set<Sample> usedNormalsFromProject) {
        this.usedNormalsFromProject = usedNormalsFromProject;
    }

    public static class NoValidSamplesException extends RuntimeException {
        public NoValidSamplesException(String message) {
            super(message);
        }
    }
}
