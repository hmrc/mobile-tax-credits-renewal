Return Renewals Information
----
  Acts as a stub implementation of the /renewals/:nino endpoint.

  To use the sandbox endpoints, either access the /sandbox endpoint directly or supply the use the 
  "X-MOBILE-USER-ID" header with one of the following values: 208606423740 or 167927702220

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

  To test different scnearios, add a header "SANDBOX-CONTROL" with one of the following values:

  * SANDBOX-CONTROL not set (default) <br />
    **Code:** 200 <br />
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

  * SANDBOX-CONTROL -> RENEWALS-RESPONSE-CHECK-STATUS-ONLY <br />
    **Code:** 200 <br />
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

  * SANDBOX-CONTROL -> RENEWALS-RESPONSE-CLOSED <br />
    **Code:** 200 <br />
    **Note:** A response for a during the closed period.<br/>
    **Content:**
    
```json
{
  "submissionsState":"closed"
}
```
  
  * ERRORS
  Error scenarios can be tested using the  header "SANDBOX-CONTROL" with one of the following values:
  
  | *Value* | *HTTP Status Code* | *Description* 
  |---------|--------------------|---------------|
  | "ERROR-401" | 401 | Triggers a 401 Unauthorized response |
  | "ERROR-403" | 403 | Triggers a 403 Forbidden response |
  | "ERROR-404" | 404 | Triggers a 404 NotFound response |
  | "ERROR-500" | 500 | Triggers a 500 Internal Server Error response |   


* **Error Responses:**

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Note:** Accept header missing <br/>
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`







