package org.digitalforge.monobuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Streams;
import me.alexjs.dag.Dag;
import me.alexjs.dag.DagTraversalTask;
import org.eclipse.jgit.lib.Constants;

import org.digitalforge.monobuild.helper.ProjectHelper;
import org.digitalforge.monobuild.helper.RepoHelper;
import org.digitalforge.monobuild.helper.ThreadHelper;
import org.digitalforge.monobuild.logging.console.Console;
import org.digitalforge.sneakythrow.SneakyThrow;

@Singleton
public class Monobuild {

    //TODO: make this part of monobuildConfig.json so you can configure the monobuild
    private final static String MAIN = "main"; //or 'master' for legacy githubs

    private final Boolean ci;
    private final Path outputDir;
    private final Path logDir;
    private final Path repoDir;
    private final Integer threadCount;
    private final String oldGitRef;
    private final Console console;
    private final ProjectTasks projectTasks;
    private final ProjectHelper projectHelper;
    private final RepoHelper repoHelper;
    private final ThreadHelper threadHelper;

    @Inject
    public Monobuild(
            @Named("ci") Boolean ci,
            @Named("outputDir") Path outputDir,
            @Named("logDir") Path logDir,
            @Named("repoDir") Path repoDir,
            @Named("threadCount") Integer threadCount,
            @Named("oldGitRef") String oldGitRef,
            Console console,
            ProjectTasks projectTasks,
            ProjectHelper projectHelper,
            RepoHelper repoHelper,
            ThreadHelper threadHelper
    ) {
        this.ci = ci;
        this.outputDir = outputDir;
        this.logDir = logDir;
        this.repoDir = repoDir;
        this.threadCount = threadCount;
        this.console = console;
        this.oldGitRef = oldGitRef;
        this.projectTasks = projectTasks;
        this.projectHelper = projectHelper;
        this.repoHelper = repoHelper;
        this.threadHelper = threadHelper;
    }

    public int buildTest(String[] args, String baseRef) {

        if(baseRef == null) {
            baseRef = MAIN;
        }

        outputHeader();

        // Start the timer
        long start = System.currentTimeMillis();

        try {

            List<Project> allProjects = projectHelper.listAllProjects(repoDir);
            //TODO: modify main branch to be a configuration thing(monobuildConfig.json perhaps as yml sucks)
            Collection<String> changedFiles = repoHelper.diff(repoDir.toFile(), oldGitRef, Constants.HEAD, MAIN);
            List<Project> changedProjects = projectHelper.getChangedProjects(allProjects, changedFiles, repoDir);
            Dag<Project> dag = projectHelper.getDependencyTree(allProjects, repoDir);

            // Build the affected projects, the projects that they depend on, and the projects that depend on them
            List<Project> projectsToBuild = changedProjects.stream()
                    .flatMap(p -> Streams.concat(dag.getAncestors(p).stream(), dag.getDescendants(p).stream(), Stream.of(p)))
                    .distinct()
                    .sorted(Comparator.comparing(p -> p.name))
                    .collect(Collectors.toList());

            StringJoiner changedJoiner = new StringJoiner("\n", "", "\n");
            StringJoiner builtJoiner = new StringJoiner("\n", "", "\n");

            console.header("Changed projects");
            if (!changedProjects.isEmpty()) {
                for (Project project : changedProjects) {
                    Path path = repoDir.relativize(project.path);
                    console.infoLeftRight(project.name, path);
                    changedJoiner.add(path.toString());
                }
            } else {
                console.info("No projects changed");
                return 0;
            }

            console.header("Affected projects");
            if (!projectsToBuild.isEmpty()) {
                for (Project project : projectsToBuild) {
                    Path path = repoDir.relativize(project.path);
                    console.infoLeftRight(project.name, path);
                    builtJoiner.add(path.toString());
                }
            } else {
                console.info("No projects to test");
                return 0;
            }

            // Write to files so we can see these lists after monobuild is complete
            writeProjectList("changed.txt", changedJoiner.toString());
            writeProjectList("built.txt", builtJoiner.toString());

            console.header("Building");

            ExecutorService buildThreadPool = threadHelper.newThreadPool("builder", threadCount);
            dag.retainAll(projectsToBuild);
            BiConsumer<Project, String[]> builder = (project, args2) -> {projectTasks.buildProject(project, args2);};
            DagTraversalTask<Project> buildTask = new DagTraversalTask<>(dag, new BiConsumerTask(args, builder), buildThreadPool);

            if (!buildTask.awaitTermination(30, TimeUnit.MINUTES)) {
                console.error("Building failed");
                return 1;
            }

            console.header("Testing");

            ExecutorService testThreadPool = threadHelper.newThreadPool("tester", threadCount);
            BiConsumer<Project, String[]> tester = (project, args2) -> {projectTasks.testProject(project, args2);};
            DagTraversalTask<Project> testTask = new DagTraversalTask<>(dag, new BiConsumerTask(args, tester), testThreadPool);

            if (!testTask.awaitTermination(30, TimeUnit.MINUTES)) {
                console.error("Testing failed");
                return 1;
            }

        } catch (InterruptedException | IOException e) {
            throw SneakyThrow.sneak(e);
        }

        // Stop the timer
        console.footer();
        console.infoLeftRight("Success! Total time", console.formatMillis(System.currentTimeMillis() - start));
        console.info("You can view all project build logs in " + logDir);

        return 0;

    }

