package org.mskcc.kickoff.retriever;

import org.hamcrest.object.IsCompatibleType;
import org.junit.Test;
import org.mskcc.util.TestUtils;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class SequencerRunFolderRetrieverTest {
    private SequencerRunFolderRetriever sequencerRunFolderRetriever = new SequencerRunFolderRetriever();

    @Test
    public void whenSeqRunFolderIsValid_shouldReturnSequencerName() throws Exception {
        assertSequencerName("KIM", "_4324324_nriUI83989hgrei");
        assertSequencerName("bfjekwfnew", "_324_ni");
        assertSequencerName("ALA", "_1_nriUI83989hgrei");
        assertSequencerName("LALOPE", "_4324324_nriUI83989hgrei");
        assertSequencerName("KOTKOT", "_001_ABH67D");
    }

    @Test
    public void whenSequencerRunFolderIsInvalid_shouldThrowAnException() throws Exception {
        assertExceptionThrown("_432_HJF");
        assertExceptionThrown("_");
        assertExceptionThrown("");
        assertExceptionThrown("DFSD_");
        assertExceptionThrown("DFSD");
    }

    private void assertExceptionThrown(String invalidRunFolder) {
        Optional<Exception> exception = TestUtils.assertThrown(() -> {
            sequencerRunFolderRetriever.retrieve(invalidRunFolder);
        });

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(SequencerRunFolderRetriever
                .InvalidSequencerRunFolderException.class));
    }

    private void assertSequencerName(String seqName, String runFolderSuffix) {
        String runFolder = seqName + runFolderSuffix;

        String actualSeqName = sequencerRunFolderRetriever.retrieve(runFolder);

        assertThat(actualSeqName, is(seqName));
    }

}