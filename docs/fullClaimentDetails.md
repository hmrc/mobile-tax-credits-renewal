Return Full Claimant Details object
----
  Return full claimant details object based on NINO. The service can either return details a single claim or multiple claims.
  Requesting a single claim the 'claims' parameter must not be supplied and the tcrTokenAuth must be supplied in the header.
  Requesting multiple claims, the 'claim' parameter must be supplied with no tcrTokenAuth header.

* **URL**

  `/income/:nino/tax-credits/full-claimant-details`

* **Method:**
  
  `GET`

*  **URL Params**

   **Required:**
 
   `nino=[Nino]`
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))
   
   **Required**: 
   
   `journeyId=journeyId`
   
   journey id provides clarity to log messages.

*  **HTTP Headers**

   **Required:**
 
   `tcrAuthToken: [TCRAuthToken]` to be supplied when requesting a single claim.

   Note: The tcrAuthToken is only required when the claims query parameter is not supplied.

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

        [Source...](Please see https://github.com/hmrc/personal-income/blob/master/app/uk/gov/hmrc/apigateway/personalincome/domain/Renewals.scala#L55)

```json
{
    "references": [
        {
            "household": {
                "barcodeReference": "200000000000013",
                "applicationID": "198765432134567",
                "applicant1": {
                    "nino": "CS700100A",
                    "title": "MR",
                    "firstForename": "JOHN",
                    "secondForename": "",
                    "surname": "DENSMORE",
                    "previousYearRtiEmployedEarnings": 25444.99
                },
                "householdEndReason": ""
            },
            "renewal": {
                "awardStartDate": "06/04/2018",
                "awardEndDate": "05/04/2019",
                "renewalStatus": "NOT_SUBMITTED",
                "renewalNoticeIssuedDate": "12/10/2030",
                "renewalNoticeFirstSpecifiedDate": "12/10/2010",
                "renewalFormType": "D"
            }
        }
    ]
}
```


* **Error Response:**

  * **Code:** 400 BADREQUEST <br />
    **Content:** `{"code":"BADREQUEST","message":"Bad Request"}`

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"code":"UNAUTHORIZED","message":"Bearer token is missing or not authorized for access"}`

  * **Code:** 403 FORBIDDEN <br />
    **Content:** `{"code":"FORBIDDEN","message":"No auth header supplied in http request"}`
    
  * **Code:** 404 NOT_FOUND <br />
    **Content:** `{"code":"NOT_FOUND","message":"Not Found"}`

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`

  OR

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


