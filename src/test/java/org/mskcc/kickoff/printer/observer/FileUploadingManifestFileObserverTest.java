package org.mskcc.kickoff.printer.observer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.junit.Before;
import org.junit.Test;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.FileGenerated;
import org.mskcc.kickoff.process.ProcessingType;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class FileUploadingManifestFileObserverTest {
    private final KickoffRequest request = new KickoffRequest("reqId1", mock(ProcessingType.class));
    private SpyFileUploader fileUploader;
    private FileUploadingManifestFileObserver fileUploadingManifestFileObserver;
    private Multimap<KickoffRequest, ManifestFile> createdFiles;

    @Before
    public void setUp() throws Exception {
        createdFiles = HashMultimap.create();
        fileUploader = new SpyFileUploader();
        fileUploadingManifestFileObserver = new
                FileUploadingManifestFileObserver(fileUploader);
    }

    @Test
    public void whenOneFileIsGenerated_shouldUploadOneFile() throws Exception {
        //given
        createdFiles.put(request, ManifestFile.MANIFEST);

        assertUploadedFiles(createdFiles);
    }

    @Test
    public void whenMultipleFileIsGenerated_shouldUploadAllFiles() throws Exception {
        //given
        createdFiles.put(request, ManifestFile.MANIFEST);
        createdFiles.put(request, ManifestFile.MAPPING);
        createdFiles.put(request, ManifestFile.GROUPING);
        createdFiles.put(request, ManifestFile.PAIRING);
        createdFiles.put(request, ManifestFile.PATIENT);
        createdFiles.put(request, ManifestFile.REQUEST);
        createdFiles.put(request, ManifestFile.C_TO_P_MAPPING);
        createdFiles.put(request, ManifestFile.CLINICAL);
        createdFiles.put(request, ManifestFile.README);
        createdFiles.put(request, ManifestFile.SAMPLE_KEY);

        assertUploadedFiles(createdFiles);
    }

    @Test
    public void whenFilesAreRegeneratedMultipleTimes_shouldDeleteAndUploadEveryTime() throws Exception {
        //given

        //when
        fileUploadingManifestFileObserver.update(request, ManifestFile.MANIFEST, FileGenerated.INSTANCE);
        fileUploadingManifestFileObserver.update(request, ManifestFile.MANIFEST, FileGenerated.INSTANCE);
        fileUploadingManifestFileObserver.update(request, ManifestFile.MANIFEST, FileGenerated.INSTANCE);

    }

    private void assertUploadedFiles(Multimap<KickoffRequest, ManifestFile> createdFiles) {
        //when
        for (KickoffRequest kickoffRequest : createdFiles.keys()) {
            for (ManifestFile manifestFile : createdFiles.get(kickoffRequest)) {
                fileUploadingManifestFileObserver.update(kickoffRequest, manifestFile, FileGenerated.INSTANCE);
            }
        }

        //then
        assertTrue(fileUploader.getUploadedFiles().equals(createdFiles));
    }

}