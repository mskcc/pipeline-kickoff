package org.mskcc.kickoff.generator;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileExistenceOutputDirValidatorTest {
    private final FileExistenceOutputDirValidator fileExistenceOutputDirValidator = new
            FileExistenceOutputDirValidator();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void whenOutputDirIsNull_shouldReturnFalse() {
        boolean isValid = fileExistenceOutputDirValidator.test(null);

        assertFalse(isValid);
    }

    @Test
    public void whenOutputDirIsEmpty_shouldReturnFalse() {
        boolean isValid = fileExistenceOutputDirValidator.test("");

        assertFalse(isValid);
    }

    @Test
    public void whenOutputDirDoesntExist_shouldReturnFalse() {
        String notExistentDir = "/some/path/which/doesnt/exist";
        boolean isValid = fileExistenceOutputDirValidator.test(notExistentDir);

        assertFalse(isValid);
    }

    @Test
    public void whenOutputDirExistsButIsNotDirectory_shouldReturnFalse() throws Exception {
        String noDirPath = "someFile";
        File file = folder.newFile(noDirPath);

        boolean isValid = fileExistenceOutputDirValidator.test(file.getAbsolutePath());

        assertFalse(isValid);
    }

    @Test
    public void whenOutputDirExistsAndIsDirectory_shouldReturnTrue() throws Exception {
        String noDirPath = "existentDir";
        File existentFolder = folder.newFolder(noDirPath);

        boolean isValid = fileExistenceOutputDirValidator.test(existentFolder.getAbsolutePath());

        assertTrue(isValid);
    }
}