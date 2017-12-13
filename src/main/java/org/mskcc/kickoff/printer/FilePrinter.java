package org.mskcc.kickoff.printer;

import org.mskcc.kickoff.domain.KickoffRequest;

public interface FilePrinter {
    boolean shouldPrint(KickoffRequest kickoffRequest);

    String getFilePath(KickoffRequest request);

    void print(KickoffRequest kickoffRequest);
}
