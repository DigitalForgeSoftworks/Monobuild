package org.digitalforge.monobuild.helper;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

import me.alexjs.dag.Dag;
import me.alexjs.dag.HashDag;

import org.digitalforge.monobuild.Project;
import org.digitalforge.monobuild.logging.console.Console;

@Singleton
public class ProjectHelper {

    private Console console;

    @Inject
    public ProjectHelper(Console console) {
        this.console = console;
    }

    public List<Project> listAllProjects(Path repoDir) throws IOException {

        List<String> lines = Files.readAllLines(repoDir.resolve(".monobuild/projects.txt"));
        lines.removeIf(l -> l.startsWith("#") || l.isBlank());

        FileSystem fs = FileSystems.getDefault();
        List<PathMatcher> exclude = new ArrayList<>();
        List<PathMatcher> matchers = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("!")) {
                exclude.add(fs.getPathMatcher("glob:" + repoDir.resolve(line.substring(1))));
            } else {
                matchers.add(fs.getPathMatcher("glob:" + repoDir.resolve(line)));
            }
        }

        // There's gotta be a better way to do this. This is kind of absurd
        Set<Project> projects = new HashSet<>();
        try (Stream<Path> walk = Files.walk(repoDir)) {
            List<Project> found = walk
                    .filter(path -> {
                        for (PathMatcher matcher : matchers) {
                            // If the path matches an included path, then we want to keep track of it
                            if (matcher.matches(path)) {
                                for (PathMatcher excludeMatcher : exclude) {
                                    // If the path also matches an excluded path, then filter it out
                                    if (excludeMatcher.matches(path)) {
                                        return false;
                                    }
                                }
                                // included && !excluded
                                return true;
                            }
                        }
                        return false;
                    })
                    .filter(d ->
                            Files.exists(d.resolve("settings.gradle"))
                            || Files.exists(d.resolve("settings.gradle.kts"))
                            || Files.exists(d.resolve("local_requirements.txt"))
                            || Files.exists(d.resolve("setup.cfg"))
                            || Files.exists(d.resolve("package.json")))
                    .map(repoDir::resolve)
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .map(dir -> new Project(dir.getFileName().toString(), dir))
                    .collect(Collectors.toList());

            projects.addAll(found);
        }

        return projects.stream()
                .sorted(Comparator.comparing(p -> p.name))
                .collect(Collectors.toList());

    }

    public List<Project> getChangedProjects(List<Project> projects, Collection<String> diffs, Path repoDir) {

        console.header("All projects discovered in monorepo");
        for(Project p : projects) {
            console.infoLeftRight(p.name, p.path);
        }
        Set<Project> changedProjects = new HashSet<>();
        for(String changedFile : diffs) {
            for (Project project : projects) {
                // If this changed file is in this project, then this project has been changed
                if (repoDir.resolve(changedFile).startsWith(project.path)) {
                    changedProjects.add(project);
                    break;
                }
            }
        }

        return changedProjects.stream()
            .sorted(Comparator.comparing(p -> p.name))
            .collect(Collectors.toList());
    }

    public Dag<Project> getDependencyTree(List<Project> projects, Path repoDir) throws IOException {

        Dag<Project> dag = new HashDag<>();
        for (Project project : projects) {

            dag.add(project);

            Path settings = repoDir.resolve(project.path.resolve("settings.gradle"));
            if (!Files.exists(settings)) {
                continue;
            }

            // Each "includeBuild" line represents an edge in the DAG
            try (Stream<String> lines = Files.lines(settings)) {
                lines.map(IncludeBuildMatcher.INSTANCE)
                        .filter(Matcher::matches)
                        .map(matcher -> matcher.group(1))
                        .map(project.path::resolve)
                        .map(repoDir::resolve)
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .sorted()
                        .map(dir -> new Project(dir.getFileName().toString(), dir))
                        .forEach(p -> dag.put(p, project));
            }

        }

        return dag;

    }

    private static class IncludeBuildMatcher implements Function<String, Matcher> {

        public static final IncludeBuildMatcher INSTANCE = new IncludeBuildMatcher();

        private static final Pattern REGEX = Pattern.compile("(?://#)?includeBuild \\(*['\"](.*)['\"]\\)*.*");

        @Override
        public Matcher apply(String s) {
            return REGEX.matcher(s);
        }

    }

}
