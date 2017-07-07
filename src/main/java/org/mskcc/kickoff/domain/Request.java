package org.mskcc.kickoff.domain;

import org.mskcc.domain.*;
import org.mskcc.kickoff.process.ProcessingType;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Request {
    private final org.mskcc.domain.Request request;
    private ProcessingType processingType;
    private boolean mappingIssue;
    private int newMappingScheme = 0;
    private String outputPath;
    private boolean forced;

    public Request(String id, ProcessingType processingType) {
        this.request = new org.mskcc.domain.Request(id);
        this.processingType = processingType;
    }

    public void archiveFilesToOld() {
        //@TODO think where to keep force/non-force processing
        processingType.archiveFilesToOld(this);
    }

    public Map<String, Sample> getAllValidSamples() {
        return getAllValidSamples(s -> true);
    }

    public Map<String, Sample> getAllValidSamples(Predicate<Sample> samplePredicate) {
        return processingType.getAllValidSamples(request.getSamples(), samplePredicate);
    }

    public boolean hasValidSamples() {
        return getAllValidSamples().size() > 0;
    }

    public Map<String, Sample> getValidNonPooledNormalSamples() {
        return getAllValidSamples(s -> !s.isPooledNormal());
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public String getIncludeRunId(Collection<Run> runs) {
        return processingType.getIncludeRunId(runs);
    }

    public boolean isForced() {
        return forced;
//        @TODO temporary before sampleInfo is dependant of velox
//        return processingType instanceof ForcedProcessingType;
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

    private Function<Sample, String> getGetCmoSampleIdFunction() {
        return Sample::getCmoSampleId;
    }

    public List<Sample> getUniqueSamplesByCmoIdLastWin() {
        return getUniqueSamplesByCmoIdLastWin(s -> true);
    }

    public Collection<Sample> getNonPooledNormalUniqueSamples(Function<Sample, String> compareSamples) {
        return request.getSamples(s -> !s.isPooledNormal()).values().stream()
                .collect(getUniqueCollector(compareSamples));
    }

    private Collector<Sample, ?, TreeSet<Sample>> getUniqueCollector(Function<Sample, String> compareSamples) {
        return Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(compareSamples)));
    }

    public ProcessingType getProcessingType() {
        return processingType;
    }


    public void setProcessingType(ProcessingType processingType) {
        //@TODO temporary, do not use forced flag later
        forced = true;
        this.processingType = processingType;
    }

    public String getId() {
        return request.getId();
    }

    public void addRunIDlist(String runIDFull) {
        request.addRunIDlist(runIDFull);
    }

    public String getBaitVersion() {
        return request.getBaitVersion();
    }

    public String getRequestType() {
        return request.getRequestType();
    }

    public boolean isInnovationProject() {
        return request.isInnovationProject();
    }

    public boolean isManualDemux() {
        return request.isManualDemux();
    }

    public Set<LibType> getLibTypes() {
        return request.getLibTypes();
    }

    public Map<String, Sample> getSamples() {
        return request.getSamples();
    }

    public Map<String, Pool> getPools() {
        return request.getPools();
    }

    public boolean isBicAutorunnable() {
        return request.isBicAutorunnable();
    }

    public String getReadMe() {
        return request.getReadMe();
    }

    public Map<String, Sample> getSamples(Predicate<Sample> samplePredicate) {
        return request.getSamples(samplePredicate);
    }

    public String getExtraReadMeInfo() {
        return request.getExtraReadMeInfo();
    }

    public void setExtraReadMeInfo(String extraReadMeInfo) {
        request.setExtraReadMeInfo(extraReadMeInfo);
    }

    public Map<String, Patient> getPatients() {
        return request.getPatients();
    }

    public Sample getSample(String igoId) {
        return request.getSample(igoId);
    }

    public Optional<Sample> getSampleByCorrectedCmoId(String cmoSampleId) {
        return request.getSampleByCorrectedCmoId(cmoSampleId);
    }

    public String getReadmeInfo() {
        return request.getReadmeInfo();
    }

    public void setRequestType(String requestType) {
        request.setRequestType(requestType);
    }

    public void setProjectInfo(Map<String, String> projectInfo) {
        request.setProjectInfo(projectInfo);
    }

    public String getPi() {
        return request.getPi();
    }

    public String getInvest() {
        return request.getInvest();
    }

    public void setRunNumber(int runNum) {
        request.setRunNumber(runNum);
    }

    public RequestSpecies getSpecies() {
        return request.getSpecies();
    }

    public void setReadmeInfo(String readmeInfo) {
        request.setReadmeInfo(readmeInfo);
    }

    public Patient putPatientIfAbsent(String patientId) {
        return request.putPatientIfAbsent(patientId);
    }

    public void setBaitVersion(String baitVersion) {
        request.setBaitVersion(baitVersion);
    }

    public void setPi(String pi) {
        request.setPi(pi);
    }

    public void setInvest(String invest) {
        request.setInvest(invest);
    }

    public void setSpecies(RequestSpecies sampleSpecies) {
        request.setSpecies(sampleSpecies);
    }

    public int getRunNumber() {
        return request.getRunNumber();
    }

    public List<Recipe> getRecipe() {
        return request.getRecipe();
    }

    public Map<String, String> getProjectInfo() {
        return request.getProjectInfo();
    }

    public Set<String> getRunIdList() {
        return request.getRunIdList();
    }

    public List<String> getAmpType() {
        return request.getAmpType();
    }

    public Set<Strand> getStrands() {
        return request.getStrands();
    }

    public void setBicAutorunnable(boolean bicAutorunnable) {
        request.setBicAutorunnable(bicAutorunnable);
    }

    public void setManualDemux(boolean manualDemux) {
        request.setManualDemux(manualDemux);
    }

    public void setRequestName(String requestName) {
        request.setRequestName(requestName);
    }

    public void setRecipe(List<Recipe> recipes) {
        request.setRecipe(recipes);
    }

    public void setReadMe(String readMe) {
        request.setReadMe(readMe);
    }

    public void setName(String name) {
        request.setName(name);
    }

    public Sample putPooledNormalIfAbsent(String igoNormalId) {
        return request.putPooledNormalIfAbsent(igoNormalId);
    }

    public String getName() {
        return request.getName();
    }

    public String getRequestName() {
        return request.getRequestName();
    }

    public Optional<Sample> getSampleByCmoId(String cmoSampleId) {
        return request.getSampleByCmoId(cmoSampleId);
    }

    public Pool putPoolIfAbsent(String igoSampleId) {
        return request.putPoolIfAbsent(igoSampleId);
    }

    public Sample putSampleIfAbsent(String igoSampleId) {
        return request.putSampleIfAbsent(igoSampleId);
    }

    public Sample getOrCreate(String igoSampleId) {
        return request.getOrCreate(igoSampleId);
    }

    public void addLibType(LibType libType) {
        request.addLibType(libType);
    }

    public void addStrand(Strand strand) {
        request.addStrand(strand);
    }
}
