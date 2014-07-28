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
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Result;
import utils.GithubApi;
import play.cache.Cache;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Vector;

public class Application extends Controller {

    public static final String HEADER_GITHUB_ACCEPT = "application/vnd.github.+json";
    public static final String URL_GITHUB_API = "https://api.github.com/";
    public static final String CACHE_GITHUB_ETAG_PREFIX = "github.Etag.";
    public static final String CACHE_RESULT_PREFIX = "app.result.";
    public static final String CACHE_PULLREQUESTS_RANKING_PREFIX = "app.pullrequests.ranking.";
    public static final String CACHE_ACTIVE_GETPULLREQUESTS_PREFIX = "app.getPullRequests.";
    public static final String MESSAGE_WELCOME = "Welcome to Github Insights!";
    public static final int FULL_LIST = -1;
    public static final String ERROR_GITHUB_QUERY_PREFIX = "Github query failed - ";

    public static Result index() {
        ObjectNode result = Json.newObject();
        return ok(result.put("message", MESSAGE_WELCOME));
    }

    public static Promise<Result> listRepos(final String org) {
        return listRepos(org, FULL_LIST);
    }

    public static Promise<Result> listReposTop5(final String org) {
        return listRepos(org, 5);
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
                .setFollowRedirects(true)
                .setHeader("Accept", HEADER_GITHUB_ACCEPT)
                .setHeader("If-None-Match", etag)
                .setQueryParameter("type", "public")
                .setQueryParameter("per_page", GithubApi.MAX_PAGE_SIZE)
                .get()
                .map(
                        new Function<WSResponse, Result>() {
                            public Result apply(WSResponse response) {
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

                                JsonNode repos = response.asJson();
                                Logger.debug("Previous ETag: " + Cache.get(CACHE_GITHUB_ETAG_PREFIX + org));
                                Cache.set(CACHE_GITHUB_ETAG_PREFIX + org, response.getHeader("ETag"));

                                Logger.debug("HTTP Response Headers: " + response.getAllHeaders());
                                Logger.debug(repos.toString());

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

                                final JsonNodeFactory factory = JsonNodeFactory.instance;
                                ArrayNode result = factory.arrayNode();
                                Cache.set(CACHE_RESULT_PREFIX + org, result);
                                Cache.set(CACHE_PULLREQUESTS_RANKING_PREFIX + org, new TreeSet<Integer>());
                                Cache.set(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX + org, new Vector<Long>(githubRepos.length));
                                for (GithubRepo githubRepo : githubRepos) {
                                    getPullRequests(githubRepo);
                                }

                                while (((Vector)Cache.get(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX + org)).size() != 0) {
                                    Logger.debug("Active getPullRequests: " + ((Vector)Cache.get(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX + org)).size());
                                    try {
                                        Thread.sleep(200);
                                    } catch(InterruptedException ex) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                                return ok(evalTop(org, top));
                            }
                        }
                );
    }

    private static boolean isCacheAvailable(final String org, final int top) {
        if(top == FULL_LIST) {
            ArrayNode cachedArrayNode = (ArrayNode) Cache.get(CACHE_RESULT_PREFIX + org);
            if(cachedArrayNode != null)
                return true;
        } else {
            ArrayNode cachedArrayNode = (ArrayNode) Cache.get(CACHE_RESULT_PREFIX + org + top);
            if(cachedArrayNode != null)
                return true;
        }
        return false;
    }

    private static Promise<JsonNode> getPullRequests(final GithubRepo githubRepo) {
        final Long uuid = UUID.randomUUID().getLeastSignificantBits();
        Logger.debug("getPullRequests: Adding " + uuid);
        @SuppressWarnings("unchecked")
        Vector<Long> activeGetPullRequests = (Vector<Long>)Cache.get(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX + githubRepo.getOrg());
        activeGetPullRequests.add(uuid);

        try {
            return WS.url(githubRepo.getUrl() + "/pulls")
                    .setFollowRedirects(true)
                    .setHeader("Accept", HEADER_GITHUB_ACCEPT)
                    .setQueryParameter("state", "all")
                    .setQueryParameter("per_page", GithubApi.MAX_PAGE_SIZE)
                    .get()
                    .map(
                            new Function<WSResponse, JsonNode>() {
                                public JsonNode apply(WSResponse response) {
                                    try {
                                        JsonNode json = response.asJson();
                                        Logger.debug(githubRepo.getName() + " - pull requests: "
                                                + GithubApi.getPullRquestsCount(json));
                                        int pullRequestsCount = GithubApi.getPullRquestsCount(json);
                                        githubRepo.setPullRequestsCount(pullRequestsCount);
                                        @SuppressWarnings("unchecked")
                                        TreeSet<Integer> pullRequestsRanking = (TreeSet<Integer>) Cache.get(CACHE_PULLREQUESTS_RANKING_PREFIX + githubRepo.getOrg());
                                        pullRequestsRanking.add(pullRequestsCount);
                                        ArrayNode arrayNode = (ArrayNode) Cache.get(CACHE_RESULT_PREFIX + githubRepo.getOrg());
                                        arrayNode.add(githubRepo.toJson());
                                        return arrayNode;
                                    } finally {
                                        Logger.debug("getPullRequests: Removing " + uuid);
                                        ((Vector) Cache.get(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX + githubRepo.getOrg())).remove(uuid);
                                    }
                                }
                            }
                    );
        } catch(Exception e) {
            ((Vector) Cache.get(CACHE_ACTIVE_GETPULLREQUESTS_PREFIX + githubRepo.getOrg())).remove(uuid);
            return null;
        }
    }

    private static ArrayNode evalTop(String org, int top) {
        ArrayNode jsonArrayNode = (ArrayNode) Cache.get(CACHE_RESULT_PREFIX + org);
        @SuppressWarnings("unchecked")
        TreeSet<Integer> pullRequestsRanking = (TreeSet<Integer>) Cache.get(CACHE_PULLREQUESTS_RANKING_PREFIX + org);
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

        if(top == FULL_LIST)
            return jsonArrayNode;
        else
            return jsonArrayNodeTop;
    }
}
