Github Insights
=================================

Github Insights is a RESTful service that provides insights to your Github repositories.

## Routes
```
GET     /                           controllers.Application.index()

POST    /auth/github                controllers.Application.setGithubAuth()

GET     /:org/repos                 controllers.Application.listRepos(org: String)

GET     /:org/repos/top5            controllers.Application.listReposTop5(org: String)
```

## Sample Requests
Set Github username and password (It's not required to set username and password)
```
curl -H "Content-Type: application/json" -d '{ "username": "user","password": "pass" }' localhost:9000/auth/github
```

List Netflix repositories
```
curl localhost:9000/Netflix/repos
```

List the top 5 repositories based on the number of pull requests
```
curl localhost:9000/Netflix/repos/top5
```

## Build Scripts
Compile and run tests
```
activator test
```

Start server
```
activator run
```

## Design Considerations
* Only public repos are included in the insight.
* HTTP redirection gets followed when provided by Github API.
* Uses Etag and caching to minimize the impact from Github API's rate limiting.
* For increasing the rate limit allowance, optionally allow setting GitHub username and password to authenticate.
* Specifies Github API v3 in the header to make sure this service is talking the same language with Github API service.
* Pagination item size is set to the maximum value of 100 to minimize HTTP round trips.