package org.digitalforge.monobuild.circleci.workflow;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Job {

    private String name;
    private String projectdir;
    private List<String> context;
    private List<String> requires;

    public String getName() {
        return name;
    }

    public Job setName(String name) {
        this.name = name;
        return this;
    }

    public String getProjectdir() {
        return projectdir;
    }

    public Job setProjectdir(String projectdir) {
        this.projectdir = projectdir;
        return this;
    }

    public List<String> getContext() {
        return context;
    }

    public Job setContext(List<String> context) {
        this.context = context;
        return this;
    }

    public List<String> getRequires() {
        return requires;
    }

    public Job setRequires(List<String> requires) {
        this.requires = requires;
        return this;
    }

}
