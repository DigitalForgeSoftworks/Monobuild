package org.digitalforge.monobuild;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import org.digitalforge.sneakythrow.SneakyThrow;

public class Command {

    private final Process process;
    private String output;

    public Command(Process process) {
        this.process = process;
    }

    /**
     * This will run a command in your current working directory
     * All output can be accessed with Process.getInputStream()
     */
    public static Command run(final String... command) {
        return run(Path.of(System.getProperty("user.dir")).toFile(), command);
    }

    public static Command run(File dir, final String... command) {
        return run(dir, Map.of(), command);
    }

    /**
     * This will run a command in a given directory
     * All output can be accessed with Process.getInputStream()
     */
    public static Command run(File dir, Map<String, String> env, final String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder()
                    .directory(dir)
                    .command(command)
                    .redirectErrorStream(true);
            builder.environment().putAll(env);
            return new Command(builder.start());
        } catch (IOException e) {
            throw SneakyThrow.sneak(e);
        }
    }

    public int getExitCode() {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            throw SneakyThrow.sneak(e);
        }
    }

    /**
     * This method waits for the completion of the Process and returns all lines of the Process's output.
     * <p>
     * If the Process has already completed and the full output has already been collected, it will be immediately
     * returned.
     */
    public String getOutput() {
        if (output != null) {
            return output;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            this.output = br.lines().collect(Collectors.joining("\n"));
            return output;
        } catch (IOException e) {
            throw SneakyThrow.sneak(e);
        }
    }

}
