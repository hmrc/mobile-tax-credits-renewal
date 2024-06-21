mobile-tax-credits-renewal
=============================================

An API designed for mobile device use which provides services pertaining to tax credits renewal.

Requirements
------------

The following services are exposed.

Please note it is mandatory to supply an Accept HTTP header to all below services with the
value ```application/vnd.hmrc.1.0+json```.

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
