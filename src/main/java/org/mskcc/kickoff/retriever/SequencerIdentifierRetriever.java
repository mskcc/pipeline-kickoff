package org.mskcc.kickoff.retriever;

import org.apache.log4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SequencerIdentifierRetriever {
    private static final Logger LOGGER = Logger.getLogger(SequencerIdentifierRetriever.class);

    public String retrieve(String runFolder) {
        Pattern pattern = Pattern.compile("([a-zA-Z0-9]+)_.+");
        Matcher matcher = pattern.matcher(runFolder);

        if (!matcher.matches())
            throw new InvalidSequencerRunFolderException(String.format("Invalid sequencer run folder name: \"%s\". " +
                    "Expected format is: \"<SEQUENCER_NAME>_<WHATEVER>\"", runFolder));

        String sequencerIdentifier = matcher.group(1);

        LOGGER.info(String.format("Sequencer identifier found: %s", sequencerIdentifier));

        return sequencerIdentifier;
    }

    class InvalidSequencerRunFolderException extends RuntimeException {
        public InvalidSequencerRunFolderException(String message) {
            super((message));
        }
    }
}
