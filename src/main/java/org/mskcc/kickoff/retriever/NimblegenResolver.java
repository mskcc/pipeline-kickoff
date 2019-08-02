package org.mskcc.kickoff.retriever;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;

public interface NimblegenResolver {
    DataRecord resolve(DataRecordManager drm, DataRecord rec, User apiUser, boolean isPoolNormal);

    boolean shouldRetrieve();
}
