package org.digitalforge.monobuild.command;

import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Singleton;

import picocli.CommandLine;

import org.digitalforge.monobuild.Monobuild;

@Singleton
@CommandLine.Command(name = "monobuild", description = "Run monobuild")
public class MonobuildCommand implements Callable<Integer> {

    private final Monobuild monobuild;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help and exit")
    private boolean help;

    @Inject
    public MonobuildCommand(Monobuild monobuild) {
        this.monobuild = monobuild;
    }

    @Override
    public Integer call() {
        return monobuild.buildTest();
    }

    @CommandLine.Command(name = "graph", description = "Find and print the graph of the monorepo")
    public Integer graph(@CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help and exit") boolean help) {
        return monobuild.graph();
    }

    @CommandLine.Command(name = "deploy", description = "Deploy changed projects in the monorepo")
    public Integer deploy() {
        return monobuild.deploy();
    }

}
