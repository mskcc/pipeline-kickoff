package org.mskcc.kickoff.velox.util;

import com.velox.sapioutils.client.standalone.VeloxConnection;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.velox.VeloxConnectionData;

public class VeloxUtils {
    private static final Logger devLogger = Logger.getLogger(Constants.DEV_LOGGER);

    public static VeloxConnection getVeloxConnection(VeloxConnectionData veloxConnectionData) {
        return new VeloxConnection(veloxConnectionData.getHost(), veloxConnectionData.getPort(), veloxConnectionData
                .getGuid(), veloxConnectionData.getUsername(), veloxConnectionData.getPassword());
    }
}
