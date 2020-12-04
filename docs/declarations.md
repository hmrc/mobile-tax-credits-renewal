Submit declaration
----
  Submit tax credits renewal declarations for off-line processing.

* **URL**

  `/declarations/:nino`

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
   
* **Success Response:**

  * **Code:** 200 <br />

* **Error Responses:**

  * **Code:** 400 BAD REQUEST <br/>
    **Note:** Response body not valid <br/>
    **Content:** `{"message":"error1, error2"}`

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




