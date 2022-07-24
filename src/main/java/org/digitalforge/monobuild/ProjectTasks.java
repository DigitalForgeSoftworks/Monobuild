package org.digitalforge.monobuild;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.pty4j.PtyProcessBuilder;

import org.digitalforge.monobuild.helper.StreamHelper;
import org.digitalforge.monobuild.logging.console.Console;
import org.digitalforge.sneakythrow.SneakyThrow;

@Singleton
public class ProjectTasks {

    private final Path logDir;
    private final Path repoDir;
    private final String gitHash;
    private final Console console;
    private final StreamHelper streamHelper;

    @Inject
    public ProjectTasks(@Named("logDir") Path logDir,
                        @Named("repoDir") Path repoDir,
                        @Named("gitHash") String gitHash,
                        Console console,
                        StreamHelper streamHelper) {
        this.logDir = logDir;
        this.repoDir = repoDir;
        this.gitHash = gitHash;
        this.console = console;
        this.streamHelper = streamHelper;
    }

    public void buildProject(Project project) {

        timedSafeExecute(project, start -> {

            // Use JetBrains' PtyProcessBuilder to capture colored output
            PtyProcessBuilder processBuilder = new PtyProcessBuilder()
                .setCommand(new String[]{"sh", "-c", "./.scripts/build"})
                .setDirectory(project.path.toString())
                .setRedirectErrorStream(true);
            processBuilder.setEnvironment(new HashMap<>(System.getenv()));

            Process process = processBuilder.start();

            // Stream the output to a log file and return a reference to the OutputStream
            Path logFile = logDir.resolve("build-" + project.name + ".log");
            CompletableFuture<String> output = streamHelper.forkToFileAndString(process.getInputStream(), logFile);

            if (process.waitFor() != 0) {
                System.out.println(output.get());
                long elapsed = System.currentTimeMillis() - start;
                console.errorLeftRight("Failed to build (%s)", console.formatMillis(elapsed), project.name);
                System.exit(1);
            } else {
                long elapsed = System.currentTimeMillis() - start;
                console.infoLeftRight("Finished building (%s)", console.formatMillis(elapsed), project.name);
            }

        });

    }

    public void testProject(Project project) {

        timedSafeExecute(project, start -> {

            // Use JetBrains' PtyProcessBuilder to capture colored output
            PtyProcessBuilder processBuilder = new PtyProcessBuilder()
                .setCommand(new String[]{"sh", "-c", "./.scripts/test"})
                .setDirectory(project.path.toString())
                .setRedirectErrorStream(true);
            processBuilder.setEnvironment(new HashMap<>(System.getenv()));

            Process process = processBuilder.start();

            // Stream the output to a log file and return a reference to the OutputStream
            Path logFile = logDir.resolve("test-" + project.name + ".log");
            CompletableFuture<String> output = streamHelper.forkToFileAndString(process.getInputStream(), logFile);

            if (process.waitFor() != 0) {
                System.out.println(output.get());
                long elapsed = System.currentTimeMillis() - start;
                console.errorLeftRight("Failed to test (%s)", console.formatMillis(elapsed), project.name);
                System.exit(1);
            } else {
                long elapsed = System.currentTimeMillis() - start;
                console.infoLeftRight("Finished testing (%s)", console.formatMillis(elapsed), project.name);
            }

        });

    }

    private void timedSafeExecute(Project project, TimedTask<Long> timedTask) {

        long start = System.currentTimeMillis();

        try {
            timedTask.accept(start);
        } catch (InterruptedException e) {
            long elapsed = System.currentTimeMillis() - start;
            console.errorLeftRight("Interrupted while executing (%s)", console.formatMillis(elapsed), repoDir.relativize(project.path));
        } catch (IOException | ExecutionException e) {
            long elapsed = System.currentTimeMillis() - start;
            console.errorLeftRight("Exception while executing (%s)", console.formatMillis(elapsed), repoDir.relativize(project.path));
            e.printStackTrace();
            throw SneakyThrow.sneak(e);
        }

    }

    @FunctionalInterface
    private interface TimedTask<T> {

        void accept(T value) throws IOException, InterruptedException, ExecutionException;

    }

}
