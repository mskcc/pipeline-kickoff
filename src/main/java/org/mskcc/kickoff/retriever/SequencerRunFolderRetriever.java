package org.mskcc.kickoff.retriever;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SequencerRunFolderRetriever {
    public String retrieve(String runFolder) {
        Pattern pattern = Pattern.compile("([a-zA-Z]+)_.+");
        Matcher matcher = pattern.matcher(runFolder);

        if (!matcher.matches())
            throw new InvalidSequencerRunFolderException(String.format("Invalid sequencer run folder name: \"%s\". " +
                    "Expected format is: \"<SEQUENCER_NAME>_<RUN_ID>\"", runFolder));

        return matcher.group(1);
    }

    class InvalidSequencerRunFolderException extends RuntimeException {
        public InvalidSequencerRunFolderException(String message) {
            super((message));
        }
    }
}
