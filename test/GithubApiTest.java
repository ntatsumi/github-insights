import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.GithubRepo;
import org.junit.Test;
import utils.GithubApi;

import java.io.File;
import java.net.URL;

import static org.fest.assertions.Assertions.assertThat;


public class GithubApiTest {

    @Test
    public void testReposJsonExtraction() throws Exception {
        //json with multiple repos
        URL url = this.getClass().getResource("/github-repos.json");
        File repos = new File(url.getFile());
        ObjectMapper mapper = new ObjectMapper();
        JsonParser parser = mapper.getFactory().createParser(repos);
        JsonNode reposJson = mapper.readTree(parser);
        GithubRepo[] githubRepos = GithubApi.unmarshallGithubRepo("Netflix", reposJson);

        assertThat(githubRepos.length).isEqualTo(reposJson.size());

        for(GithubRepo githubRepo : githubRepos) {
            assertThat(githubRepo.getUrl()).startsWith("https://api.github.com/repos/Netflix/");
        }

        //json with one repo
        url = this.getClass().getResource("/github-repo.json");
        repos = new File(url.getFile());
        parser = mapper.getFactory().createParser(repos);
        reposJson = mapper.readTree(parser);

        assertThat(1).isEqualTo(reposJson.size());

        assertThat(githubRepos[0].getUrl()).startsWith("https://api.github.com/repos/Netflix/");
    }

    @Test
    public void checkPullRequestsCount() throws Exception {
        URL url = this.getClass().getResource("/github-pullrequests-30.json");
        File repos = new File(url.getFile());
        ObjectMapper mapper = new ObjectMapper();
        JsonParser parser = mapper.getFactory().createParser(repos);
        JsonNode pullReqsJson = mapper.readTree(parser);
        int pullReqs = GithubApi.getPullRquestsCount(pullReqsJson);
        assertThat(pullReqs).isEqualTo(30);
    }
}
