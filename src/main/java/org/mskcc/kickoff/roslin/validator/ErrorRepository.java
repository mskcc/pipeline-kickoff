package org.mskcc.kickoff.roslin.validator;

import org.mskcc.kickoff.roslin.notify.GenerationError;

import java.util.List;

public interface ErrorRepository {
    void add(GenerationError generationError);

    List<GenerationError> getErrors();
}
