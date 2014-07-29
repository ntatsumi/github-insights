package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.GithubRepo;

import play.Logger;
import play.libs.F;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSAuthScheme;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.GithubApi;
import play.cache.Cache;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Vector;

public class Application extends Controller {

    public static Result index() {
        ObjectNode result = Json.newObject();
        return ok(result.put("message", MESSAGE_WELCOME));
    }

    public static Result setGithubAuth() {
        Http.RequestBody body = request().body();
        JsonNode input = body.asJson();
        if(input == null || input.get("username") == null || input.get("password") == null)
            return badRequest(Json.newObject().put("message", "Expecting Json request body with username and password"));
        String username = input.get("username").asText();
        String password = input.get("password").asText();
        Cache.set(CACHE_USERNAME, username);
        Cache.set(CACHE_PASSWORD, password);
        return ok(Json.newObject().put("message", "ok"));
    }

    public static Promise<Result> listRepos(final String org) {
        return listRepos(org, FULL_LIST);
    }

    public static Promise<Result> listReposTop(final String org, final Integer top) {
        return listRepos(org, top);
    }

    public static Promise<Result> listRepos(final String org, final int top) {
        if(org == null || org.trim().length() == 0) {
            return Promise.promise(new F.Function0<Result>() {
                public Result apply() throws Throwable {
                    return badRequest(Json.newObject()
                            .put("message", "Org missing in the request (e.g. /Netflix/repos)"));
                }

            });
        }

        String etag;
        if(isCacheAvailable(org, top))
            etag = (String)Cache.get(CACHE_GITHUB_ETAG_PREFIX + org);
        else
            etag = "";

        return WS.url(URL_GITHUB_API + "orgs/" + org + "/repos")
                .setAuth((String)Cache.get(CACHE_USERNAME), (String)Cache.get(CACHE_PASSWORD), WSAuthScheme.BASIC)
                .setFollowRedirects(true)
                .setHeader("Accept", HEADER_ACCEPT_GITHUB)
                .setHeader("If-None-Match", etag)
                .setQueryParameter("type", "public")
                .setQueryParameter("per_page", String.valueOf(GithubApi.MAX_PAGE_SIZE))
                .get()
                .map(
                        new Function<WSResponse, Result>() {
                            public Result apply(WSResponse response) {
                                Result evalResult = evalResponse(response, org, top);
                                if (evalResult != null)
                                    return evalResult;

                                JsonNode repos = response.asJson();
                                Logger.debug("Previous ETag: " + Cache.get(CACHE_GITHUB_ETAG_PREFIX + org));
                                Cache.set(CACHE_GITHUB_ETAG_PREFIX + org, response.getHeader("ETag"));

                                GithubRepo[] githubRepos;
                                try {
                                    githubRepos = GithubApi.unmarshallGithubRepo(org, repos);
                                    if (githubRepos == null)
                                        return internalServerError(Json.newObject()
                                                .put("message", ERROR_GITHUB_QUERY_PREFIX + repos.get("message").asText()));
                                } catch (Exception e) {
                                    return internalServerError(Json.newObject()
                                            .put("message", ERROR_GITHUB_QUERY_PREFIX + repos.get("message").asText()));
                                }

                                final Long listReposUuid = UUID.randomUUID().getLeastSignificantBits();
                                Logger.debug("listRepos UUID: " + listReposUuid);
                                for (GithubRepo githubRepo : githubRepos) {
                                    getPullRequests(listReposUuid, githubRepo, top, FIRST_PAGE, 0);
                                }

                                while (((Vector) Cache.get(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX + listReposUuid)).size() != 0) {
                                    Logger.debug("Active getPullRequests: "
                                            + ((Vector) Cache.get(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX
                                            + listReposUuid)).size());
                                    try {
                                        Thread.sleep(200);
                                    } catch (InterruptedException ex) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                                return ok(evalTop(listReposUuid, org, top));
                            }
                        }
                );
    }

    private static boolean isCacheAvailable(final String org, final int top) {
        if(top == FULL_LIST) {
            if(Cache.get(CACHE_RESULT_PREFIX + org) != null)
                return true;
        } else {
            if(Cache.get(CACHE_RESULT_PREFIX + org + top) != null)
                return true;
        }
        return false;
    }

    private static Promise<Result> getPullRequests(final long listReposUuid, final GithubRepo githubRepo, final int top,
                                                   final int page, final int pullRequests) {
        final Long getPullRequestsUuid = UUID.randomUUID().getLeastSignificantBits();
        Logger.debug("getPullRequests UUID: Adding " + getPullRequestsUuid);
        @SuppressWarnings("unchecked")
        Vector<Long> activeGetPullRequests = (Vector<Long>)Cache.get(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX + listReposUuid);
        if(activeGetPullRequests == null)
            activeGetPullRequests = new Vector<>();
        activeGetPullRequests.add(getPullRequestsUuid);
        Cache.set(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX + listReposUuid, activeGetPullRequests);

        try {
            return WS.url(githubRepo.getUrl() + "/pulls")
                    .setAuth((String)Cache.get(CACHE_USERNAME), (String)Cache.get(CACHE_PASSWORD), WSAuthScheme.BASIC)
                    .setFollowRedirects(true)
                    .setHeader("Accept", HEADER_ACCEPT_GITHUB)
                    .setQueryParameter("state", "all")
                    .setQueryParameter("per_page", String.valueOf(GithubApi.MAX_PAGE_SIZE))
                    .setQueryParameter("page", String.valueOf(page))
                    .get()
                    .map(
                            new Function<WSResponse, Result>() {
                                public Result apply(WSResponse response) {
                                    try {
                                        Result evalResult = evalResponse(response, githubRepo.getOrg(), top);
                                        if (evalResult != null)
                                            return evalResult;

                                        JsonNode json = response.asJson();
                                        int pullRequestsCountInPage = GithubApi.getPullRquestsCount(json);
                                        Logger.debug(listReposUuid + ".getPullRequests." + githubRepo.getName() + ": "
                                                + pullRequestsCountInPage);
                                        int totalPullRequests = pullRequests + pullRequestsCountInPage;
                                        if(pullRequestsCountInPage == GithubApi.MAX_PAGE_SIZE) {
                                            getPullRequests(listReposUuid, githubRepo, top, page + 1, totalPullRequests);
                                        } else {
                                            @SuppressWarnings("unchecked")
                                            TreeSet<Integer> pullRequestsRanking =
                                                    (TreeSet<Integer>) Cache.get(CACHE_PULLREQUESTS_RANKING_PREFIX + listReposUuid);
                                            if (pullRequestsRanking == null)
                                                pullRequestsRanking = new TreeSet<>();
                                            pullRequestsRanking.add(totalPullRequests);
                                            Cache.set(CACHE_PULLREQUESTS_RANKING_PREFIX + listReposUuid, pullRequestsRanking);

                                            ArrayNode result = (ArrayNode) Cache.get(CACHE_RESULT_PREFIX + listReposUuid);
                                            if (result == null) {
                                                final JsonNodeFactory factory = JsonNodeFactory.instance;
                                                result = factory.arrayNode();
                                            }
                                            githubRepo.setPullRequestsCount(totalPullRequests);
                                            result.add(githubRepo.toJson());
                                            Cache.set(CACHE_RESULT_PREFIX + listReposUuid, result);
                                        }

                                        return ok();
                                    } finally {
                                        Logger.debug("getPullRequests: Removing " + getPullRequestsUuid);
                                        ((Vector) Cache.get(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX + listReposUuid))
                                                .remove(getPullRequestsUuid);
                                    }
                                }
                            }
                    );
        } catch(Exception e) {
            ((Vector) Cache.get(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX + listReposUuid)).remove(getPullRequestsUuid);
            return null;
        }
    }

    private static ArrayNode evalTop(long listReposUuid, String org, int top) {
        ArrayNode jsonArrayNode = (ArrayNode) Cache.get(CACHE_RESULT_PREFIX + listReposUuid);
        Cache.set(CACHE_RESULT_PREFIX + org, jsonArrayNode);
        if(top == FULL_LIST)
            return jsonArrayNode;

        @SuppressWarnings("unchecked")
        TreeSet<Integer> pullRequestsRanking = (TreeSet<Integer>) Cache.get(CACHE_PULLREQUESTS_RANKING_PREFIX + listReposUuid);
        Iterator<Integer> pullRequestsRankingItr = pullRequestsRanking.descendingIterator();
        int descTop = 0;
        for(int i =0; i<top; i++) {
            if(pullRequestsRankingItr.hasNext())
                descTop = pullRequestsRankingItr.next();
        }
        final JsonNodeFactory factory = JsonNodeFactory.instance;
        ArrayNode jsonArrayNodeTop = factory.arrayNode();
        for (final JsonNode jsonNode : jsonArrayNode) {
            if(jsonNode.get(GithubRepo.PULL_REQUEST_COUNTS).asInt() >= descTop) {
                jsonArrayNodeTop.add(jsonNode);
            }
        }
        Cache.set(CACHE_RESULT_PREFIX + org + top, jsonArrayNodeTop);
        return jsonArrayNodeTop;
    }

    private static Result evalResponse(WSResponse response, String org, int top) {
        Logger.debug("HTTP Response Headers: " + response.getAllHeaders());

        if (response.getStatus() != 200 && response.getStatus() != 304) {
            JsonNode repos = response.asJson();
            if (repos.get("message") != null) {
                return internalServerError(Json.newObject()
                        .put("message"
                                , ERROR_GITHUB_QUERY_PREFIX
                                + repos.get("message").asText())
                        .put("github_status", response.getStatus()));
            } else {
                return internalServerError(Json.newObject()
                        .put("message"
                                , ERROR_GITHUB_QUERY_PREFIX + "Github request status code "
                                + response.getStatus())
                        .put("status_code", response.getStatus()));
            }
        } else if (response.getStatus() == 304) {
            Logger.info("Got 304 from GitHub. Returning cached result.");
            if(top == FULL_LIST) {
                return ok((ArrayNode) Cache.get(CACHE_RESULT_PREFIX + org));
            } else {
                return ok((ArrayNode) Cache.get(CACHE_RESULT_PREFIX + org + top));
            }
        }
        return null;
    }

    public static final String HEADER_ACCEPT_GITHUB = "application/vnd.github.+json";
    public static final String URL_GITHUB_API = "https://api.github.com/";
    public static final String CACHE_GITHUB_ETAG_PREFIX = "github.Etag.";
    public static final String CACHE_RESULT_PREFIX = "app.result.";
    public static final String CACHE_PULLREQUESTS_RANKING_PREFIX = "app.pullrequests.ranking.";
    public static final String CACHE_ACTIVE_GETPULLREQUESTS_PREFIX = "app.getPullRequests.";
    public static final String CACHE_USERNAME = "app.username";
    public static final String CACHE_PASSWORD = "app.password";
    public static final String MESSAGE_WELCOME = "Welcome to Github Insights!";
    public static final int FULL_LIST = -1;
    public static final int FIRST_PAGE = 1;
    public static final String ERROR_GITHUB_QUERY_PREFIX = "Github query failed - ";
}
