package org.mskcc.kickoff.notify;

import org.mskcc.kickoff.manifest.ManifestFile;

import java.util.List;

public interface NotificationFormatter {
    String format(List<ManifestFile> notGenerated);
}
