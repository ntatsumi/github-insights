Github Insights
=================================

Github Insights is a RESTful service that provides insights to your Github repositories.

## Routes
```
GET     /                           controllers.Application.index()

GET     /:org/repos                 controllers.Application.listRepos(org: String)

GET     /:org/repos/top5            controllers.Application.top5Repos(org: String)
```

## Sample Requests
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
* Specifies Github API v3 in the header to make sure this service is talking the same language with Github API service.
* Pagination item size is set to the maximum value of 100 to minimize HTTP round trips.