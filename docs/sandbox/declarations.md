Submit declaration
----
  Acts as a stub implementation of the /declarations/:nino endpoint.

  To use the sandbox endpoints, either access the /sandbox endpoint directly or supply the use the 
  "X-MOBILE-USER-ID" header with one of the following values: 208606423740 or 167927702220

* **URL**

  `/sandbox/declarations/:nino`

* **Method:**

  `POST`
  
* **URL Params**

   **Required:**: `nino=[Nino]`
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

   **Required**: `journeyId=journeyId`

   journey id provides clarity to log messages.

*  **Request Headers**

   **Required:**: "Accept" -> "application/vnd.hmrc.1.0+json"
   
*  **Request body**

Most fields are optional. A full payload looks like this:

```json
{
    "renewalData": {
      "incomeDetails": {
        "taxableBenefits": 1,
        "earnings": 2,
        "companyBenefits": 3,
        "seProfits": 4,
        "seProfitsEstimated": true
      },
      "incomeDetailsPY1": {
        "taxableBenefits:": 1,
        "earnings": 2,
        "companyBenefits": 3,
        "seProfits": 4,
        "seProfitsEstimated": true
      },
      "certainBenefits": {
        "receivedBenefits": true, 
        "incomeSupport": true, 
        "jsa": true: 
        "pensionCredit": true
      }
    },
    "applicant2Data": {
      "incomeDetails": {
        "taxableBenefits": 1,
        "earnings": 2,
        "companyBenefits": 3,
        "seProfits": 4,
        "seProfitsEstimated": true
      },
      "incomeDetailsPY1": {
        "taxableBenefits:": 1,
        "earnings": 2,
        "companyBenefits": 3,
        "seProfits": 4,
        "seProfitsEstimated": true
      },
      "certainBenefits": {
        "receivedBenefits": true, 
        "incomeSupport": true, 
        "jsa": true: 
        "pensionCredit": true
      }
    },
    "otherIncome": {
      "otherHouseholdIncome":1,
      "isOtherHouseholdIncomeEst":true
    },
    "otherIncomePY1": {
      "otherHouseholdIncome":1,
      "isOtherHouseholdIncomeEst":true
    },
    "hasChangeOfCircs": true
}   
```   

Here is a minimal payload:
```json
{ 
   "renewalData": {},
   "hasChangeOfCircs": true
}   
```  
      
* **Success Responses:**

  To test different scenarios, add a header "SANDBOX-CONTROL" with one of the following values:
  
  | *Value* | *HTTP Status Code* | *Description* 
  |---------|--------------------|---------------|
  | Not set or any value not specified below | 200 | Simulates a successful submission of a declaration. No body returned |
  | "ERROR-401" | 401 | Triggers a 401 Unauthorized response |
  | "ERROR-403" | 403 | Triggers a 403 Forbidden response |
  | "ERROR-404" | 404 | Triggers a 404 NotFound response |
  | "ERROR-500" | 500 | Triggers a 500 Internal Server Error response |   

* **Error Responses:**

  * **Code:** 400 BAD REQUEST <br/>
    **Note:** Response body not valid <br/>
    **Content:** `{"message":"error1, error2"}`

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Note:** Accept header missing <br/>
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`



