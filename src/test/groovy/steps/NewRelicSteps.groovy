package steps

import io.restassured.response.Response
import org.hamcrest.Matchers
import org.yaml.snakeyaml.Yaml

import java.util.stream.Collectors

import static io.restassured.RestAssured.given

class NewRelicSteps {
    Yaml yaml = new Yaml()
    def configFile = new File("./src/test/resources/testenv.yaml")
    def config = yaml.load(configFile.text)

    static def aggregateLogs(newrelic_url, api_key, query) {
        return given()
                .relaxedHTTPSValidation()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .header("API-Key", api_key)
                .body(query)
                .when()
                .post(newrelic_url + "/graphql")
                .then()
                .extract().response()
    }

    def getListOfResultsLogAggregation(newrelic_url, api_key, account_id, query){
        def results = []
        def json_query = getCountQuery(account_id, query)
        Response response = aggregateLogs(newrelic_url, api_key, json_query)
        response.then().log().ifValidationFails().statusCode(200)
        results = response.jsonPath().getList("data.actor.account.nrql.results")
        return results
    }

    def getMapOfCountLogAggregation(newrelic_url, api_key, account_id, query){
        Map<String, Integer> countsMap = new HashMap<>()
        def results = []
        results = getListOfResultsLogAggregation(newrelic_url, api_key, account_id, query)
        results.forEach({ it ->
            countsMap.put(it["facet"].toString(), it["count"] as Integer)
        })

        return countsMap
    }

    /**
     * ===========
     * | Queries |
     * ===========
     * These are named query_<test name> and used in NewRelicTest.groovy
     * */

    def getCountQuery(account_id, query) {

        return "{\"query\":\"{\\n" +
                "  actor {\\n" +
                "    account(id: ${account_id}) {\\n" +
                "      nrql(query: \\\"${query} SINCE 24 hours ago\\\") {\\n" +
                "        results\\n" +
                "      }\\n" +
                "    }\\n" +
                "  }\\n" +
                "}\\n\", \"variables\":\"\"}"

    }

}
