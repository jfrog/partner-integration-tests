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

    //getmapofresultslogaggregation
    def getMapOfResultsLogAggregation(newrelic_url, api_key, account_id, query){
        Map<String, Integer> countsMap = new HashMap<>()
        def results = []
        results = getListOfResultsLogAggregation(newrelic_url, api_key, account_id, query)
        results.forEach({ it ->
            countsMap.put(it["facet"].toString(), it["sum"] as Integer)
        })

        return countsMap
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
                "      nrql(query: \\\"${query} SINCE 6 hours ago\\\") {\\n" +
                "        results\\n" +
                "      }\\n" +
                "    }\\n" +
                "  }\\n" +
                "}\\n\", \"variables\":\"\"}"

    }

    static Map<String, Integer> renameMapKeysForWatches(Map<String, Integer> map) {
        return map.collect().stream().collect(
                Collectors.toMap(
                        {Map.Entry<String, Integer> it -> it.key.contains("security") ? "security" : it.key.substring(it.key.lastIndexOf("_")+1)},
                        {Map.Entry<String, Integer> it -> it.value},
                        Integer::sum
                )
        )
    }

    static Map<String, Integer> extractArtifactNamesToMap(Map<String, Integer> map) {
        return map.collect().stream().collect(
                Collectors.toMap(
                        {Map.Entry<String, Integer> it ->
                            it.key.substring(it.key.lastIndexOf("/") + 1, it.key.length()-2)
                        },
                        {Map.Entry<String, Integer> it -> it.value},
                        Integer::sum
                )
        )
    }

}
