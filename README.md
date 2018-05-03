mobile-tax-credits-renewal
=============================================

[ ![Download](https://api.bintray.com/packages/hmrc/releases/mobile-tax-credits-renewal/images/download.svg) ](https://bintray.com/hmrc/releases/mobile-tax-credits-renewal/_latestVersion)

An API designed for mobile device use which provides services pertaining to tax credits renewal.

Requirements
------------

The following services are exposed.

Please note it is mandatory to supply an Accept HTTP header to all below services with the value ```application/vnd.hmrc.1.0+json```. 

API
---

| *Task* | *Supported Methods* | *Description* |
|--------|----|----|
| ```/income/:nino/tax-credits/:renewalReference/auth``` | GET | Validate and retrieve the TCR auth-token assoicated with the NINO and renewal reference. [More...](docs/authenticate.md)|
| ```/income/:nino/tax-credits/claimant-details``` | GET | Retrieve the claiment-details associated with the nino. Note the header tcrAuthToken must be supplied. [More...](docs/claimentDetails.md) |
| ```/income/:nino/tax-credits/full-claimant-details``` | GET | Retrieve the full claiment-details associated with the nino. Note the header tcrAuthToken must be supplied. [More...](docs/claimentDetails.md) |
| ```/income/:nino/tax-credits/renewal``` | POST | Post a renewal to the NTC micro-service for off-line processing. Note the header tcrAuthToken must be supplied. [More...](docs/renewal.md)|
| ```/income/tax-credits/submission/state/enabled``` | GET | Returns the submission state of the tax credit renewals. Note the header tcrAuthToken must be supplied. [More...](docs/tax-credits-submission-state-enabled.md)|

# Sandbox
All the above endpoints are accessible on sandbox with `/sandbox` prefix on each endpoint,e.g.
```
    GET /sandbox/income/:nino/tax-credits/:renewalReference/auth
```

# Definition
API definition for the service will be available under `/api/definition` endpoint.
See definition in `/conf/api-definition.json` for the format.

# Version
Version of API need to be provided in `Accept` request header
```
Accept: application/vnd.hmrc.v1.0+json
```
