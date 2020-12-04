Return Renewals Information
----
  Return claimant details by NINO. 

* **URL**

  `/renewals/:nino`

* **Method:**
  
  `GET`

* **URL Params**

   **Required:**: `nino=[Nino]`
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   **Required**: `journeyId=journeyId`

   journey id provides clarity to log messages.

*  **Request Headers**

   **Required:**: "Accept" -> "application/vnd.hmrc.1.0+json"

* **Success Responses:**

  * **Code:** 200 <br />
    **Note:** A response for a user who has claims information during the open submissions period.<br/>
    **Content:**

```json
{
  "submissionsState": "open",
  "claims": [
    {
      "household": {
        "barcodeReference": "200000000000013",
        "applicationID": "198765432134567",
        "applicant1": {
          "nino": "AA000000A",
          "title": "MR",
          "firstForename": "JOHN",
          "secondForename": "",
          "surname": "DENSMORE"
        },
        "householdEndReason": ""
      },
      "renewal": {
        "awardStartDate": "12/10/2030",
        "awardEndDate": "12/10/2010",
        "renewalStatus": "NOT_SUBMITTED",
        "renewalNoticeIssuedDate": "12/10/2030",
        "renewalNoticeFirstSpecifiedDate": "12/10/2010",
        "claimantDetails": {
          "hasPartner": true,
          "claimantNumber": 123,
          "renewalFormType": "D",
          "mainApplicantNino": "AA000000A",
          "usersPartnerNino": "AP412713B",
          "availableForCOCAutomation": false,
          "applicationId": "198765432134566"
        }
      }
    },
    {
      "household": {
        "barcodeReference": "000000000000000",
        "applicationID": "198765432134567",
        "applicant1": {
          "nino": "AA000000A",
          "title": "MR",
          "firstForename": "JOHN",
          "secondForename": "",
          "surname": "DENSMORE"
        },
        "householdEndReason": ""
      },
      "renewal": {
        "awardStartDate": "12/10/2030",
        "awardEndDate": "12/10/2010",
        "renewalStatus": "AWAITING_BARCODE",
        "renewalNoticeIssuedDate": "12/10/2030",
        "renewalNoticeFirstSpecifiedDate": "12/10/2010"
      }
    }
  ]
}
```


  * **Code:** 200 <br />
    **Note:** A response for a user who has claims information during the check-status-only period.<br/>
    **Content:**
    
```json
{
  "submissionsState": "check_status_only",
  "claims": [
    {
      "household": {
        "barcodeReference": "200000000000013",
        "applicationID": "198765432134567",
        "applicant1": {
          "nino": "AA000000A",
          "title": "MR",
          "firstForename": "JOHN",
          "secondForename": "",
          "surname": "DENSMORE"
        },
        "householdEndReason": ""
      },
      "renewal": {
        "awardStartDate": "12/10/2030",
        "awardEndDate": "12/10/2010",
        "renewalStatus": "NOT_SUBMITTED",
        "renewalNoticeIssuedDate": "12/10/2030",
        "renewalNoticeFirstSpecifiedDate": "12/10/2010",
        "claimantDetails": {
          "hasPartner": true,
          "claimantNumber": 123,
          "renewalFormType": "D",
          "mainApplicantNino": "AA000000A",
          "usersPartnerNino": "AP412713B",
          "availableForCOCAutomation": false,
          "applicationId": "198765432134566"
        }
      }
    },
    {
      "household": {
        "barcodeReference": "000000000000000",
        "applicationID": "198765432134567",
        "applicant1": {
          "nino": "AA000000A",
          "title": "MR",
          "firstForename": "JOHN",
          "secondForename": "",
          "surname": "DENSMORE"
        },
        "householdEndReason": ""
      },
      "renewal": {
        "awardStartDate": "12/10/2030",
        "awardEndDate": "12/10/2010",
        "renewalStatus": "AWAITING_BARCODE",
        "renewalNoticeIssuedDate": "12/10/2030",
        "renewalNoticeFirstSpecifiedDate": "12/10/2010"
      }
    }
  ]
}

```

  * **Code:** 200 <br />
    **Note:** A response for a during the closed period.<br/>
    **Content:**
    
```json
{
  "submissionsState":"closed"
}
```


* **Error Responses:**

  * **Code:** 401 UNAUTHORIZED <br/>
    **Content:** `{"code":"UNAUTHORIZED","message":"Bearer token is missing or not authorized for access"}`

  * **Code:** 403 FORBIDDEN <br/>
    **Content:** `{"code":"FORBIDDEN","message":Authenticated user is not authorised for this resource"}`

  * **Code:** 404 NOT FOUND <br/>
    **Content:** `{ "code" : "MATCHING_RESOURCE_NOT_FOUND", "message" : "A resource with the name in the request can not be found in the API" }`

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Note:** Accept header missing <br/>
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`

  Or for a general error:
  
  * **Code:** 500 INTERNAL_SERVER_ERROR <br/>





