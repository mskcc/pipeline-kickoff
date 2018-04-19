package org.mskcc.kickoff.validator;

import org.mskcc.kickoff.notify.GenerationError;

import java.util.List;

public interface ErrorRepository {
    void add(GenerationError generationError);

    List<GenerationError> getErrors();
}
