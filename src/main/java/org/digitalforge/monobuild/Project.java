package org.digitalforge.monobuild;

import java.nio.file.Path;
import java.util.Objects;

public class Project implements Comparable<Project> {

    public final String name;
    public final Path path;

    public Project(String name, Path path) {
        this.name = name;
        this.path = path;
    }

    @Override
    public int compareTo(Project other) {
        return name.compareTo(other.name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Project project = (Project) o;

        if (!Objects.equals(name, project.name)) return false;
        return Objects.equals(path, project.path);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (path != null ? path.hashCode() : 0);
        return result;
    }

}
