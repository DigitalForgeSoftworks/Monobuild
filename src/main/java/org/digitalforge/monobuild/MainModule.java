package org.digitalforge.monobuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.digitalforge.monobuild.helper.RepoHelper;

public class MainModule extends AbstractModule {

    private static final int CI_THREAD_COUNT = 1;
    private static final int DEFAULT_THREAD_COUNT = 1;

    @Override
    protected void configure() {
    }

    @Provides
    @Named("ci")
    @Singleton
    public Boolean provideCI() {
        return Boolean.parseBoolean(System.getenv("CI"));
    }

    @Provides
    @Named("repoDir")
    @Singleton
    public Path provideRepoDir() {
        Path dir = Path.of(System.getProperty("user.dir"));
        do {
            Path file = dir.resolve(".monobuild");
            if (Files.exists(file) && Files.isDirectory(file)) {
                return dir;
            }
            dir = dir.getParent();
        } while (dir.getParent() != null);
        throw new RuntimeException("Cannot find .monobuild directory in parent directories: " + System.getProperty("user.dir"));
    }

    @Provides
    @Named("outputDir")
    @Singleton
    public Path provideOutputDir() throws IOException {
        Path tmpDir = Path.of("/tmp/monobuild");
        if (!Files.exists(tmpDir)) {
            Files.createDirectories(tmpDir);
        }
        return tmpDir;
    }

    @Provides
    @Named("logDir")
    @Singleton
    public Path provideLogDir(@Named("outputDir") Path tmpDir) throws IOException {
        Path logDir = tmpDir.resolve("logs");
        if (!Files.exists(logDir)) {
            Files.createDirectories(logDir);
        }
        return logDir;
    }

    @Provides
    @Named("threadCount")
    @Singleton
    public Integer provideThreadCount(@Named("ci") Boolean ci) {
        if (ci) {
            return CI_THREAD_COUNT;
        } else {
            return DEFAULT_THREAD_COUNT;
        }
    }

    @Provides
    @Named("oldGitRef")
    @Singleton
    public String provideOldGitRef(@Named("repoDir") Path repoDir, RepoHelper repoHelper) {

        String mainBranch = repoHelper.getMainBranchName(repoDir);
        boolean main = Objects.equals(mainBranch, System.getenv("CIRCLE_BRANCH"));

        if (main) {
            return "refs/heads/" + mainBranch + "~";
        } else {
            return "refs/heads/" + mainBranch;
        }

    }

}
