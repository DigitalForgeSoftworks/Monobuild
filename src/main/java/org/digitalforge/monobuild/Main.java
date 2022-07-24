package org.digitalforge.monobuild;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.Guice;
import com.google.inject.Injector;
import picocli.CommandLine;

import org.digitalforge.monobuild.command.MonobuildCommand;

@Singleton
public class Main {

    private final MonobuildCommand monobuildCommand;

    @Inject
    public Main(MonobuildCommand monobuildCommand) {
        this.monobuildCommand = monobuildCommand;
    }

    public static void main(final String[] args) {

        Injector injector = Guice.createInjector(new MainModule());
        Main main = injector.getInstance(Main.class);

        int exitCode = main.start(args);

        System.exit(exitCode);

    }

    public int start(String... args) {

        // Inject all the commands and organize subcommands here
        CommandLine commandLine = new CommandLine(monobuildCommand);
        return commandLine.execute(args);

    }

}
