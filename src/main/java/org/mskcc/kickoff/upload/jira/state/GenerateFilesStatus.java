package org.mskcc.kickoff.upload.jira.state;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.jira.domain.JiraIssue;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.util.Constants;

import java.util.List;

public class GenerateFilesStatus implements IssueStatus {
    private final String name;
    private final String transitionName;
    private final IssueStatus nextState;
    private ErrorRepository errorRepository;

    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    public GenerateFilesStatus(String name, String transitionName, IssueStatus nextState, ErrorRepository errorRepository) {
        this.name = name;
        this.transitionName = transitionName;
        this.nextState = nextState;
        this.errorRepository = errorRepository;
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader jiraFileUploader, String key, String summary) {
        if (validateBefore(kickoffRequest, jiraFileUploader, key)) {
            jiraFileUploader.uploadFiles(kickoffRequest, key);
            jiraFileUploader.assignUser(kickoffRequest, key);
        }

        jiraFileUploader.setIssueStatus(nextState);
        jiraFileUploader.changeStatus(transitionName, key);
    }

    @Override
    public boolean validateBefore(KickoffRequest kickoffRequest, FileUploader fileUploader, String key) {
        return validateRequest(kickoffRequest) &&
                validateNoManifestFilesExists(kickoffRequest, fileUploader, key);
    }

    @Override
    public void validateAfter(String key, String summary, FileUploader jiraFileUploader) {
        throw new IllegalStateException(String.format("Files cannot be validated in state: %s. They haven't been " +
                "generated yet.", getName()));
    }

    private boolean validateRequest(KickoffRequest request) {
        return request != null;
    }

    private boolean validateNoManifestFilesExists(KickoffRequest kickoffRequest, FileUploader
            jiraFileUploader, String key) {
        List<JiraIssue.Fields.Attachment> existingManifestAttachments = jiraFileUploader
                .getExistingManifestAttachments(kickoffRequest, key);
        if (existingManifestAttachments.size() != 0) {
            String msg = "This is initial manifest files generation. No files should be " +
                            "uploaded for " + key;
            errorRepository.add(new GenerationError(msg, ErrorCode.JIRA_UPLOAD_ERROR));
            LOGGER.error(msg);
            return false;
        }

        return true;
    }

    @Override
    public String getName() {
        return name;
    }
}
