package org.mskcc.kickoff.bic.process;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.bic.domain.Pool;
import org.mskcc.kickoff.bic.domain.Run;
import org.mskcc.kickoff.bic.domain.sample.Sample;
import org.mskcc.kickoff.bic.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.bic.domain.KickoffRequest;
import org.mskcc.kickoff.bic.util.Constants;
import org.mskcc.kickoff.bic.util.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;

public class NormalProcessingType implements ProcessingType {
    private static Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private ProjectFilesArchiver projectFilesArchiver;

    public NormalProcessingType(ProjectFilesArchiver projectFilesArchiver) {
        this.projectFilesArchiver = projectFilesArchiver;
    }

    @Override
    public void archiveFilesToOld(KickoffRequest request) {
        if (request.getRunNumber() > 1 && request.getRequestType().equals(Constants.RNASEQ)) {
            String finalDir = String.valueOf(request.getOutputPath()).replaceAll("drafts", request.getRequestType());
            File oldReqFile = new File(String.format("%s/%s_request.txt", finalDir, Utils.getFullProjectNameWithPrefix(request.getId())));
            if (oldReqFile.exists()) {
                String lastUpdated = getPreviousDateOfLastUpdate(oldReqFile);
                projectFilesArchiver.invoke(request, lastUpdated, "_old");
            }
        }
    }

    private String getPreviousDateOfLastUpdate(File request) {
        String date = "NULL";
        try (BufferedReader reader = new BufferedReader(new FileReader(request))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("DateOfLastUpdate: ")) {
                    return line.split(": ")[1].replaceAll("-", "");
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information date of last update for request id: %s", Arguments.project), e);
        }

        return date;
    }

    @Override
    public Map<String, Sample> getAllValidSamples(Map<String, Sample> samples, Predicate<Sample> samplePredicate) {
        return samples.entrySet()
                .stream()
                .filter(s -> s.getValue().isValid())
                .filter(s -> samplePredicate.test(s.getValue()))
                .collect(Utils.getLinkedHashMapCollector());
    }

    @Override
    public String getIncludeRunId(Collection<Run> runs) {
        return StringUtils.join(runs, ";");
    }

    @Override
    public Map<String, Pool> getValidPools(Map<String, Pool> pools) {
        return Collections.emptyMap();
    }
}
