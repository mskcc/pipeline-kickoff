package org.mskcc.kickoff.poolednormals;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mskcc.kickoff.domain.KickoffRequest;

import java.util.Collection;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class RNASeqPooledNormalsRetrieverTest {
    @Test
    public void always_shouldReturnEmptyMap() throws Exception {
        //given
        RNASeqPooledNormalsRetriever retriever = new RNASeqPooledNormalsRetriever();

        //when
        Map<DataRecord, Collection<String>> pooledNormals = retriever.getAllPooledNormals(mock(KickoffRequest
                .class), mock(User.class), mock(DataRecordManager.class));

        //then
        Assertions.assertThat(pooledNormals).isEmpty();
    }

}