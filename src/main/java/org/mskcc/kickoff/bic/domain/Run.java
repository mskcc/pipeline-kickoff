package org.mskcc.kickoff.bic.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Run {
    private static final List<Run> allRuns = new ArrayList<>();
    //@TODO change to Map<String, Set<String>>
    private static final Map<String, String> runIdToPoolName = new HashMap<>();

    private final String id;
    private QcStatus sampleLevelQsStatus;
    private QcStatus poolQsStatus;
    private QcStatus postQcStatus;
    //@TODO create subclass
    private boolean badRun;
    private long recordId;
    private long numberOfReads;
    private String note = "";
    private boolean valid;

    private Predicate<Run> passedRunPredicate = new PassedRunPredicate();
    private Predicate<Run> sampleQcPredicate = new SampleQcPassedPredicate();
    private Predicate<Run> postQcPredicate = new PostQcPassedPredicate();

    public Run(String id) {
        this.id = id;
        //@TODO change it to avoid reference leak
    }

    public static void addRun(Run run) {
        allRuns.add(run);
    }

    public static List<Run> getAllRuns() {
        return getAllRuns(r -> true);
    }

    public static List<Run> getAllRuns(Predicate<? super Run> filter) {
        return allRuns.stream()
                .filter(filter)
                .collect(Collectors.toList());
    }

    public static Map<String, String> getRunIdToPoolName() {
        return runIdToPoolName;
    }

    public void setPassedRunPredicate(Predicate<Run> passedRunPredicate) {
        this.passedRunPredicate = passedRunPredicate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Run run = (Run) o;

        return id != null ? id.equals(run.id) : run.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    public long getRecordId() {
        return recordId;
    }

    public void setRecordId(long recordId) {
        this.recordId = recordId;
    }

    public long getNumberOfReads() {
        return numberOfReads;
    }

    public void setNumberOfReads(long numberOfReads) {
        this.numberOfReads = numberOfReads;
    }

    public static void addRunIdPoolNameMapping(String runId, String poolName) {
        if (runIdToPoolName.containsKey(runId)) {
            runIdToPoolName.put(runId, runIdToPoolName.get(runId) + ";" + poolName);
        } else {
            runIdToPoolName.put(runId, poolName);
        }
    }

    public String getId() {
        return id;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public QcStatus getSampleLevelQcStatus() {
        return sampleLevelQsStatus;
    }

    public void setSampleLevelQcStatus(QcStatus sampleLevelQsStatus) {
        this.sampleLevelQsStatus = sampleLevelQsStatus;
    }

    public QcStatus getPoolQcStatus() {
        return poolQsStatus;
    }

    public void setPoolQcStatus(QcStatus poolQsStatus) {
        this.poolQsStatus = poolQsStatus;
    }

    public QcStatus getPostQcStatus() {
        return postQcStatus;
    }

    public void setPostQcStatus(QcStatus postQcStatus) {
        this.postQcStatus = postQcStatus;
    }

    public boolean isBadRun() {
        return badRun;
    }

    public void setBadRun(boolean badRun) {
        this.badRun = badRun;
    }

    @Override
    public String toString() {
        return id;
    }

    public boolean isPassed() {
        return passedRunPredicate.test(this);
    }

    public boolean isSampleQcPassed() {
        return sampleQcPredicate.test(this);
    }

    public boolean isPostQcPassed() {
        return postQcPredicate.test(this);
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }
}
