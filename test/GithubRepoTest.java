import com.fasterxml.jackson.databind.node.ObjectNode;
import model.GithubRepo;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;


public class GithubRepoTest {
    @Test
    public void testRepoModel() throws Exception {
        GithubRepo repo = new GithubRepo("Homebrew");
        repo.setName("homebrew");
        repo.setPullRequestsCount(833);
        repo.setUrl("https://api.github.com/repos/Homebrew/homebrew");
        ObjectNode json = repo.toJson();

        assertThat(json.get(GithubRepo.ORG).asText().equals("Homebrew"));
        assertThat(json.get(GithubRepo.NAME).asText().equals("homebrew"));
        assertThat(json.get(GithubRepo.PULL_REQUEST_COUNTS).asInt() == 833);
        assertThat(json.get(GithubRepo.URL).asText().equals("https://api.github.com/repos/Homebrew/homebrew"));
    }
}