    public int graph() {

        try {

            List<Project> allProjects = projectHelper.listAllProjects(repoDir);
            Dag<Project> dag = projectHelper.getDependencyTree(allProjects, repoDir);

            // Turn it into a more human-readable map, print it to the console, and save it as a json file

            console.infoLeftRight("Project", "Dependency");
            console.footer();

            Map<String, List<String>> outputMap = new HashMap<>();
            for (Project project : dag.getNodes()) {

                List<String> dependencies = dag.getIncoming(project).stream()
                        .map(p -> p.name)
                        .collect(Collectors.toList());
                outputMap.put(project.name, dependencies);

                if (!dependencies.isEmpty()) {
                    for (String dependency : dependencies) {
                        console.infoLeftRight(project.name, dependency);
                    }
                } else {
                    console.infoLeftRight(project.name, "");
                }

            }

            // Write it to a file
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(outputMap);
            Files.writeString(outputDir.resolve("projects/graph.json"), json);

        } catch (IOException e) {
            throw SneakyThrow.sneak(e);
        }

        return 0;

    }

    public int deploy(String[] args, String baseRef) {

        if(baseRef == null) {
            baseRef = MAIN;
        }

        outputHeader();

        long start = System.currentTimeMillis();

        try {

            List<Project> allProjects = projectHelper.listAllProjects(repoDir);
            Collection<String> changedFiles = repoHelper.diff(repoDir.toFile(), oldGitRef, Constants.HEAD, baseRef);
            List<Project> changedProjects = projectHelper.getChangedProjects(allProjects, changedFiles, repoDir);
            Dag<Project> graph = projectHelper.getDependencyTree(allProjects, repoDir);

            // Build the affected projects, the projects that they depend on, and the projects that depend on them
            List<Project> projectsToBuild = changedProjects.stream()
                .flatMap(p -> Streams.concat(graph.getAncestors(p).stream(), graph.getDescendants(p).stream(), Stream.of(p)))
                .distinct()
                .sorted(Comparator.comparing(p -> p.name))
                .collect(Collectors.toList());

            List<Project> projectsToDeploy = projectsToBuild.stream()
                .filter(project -> Files.isExecutable(project.path.resolve("deploy.sh")))
                .collect(Collectors.toList());

            StringJoiner deployJoiner = new StringJoiner("\n", "", "\n");

            console.header("Pending Deployment");
            if (!projectsToDeploy.isEmpty()) {
                for (Project project : projectsToDeploy) {
                    Path path = repoDir.relativize(project.path);
                    console.infoLeftRight(project.name, path);
                    deployJoiner.add(path.toString());
                }
            } else {
                console.info("No projects to deploy");
                return 0;
            }

            writeProjectList("deployed.txt", deployJoiner.toString());

            console.header("Deploying");

            ExecutorService deploymentThreadPool = threadHelper.newThreadPool("deployment", threadCount);
            graph.retainAll(projectsToBuild);
            BiConsumer<Project, String[]> deployer = (project, args2) -> {projectTasks.testProject(project, args2);};
            DagTraversalTask<Project> deployTask = new DagTraversalTask<>(graph, new BiConsumerTask(args, deployer), deploymentThreadPool);

            if (!deployTask.awaitTermination(30, TimeUnit.MINUTES)) {
                console.error("Deployment failed");
                return 1;
            }

        } catch (InterruptedException | IOException e) {
            throw SneakyThrow.sneak(e);
        }

        // Stop the timer
        console.footer();
        console.infoLeftRight("Success! Total time", console.formatMillis(System.currentTimeMillis() - start));
        console.info("You can view all project deployment logs in " + logDir);

        return 0;

    }

    public int version() {
        outputHeader();
        return 0;
    }

    private void outputHeader() {

        String version = getClass().getPackage().getImplementationVersion();
        if(version == null) {
            version = "development";
        }

        // Print some logs
        console.infoLeftRight("Monobuild (" + version + ")", "https://github.com/DigitalForgeSoftworks/monobuild");
        console.infoLeftRight("CI", ci);
        console.infoLeftRight("Repo directory", repoDir);
        console.infoLeftRight("Log directory", logDir);
        console.infoLeftRight("Diff context", oldGitRef + ".." + Constants.HEAD);

    }

    private void writeProjectList(String fileName, String text) {
        try {
            Path pathsDir = outputDir.resolve("projects");
            if (!Files.exists(pathsDir)) {
                Files.createDirectories(pathsDir);
            }
            Files.writeString(pathsDir.resolve(fileName), text);
        } catch (IOException e) {
            throw SneakyThrow.sneak(e);
        }
    }

    private static class BiConsumerTask implements Consumer<Project> {

        private final String[] args;
        private final BiConsumer<Project, String[]> biconsumer;

        private BiConsumerTask(String[] args, BiConsumer biconsumer) {
            this.args = args;
            this.biconsumer = biconsumer;
        }

        @Override
        public void accept(Project project) {
            biconsumer.accept(project, args);
        }

    }

}
