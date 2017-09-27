package org.mskcc.kickoff.domain;

import org.mskcc.domain.Run;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.util.CommonUtils;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;


public class KickoffRequest extends org.mskcc.domain.Request {
    private final List<KickoffRequest> requests = new ArrayList<>();
    private ProcessingType processingType;
    private boolean mappingIssue;
    private int newMappingScheme = 0;
    private String runNumbers = "";
    private String outputPath = "";
    private List<PairingInfo> pairingInfos = new ArrayList<>();

    public KickoffRequest(String id, ProcessingType processingType) {
        super(id);
        this.processingType = processingType;
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

    private Map<String, Sample> getAllSamples() {
        return getAllSamples(s -> true);
    }

    private Map<String, Sample> getAllSamples(Predicate<Sample> samplePredicate) {
        return getSamples(samplePredicate).entrySet().stream()
                .collect(CommonUtils.getLinkedHashMapCollector()    );
    }

    public boolean hasValidSamples() {
        return getAllValidSamples().size() > 0;
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

    public Collection<Sample> getValidUniqueSamples(Function<Sample, String> compareSamples) {
        return getAllValidSamples().values().stream()
                .collect(getUniqueCollector(compareSamples));
    }

    public Collection<Sample> getValidUniqueCmoIdSamples() {
        return getValidUniqueCmoIdSamples(s -> true);
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

    public void setPairings(List<PairingInfo> pairingInfos) {
        this.pairingInfos = pairingInfos;
    }

    public List<PairingInfo> getPairingInfos() {
        return pairingInfos;
    }

    public void setPairingInfos(List<PairingInfo> pairingInfos) {
        this.pairingInfos = pairingInfos;
    }
}
