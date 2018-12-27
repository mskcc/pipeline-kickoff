package org.mskcc.kickoff.poolednormals;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.mskcc.kickoff.domain.KickoffRequest;

import java.util.Collection;
import java.util.Map;

public interface PooledNormalsRetriever {
    Map<DataRecord, Collection<String>> getAllPooledNormals(KickoffRequest request, User user,
                                                            DataRecordManager dataRecordManager);
}
