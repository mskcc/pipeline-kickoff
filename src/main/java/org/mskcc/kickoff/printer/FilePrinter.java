package org.mskcc.kickoff.printer;

import org.mskcc.kickoff.domain.KickoffRequest;

public interface FilePrinter {
    void print(KickoffRequest kickoffRequest);

    boolean shouldPrint(KickoffRequest kickoffRequest);
}
