mobile-tax-credits-renewal
=============================================

An API designed for mobile device use which provides services pertaining to tax credits renewal.

Requirements
------------

The following services are exposed.

Please note it is mandatory to supply an Accept HTTP header to all below services with the
value ```application/vnd.hmrc.1.0+json```.

## Development Setup
- Run locally: `sbt run` which runs on port `8245` by default
- Run with test endpoints: `sbt 'run -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes'`

##  Service Manager Profiles
The service can be run locally from Service Manager, using the following profiles:

| Profile Details               | Command                                                                                                           |
|-------------------------------|:------------------------------------------------------------------------------------------------------------------|
| MOBILE_TAX_CREDITS_ALL            | sm2 --start MOBILE_TAX_CREDITS_ALL --appendArgs '{"MOBILE_TAX_CREDITS_SUMMARY": ["-Dmicroservice.reportActualProfitPeriod.endDate=2030-01-31T10:00:00.000", "-DdateOverride=2020-08-15", "-Dmicroservice.renewals.startDate=2021-04-26T07:00:00.000", "-Dmicroservice.renewals.packReceivedDate=2021-06-04T17:00:00.000", "-Dmicroservice.renewals.endDate=2030-07-31T17:00:00.000", "-Dmicroservice.renewals.gracePeriodEndDate=2030-08-07T22:59:59.000", "-Dmicroservice.renewals.endViewRenewalsDate=2030-11-30T23:59:59.000"]}'                                                                    |


## Run Tests
- Run Unit Tests:  `sbt test`
- Run Integration Tests: `sbt it:test`
- Run Unit and Integration Tests: `sbt test it:test`
- Run Unit and Integration Tests with coverage report: `sbt clean compile coverage test it:test coverageReport dependencyUpdates`

API
---

| *Task*                                                | *Supported Methods* | *Description*                                                                                                                                      |
|-------------------------------------------------------|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| ```/income/:nino/tax-credits/full-claimant-details``` | GET                 | Retrieve the full-claiment-details associated with the nino. Note the header tcrAuthToken must be supplied. [More...](docs/fullClaimentDetails.md) |
| ```/income/tax-credits/submission/state/enabled```    | GET                 | This endpoint retrieves the current state of tax credit submissions. [More...](docs/tax-credits-submission-state-enabled.md)                       |

# Sandbox

All the above endpoints are accessible on sandbox with `/sandbox` prefix on each endpoint, i.e:

```
    GET /income/:nino/tax-credits/full-claimant-details
```

To trigger the sandbox endpoints locally, use the "X-MOBILE-USER-ID" header with one of the following values:
208606423740 or 167927702220

To test different scenarios, add a header "SANDBOX-CONTROL" to specify the appropriate status code and return payload.
See each linked file for details:

| *Task*                                                        | *Supported Methods* | *Description*                                 |
|---------------------------------------------------------------|---------------------|-----------------------------------------------|
| ```/sandbox/income/:nino/tax-credits/full-claimant-details``` | GET                 | Acts as a stub for the related live endpoint. |
| ```/sandbox/income/tax-credits/submission/state/enabled```    | GET                 | Acts as a stub for the related live endpoint. |

# Version

Version of API need to be provided in `Accept` request header

```
Accept: application/vnd.hmrc.v1.0+json
```

### License

This code is open source software licensed under
the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
