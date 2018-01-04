package org.mskcc.kickoff.notify;

import org.mskcc.kickoff.manifest.ManifestFile;

import java.util.List;
import java.util.stream.Collectors;

public class NewLineNotificationFormatter implements NotificationFormatter {
    @Override
    public String format(List<ManifestFile> notGenerated) {
        String formatted = notGenerated.stream()
                .map(f -> f.getName())
                .collect(Collectors.joining(System.lineSeparator()));
        return formatted;
    }
}
