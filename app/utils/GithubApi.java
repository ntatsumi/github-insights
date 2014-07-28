package utils;

import com.fasterxml.jackson.databind.JsonNode;
import model.GithubRepo;

public class GithubApi {
    public static final String MAX_PAGE_SIZE = "100";

    public static GithubRepo[] unmarshallGithubRepo(String org, JsonNode reposJson) throws IllegalArgumentException {
        GithubRepo[] repoObjects;
        if(reposJson != null && reposJson.isArray()) {
            repoObjects = new GithubRepo[reposJson.size()];
            int counter = 0;
            for (final JsonNode repoJson : reposJson) {
                GithubRepo repoObj = new GithubRepo(org);
                repoObj.setName(repoJson.get("name").asText());
                repoObj.setUrl(repoJson.get("url").asText());
                repoObjects[counter] = repoObj;
                counter++;
            }
        } else {
            throw new IllegalArgumentException("The input Json cannot be null and must be an array");
        }
        return repoObjects;
    }

    public static int getPullRquestsCount(JsonNode pullReqsJson) {
        if(pullReqsJson != null && pullReqsJson.isArray()) {
            return pullReqsJson.size();
        }
        return -1;
    }
}
