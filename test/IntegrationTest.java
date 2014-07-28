import controllers.Application;
import org.junit.*;

import play.test.*;
import play.libs.F.*;

import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;

public class IntegrationTest {

    private void testCommon(TestBrowser browser) {
        assertThat(browser.pageSource().contains("{"));
        assertThat(browser.pageSource().contains("}"));
    }

    @Test
    public void testIndex() {
        running(testServer(3333, fakeApplication(inMemoryDatabase())), HTMLUNIT, new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                browser.goTo("http://localhost:3333");
                testCommon(browser);
                assertThat(browser.pageSource()).contains(Application.MESSAGE_WELCOME);
            }
        });
    }

    @Test
    public void listRepos() {
        running(testServer(3333, fakeApplication(inMemoryDatabase())), HTMLUNIT, new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                browser.goTo("http://localhost:3333/Netflix/repos");
                testCommon(browser);
            }
        });
    }

    @Test
    public void listReposTop5() {
        running(testServer(3333, fakeApplication(inMemoryDatabase())), HTMLUNIT, new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                browser.goTo("http://localhost:3333/Netflix/repos/top5");
                testCommon(browser);
            }
        });
    }
}
