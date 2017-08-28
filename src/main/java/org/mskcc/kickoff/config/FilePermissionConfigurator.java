package org.mskcc.kickoff.config;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.util.Constants;

import java.io.File;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.*;

public class FilePermissionConfigurator {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private static final Set<PosixFilePermission> DIRperms = new HashSet<>();
    private static final Set<PosixFilePermission> FILEperms = new HashSet<>();

    static {
        DIRperms.add(OWNER_READ);
        DIRperms.add(OWNER_WRITE);
        DIRperms.add(OWNER_EXECUTE);

        DIRperms.add(GROUP_READ);
        DIRperms.add(GROUP_WRITE);
        DIRperms.add(GROUP_EXECUTE);

        DIRperms.add(OTHERS_READ);
        DIRperms.add(OTHERS_EXECUTE);

        FILEperms.add(OWNER_READ);
        FILEperms.add(OWNER_WRITE);

        FILEperms.add(GROUP_READ);
        FILEperms.add(GROUP_WRITE);

        FILEperms.add(OTHERS_READ);
    }

    public static void setPermissions(File f) {
        try {
            if (f.isDirectory()) {
                try {
                    Files.setPosixFilePermissions(f.toPath(), DIRperms);
                } catch (AccessDeniedException e) {
                }
                for (Path path : Files.newDirectoryStream(f.toPath())) {
                    File file = path.toFile();
                    if (file.isDirectory()) {
                        try {
                            Files.setPosixFilePermissions(Paths.get(file.getAbsolutePath()), DIRperms);
                        } catch (AccessDeniedException e) {
                        }
                        setPermissions(file);
                    } else {
                        try {
                            Files.setPosixFilePermissions(Paths.get(file.getAbsolutePath()), FILEperms);
                        } catch (AccessDeniedException e) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(e.getMessage(), e);
        }
    }
}
