package org.mskcc.kickoff.resolver;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mskcc.domain.Pairedness;
import org.mskcc.util.Constants;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class PairednessResolverTest {
    private final PairednessResolver pairednessResolver = new PairednessResolver();

    @Rule
    public TemporaryFolder fastqDir = new TemporaryFolder();

    @Test
    public void whenNoFilesInPath_shouldReturnSE() throws Exception {
        Pairedness pairedness = pairednessResolver.resolve(fastqDir.getRoot().getPath());

        assertThat(pairedness, is(Pairedness.SE));
    }

    @Test
    public void whenOneNonPairedFileInPath_shouldReturnSE() throws Exception {
        fastqDir.newFile("whateverIsThere_thereIsNoPairedness.fastq.gz");

        Pairedness pairedness = pairednessResolver.resolve(fastqDir.getRoot().getPath());

        assertThat(pairedness, is(Pairedness.SE));
    }

    @Test
    public void whenOneNonPairedFileWithPairedSuffixNotAtTheEndInPath_shouldReturnSE() throws Exception {
        fastqDir.newFile("whateverIsThere_thereIsNo" + Constants.PAIRED_FILE_SUFFIX + "_ale_zonk_a_myslales_ze_to_paired_end.fastq.gz");

        Pairedness pairedness = pairednessResolver.resolve(fastqDir.getRoot().getPath());

        assertThat(pairedness, is(Pairedness.SE));
    }

    @Test
    public void whenMutlipleNonPairedFileInPath_shouldReturnSE() throws Exception {
        fastqDir.newFile("whateverIsThere_thereIsNoPairedness.fastq.gz");
        fastqDir.newFile("cokolwiek_sobie_zamarzysz_NoPairedness.fastq.gz");
        fastqDir.newFile("nawet_o_tym_nie_marz.fastq.gz");

        Pairedness pairedness = pairednessResolver.resolve(fastqDir.getRoot().getPath());

        assertThat(pairedness, is(Pairedness.SE));
    }

    @Test
    public void whenMutlipleNonPairedAndOnePairedFilesInPath_shouldReturnPE() throws Exception {
        fastqDir.newFile("whateverIsThere_thereIsNoPairedness.fastq.gz");
        fastqDir.newFile("cokolwiek_sobie_zamarzysz_NoPairedness.fastq.gz");
        fastqDir.newFile("nawet_o_tym_nie_marz.fastq.gz");
        fastqDir.newFile("nawet_o_tym_nie_marz" + Constants.PAIRED_FILE_SUFFIX);

        Pairedness pairedness = pairednessResolver.resolve(fastqDir.getRoot().getPath());

        assertThat(pairedness, is(Pairedness.PE));
    }

    @Test
    public void whenOnePairedFileInPath_shouldReturnPE() throws Exception {
        fastqDir.newFile("whateverName" + Constants.PAIRED_FILE_SUFFIX);

        Pairedness pairedness = pairednessResolver.resolve(fastqDir.getRoot().getPath());

        assertThat(pairedness, is(Pairedness.PE));
    }

    @Test
    public void whenMultiplePairedFilesInPath_shouldReturnPE() throws Exception {
        fastqDir.newFile("whateverName" + Constants.PAIRED_FILE_SUFFIX);
        fastqDir.newFile("something_different" + Constants.PAIRED_FILE_SUFFIX);
        fastqDir.newFile("yes_another_different_fancy_name" + Constants.PAIRED_FILE_SUFFIX);

        Pairedness pairedness = pairednessResolver.resolve(fastqDir.getRoot().getPath());

        assertThat(pairedness, is(Pairedness.PE));
    }

    @Test
    public void whenMultiplePairedFilesAndOneNonPairesInPath_shouldReturnPE() throws Exception {
        fastqDir.newFile("whateverName" + Constants.PAIRED_FILE_SUFFIX);
        fastqDir.newFile("something_different" + Constants.PAIRED_FILE_SUFFIX);
        fastqDir.newFile("yes_another_different_fancy_name" + Constants.PAIRED_FILE_SUFFIX);
        fastqDir.newFile("yes_another_different_fancy_name");

        Pairedness pairedness = pairednessResolver.resolve(fastqDir.getRoot().getPath());

        assertThat(pairedness, is(Pairedness.PE));
    }

}