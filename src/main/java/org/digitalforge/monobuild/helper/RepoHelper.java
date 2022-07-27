package org.digitalforge.monobuild.helper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;

import org.digitalforge.monobuild.logging.console.Console;
import org.digitalforge.sneakythrow.SneakyThrow;

@Singleton
public class RepoHelper {

    private final Console console;

    @Inject
    public RepoHelper(Console console) {
        this.console = console;
    }

    public String getFileContents(File gitDir, String gitRef, String filePath) throws IOException {

        File file = RepositoryCache.FileKey.lenient(gitDir, FS.DETECTED).getFile();
        try (Repository repo = new RepositoryBuilder().setGitDir(file).build();
             Git git = new Git(repo);
             RevWalk walk = new RevWalk(repo);
             ObjectReader reader = repo.newObjectReader();
             TreeWalk treeWalk = TreeWalk.forPath(git.getRepository(), filePath,
                     walk.parseCommit(repo.resolve(gitRef)).getTree())) {
            return new String(reader.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
        } catch (NullPointerException exception) {
            return null;
        }

    }

    public String getGitHash(File gitDir) {

        File file = RepositoryCache.FileKey.lenient(gitDir, FS.DETECTED).getFile();
        try (Repository repo = new RepositoryBuilder().setGitDir(file).build();
             RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(repo.resolve("HEAD"));
            return commit.getName();
        } catch (NullPointerException exception) {
            return null;
        } catch (IOException e) {
            throw SneakyThrow.sneak(e);
        }

    }

    public String getMainBranchName(Path repoDir) {

        try {

            Process p = new ProcessBuilder()
                .command("git", "branch", "-r")
                .directory(repoDir.toFile())
                .start();

            try(BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String branch = br.lines()
                    .map(line -> line.trim())
                    .filter(line -> line.matches(".*/HEAD\\s+->.*"))
                    .map(line -> line.substring(line.lastIndexOf('>') + 1).trim())
                    .map(line -> line.substring(line.lastIndexOf('/') + 1))
                    .findFirst()
                    .orElse(null);

                return branch;

            }

        } catch(IOException ex) {
            throw SneakyThrow.sneak(ex);
        }


    }

    public Collection<String> diff(File repoDir, String oldRef, String newRef) {

        Set<String> changedFiles = new TreeSet<>();

        Collection<String> workingChanges = runCommand(repoDir, "git", "diff", "--name-status")
            .stream()
            .flatMap(new DiffSplitter())
            .collect(Collectors.toList());
        Collection<String> stagedChanges = runCommand(repoDir, "git", "diff", "--name-status", "--cached")
            .stream()
            .flatMap(new DiffSplitter())
            .collect(Collectors.toList());
        Collection<String> branchChanges = runCommand(repoDir, "git", "diff", "--name-status", oldRef + ".." + newRef)
            .stream()
            .flatMap(new DiffSplitter())
            .collect(Collectors.toList());

        changedFiles.addAll(workingChanges);
        changedFiles.addAll(stagedChanges);
        changedFiles.addAll(branchChanges);

        return changedFiles;

    }

    private Collection<String> runCommand(File directory, String... command) {

        try {

            Process p = new ProcessBuilder()
                .command(command)
                .directory(directory)
                .start();

            try(BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String error = br.lines().collect(Collectors.joining("\n"));
                if(!error.isBlank()) {
                    console.error(error);
                    System.exit(1);
                }
            }

            try(BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                return br.lines().collect(Collectors.toList());
            }

        } catch(Exception ex) {
            throw SneakyThrow.sneak(ex);
        }

    }

    private class DiffSplitter implements Function<String, Stream<String>> {

        @Override
        public Stream<String> apply(String s) {
            String[] split = s.split("\t");
            if(split.length == 2) {
                return Stream.of(split[1]);
            } else {
                return Stream.of(split[1], split[2]);
            }
        }

    }

}
