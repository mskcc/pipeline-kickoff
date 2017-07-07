package org.mskcc.kickoff.printer;

import org.mskcc.kickoff.domain.Request;

public interface FilePrinter {
    void print(Request request);

    boolean shouldPrint(Request request);
}
