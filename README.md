Github Insights
=================================

Github Insights is a RESTful service that provides insights to your Github repositories.

## Build Instructions
Compile and run tests
```
activator test
```

Start server
```
activator run
```

## Routes
```
GET     /                           controllers.Application.index()

POST    /auth/github                controllers.Application.setGithubAuth()

GET     /:org/repos                 controllers.Application.listRepos(org: String)

GET     /:org/repos/top/:top        controllers.Application.listReposTop(org: String, top: Integer)
```

## Sample Requests
Set Github username and password (Optional to get higher rate limiting allowance)
```
curl -H "Content-Type: application/json" -d '{ "username": "user","password": "pass" }' localhost:9000/auth/github
```

List Netflix repositories
```
curl localhost:9000/Netflix/repos
```

List the top 5 repositories based on the number of pull requests
```
curl localhost:9000/Netflix/repos/top/5
```

## Design Considerations
* Only public repos are included in the insight.
* Pull requests look ups are asynchronous and non-blocking.
* HTTP redirection gets followed when provided by Github API.
* Use Etag and caching to improve performance and minimize the impact from Github API's rate limiting. The number of pull requests can be queried via the Admin Stats API but that's only available to Enterprise site admins. Popular repos will require significant number of page requests to look up all pull requests.
* For increasing the rate limit allowance, allow authenticating to GitHub.
* Specify Github API v3 in the header to make sure this service is talking the same language with Github API service.
* Pagination item size is set to the maximum value of 100 to minimize HTTP round trips.
* Assume that each org doesn't have above 100 repositories.

## Todos
* Add more tests!