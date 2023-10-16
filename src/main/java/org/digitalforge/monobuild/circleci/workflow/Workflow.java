package org.digitalforge.monobuild.circleci.workflow;

import java.util.List;

public class Workflow {

    private List<? super Object> jobs;

    public List<? super Object> getJobs() {
        return jobs;
    }

    public Workflow setJobs(List<? super Object> jobs) {
        this.jobs = jobs;
        return this;
    }

}
