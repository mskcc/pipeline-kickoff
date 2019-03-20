package org.mskcc.kickoff.roslin.validator;

import org.mskcc.kickoff.roslin.notify.GenerationError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InMemoryErrorRepository implements ErrorRepository {
    private List<GenerationError> errors = new ArrayList<>();

    @Override
    public void add(GenerationError generationError) {
        errors.add(generationError);
    }

    @Override
    public List<GenerationError> getErrors() {
        return errors;
    }
}
