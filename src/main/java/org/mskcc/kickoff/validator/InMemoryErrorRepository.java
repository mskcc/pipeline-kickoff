package org.mskcc.kickoff.validator;

import org.mskcc.kickoff.notify.GenerationError;
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
