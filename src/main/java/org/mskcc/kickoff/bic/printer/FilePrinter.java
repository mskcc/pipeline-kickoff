package org.mskcc.kickoff.bic.printer;

import org.mskcc.kickoff.bic.domain.KickoffRequest;

public interface FilePrinter {
    void print(KickoffRequest request);

    boolean shouldPrint(KickoffRequest request);
}
