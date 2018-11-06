package org.mskcc.kickoff.validator;

import com.velox.sapioutils.shared.utilities.Sets;
import org.junit.Test;
import org.mskcc.domain.Strand;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.process.ProcessingType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class StrandValidatorTest {
    private ErrorRepository errorRepository = new InMemoryErrorRepository();
    private StrandValidator strandValidator = new StrandValidator(errorRepository);

    @Test
    public void whenThereAreMultipleStrands_shouldAddErrorToRepository() throws Exception {
        //given
        KickoffRequest kickoffRequest = new KickoffRequest("id1", mock(ProcessingType.class));
        kickoffRequest.setStrands(Sets.asHashSet(Strand.NONE, Strand.REVERSE));

        //when
        strandValidator.test(kickoffRequest);

        //then
        assertThat(errorRepository.getErrors().stream()
                .anyMatch(e -> e.getErrorCode() == ErrorCode.AMBIGUOUS_STRAND));
    }
}