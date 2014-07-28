package model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;

public class GithubRepo {

    public static final String ORG = "org";
    public static final String NAME = "name";
    public static final String URL = "url";
    public static final String PULL_REQUEST_COUNTS = "pullRequestsCount";

    private String org = null;
    private String name = null;
    private String url = null;
    private int pullRequestsCount = 0;

    public GithubRepo(String org) {
        this.org = org;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getPullRequestsCount() {
        return pullRequestsCount;
    }

    public void setPullRequestsCount(int pullRequestsCount) {
        this.pullRequestsCount = pullRequestsCount;
    }

    public ObjectNode toJson() {
        ObjectNode json = Json.newObject();
        json.put(ORG, getOrg());
        json.put(NAME, getName());
        json.put(URL, getUrl());
        json.put(PULL_REQUEST_COUNTS, getPullRequestsCount());
        return json;
    }

    public String toString() {
        return toJson().toString();
    }
}
