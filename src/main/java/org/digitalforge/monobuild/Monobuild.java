package org.digitalforge.monobuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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

    private final Boolean ci;
    private final Path tmpDir;
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
            @Named("tmpDir") Path tmpDir,
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
        this.tmpDir = tmpDir;
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

    public int buildTest() {

        outputHeader();

        // Start the timer
        long start = System.currentTimeMillis();

        try {

            List<Project> allProjects = projectHelper.listAllProjects(repoDir);
            Collection<String> changedFiles = repoHelper.diff(repoDir.toFile(), oldGitRef, Constants.HEAD);
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
            ExecutorService testThreadPool = threadHelper.newThreadPool("tester", threadCount);
            dag.retainAll(projectsToBuild);
            DagTraversalTask<Project> buildTask = new DagTraversalTask<>(dag, projectTasks::buildProject, buildThreadPool);

            if (!buildTask.awaitTermination(30, TimeUnit.MINUTES)) {
                console.error("Building failed");
                return 1;
            }

            console.header("Testing");

            DagTraversalTask<Project> testTask = new DagTraversalTask<>(dag, projectTasks::testProject, testThreadPool);

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
            String dagJson = mapper.writeValueAsString(outputMap);
            Files.writeString(tmpDir.resolve("projects/dag.json"), dagJson);

        } catch (IOException e) {
            throw SneakyThrow.sneak(e);
        }

        return 0;

    }

    public int deploy() {

        outputHeader();

        long start = System.currentTimeMillis();

        try {

            List<Project> allProjects = projectHelper.listAllProjects(repoDir);
            Collection<String> changedFiles = repoHelper.diff(repoDir.toFile(), oldGitRef, Constants.HEAD);
            List<Project> changedProjects = projectHelper.getChangedProjects(allProjects, changedFiles, repoDir);
            Dag<Project> dag = projectHelper.getDependencyTree(allProjects, repoDir);

            // Build the affected projects, the projects that they depend on, and the projects that depend on them
            List<Project> projectsToBuild = changedProjects.stream()
                .flatMap(p -> Streams.concat(dag.getAncestors(p).stream(), dag.getDescendants(p).stream(), Stream.of(p)))
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
            dag.retainAll(projectsToBuild);
            DagTraversalTask<Project> buildTask = new DagTraversalTask<>(dag, projectTasks::deployProject, deploymentThreadPool);

            if (!buildTask.awaitTermination(30, TimeUnit.MINUTES)) {
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
            Path pathsDir = tmpDir.resolve("projects");
            if (!Files.exists(pathsDir)) {
                Files.createDirectories(pathsDir);
            }
            Files.writeString(pathsDir.resolve(fileName), text);
        } catch (IOException e) {
            throw SneakyThrow.sneak(e);
        }
    }

}
