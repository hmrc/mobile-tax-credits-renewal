Return Claimant Details
----
  Return claimant details by NINO. 

* **URL**

  `/claimants/:nino`

* **Method:**
  
  `GET`

*  **URL Params**

  `journeyId=journeyId`

   Optional journey id provides clarity to log messages.


   **Required:**
 
   `nino=[Nino]`
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

*  **HTTP Headers**

   **Required:**
 
   `tcrAuthToken: [TCRAuthToken]`

* **Success Response:**

  * **Code:** 200 <br />
    **Content:**

        [Source...](Please see https://github.com/hmrc/mobile-tax-credits-renewal/blob/master/app/uk/gov/hmrc/apigateway/personalincome/domain/Renewals.scala#L85)

```json
{
  "hasPartner": false,
  "claimantNumber": 1,
  "renewalFormType": "r",
  "mainApplicantNino": "true",
  "availableForCOCAutomation": false,
  "applicationId": "some-app-id"
}
```

* **Error Response:**

  * **Code:** 400 BADREQUEST <br />
    **Content:** `{"code":"BADREQUEST","message":"Bad Request"}`

  * **Code:** 404 BADREQUEST <br />
    **Content:** `{"code":"NOT_FOUND","message":"Not Found"}`

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"code":"UNAUTHORIZED","message":"Bearer token is missing or not authorized for access"}`

  * **Code:** 403 FORBIDDEN <br />
    **Content:** `{"code":"FORBIDDEN","message":"No auth header supplied in http request"}`

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`

  OR

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


