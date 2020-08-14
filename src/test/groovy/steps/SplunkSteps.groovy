package steps

import static io.restassured.RestAssured.given

class SplunkSteps {

    // Splunk API documentation
    // https://docs.splunk.com/Documentation/Splunk/8.0.5/RESTREF/RESTsearch#search.2Fjobs
    def createSearch(splunk_username, splunk_password, splunk_url, search_string) {
        return given()
                .auth()
                .preemptive()
                .basic("${splunk_username}", "${splunk_password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/x-www-form-urlencoded")
                .body(search_string)
                .when()
                .post(splunk_url + "/services/search/jobs")
                .then()
                .extract().response()
    }

    def getSearchResults(splunk_username, splunk_password, splunk_url, search_id) {
        return given()
                .auth()
                .preemptive()
                .basic("${splunk_username}", "${splunk_password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/x-www-form-urlencoded")
                .body("output_mode=json")
                .when()
                .get(splunk_url + "/services/search/jobs/" + search_id + "/results")
                .then()
                .extract().response()
    }

}
