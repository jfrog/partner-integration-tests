package steps

import java.util.stream.Collectors
import static io.restassured.RestAssured.given

class PrometheusSteps {


    def postQuery(prom_url, query) {
        return given().log().all()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/x-www-form-urlencoded")
                .body("query=${query}")
                .when()
                .post(prom_url + "/api/v1/query")
                .then()
                .extract().response()
    }

    double getSingleValue(prom_url, query) {
        def response = postQuery(prom_url, query).jsonPath().get("data.result[0].value[1]")
        return response ? response.toString().toDouble() : 0
    }

    /**
     * Get a Map mapping list of metric values (in the same order as {@code metrics}) to their counts. Use this for
     * queries like {@code sum by (metric_field) (metric)}
     * @param prom_url Prometheus URL
     * @param query Prometheus query string
     * @param metrics List of metric fields
     * @return Map with keys being List of metric values in the same order as {@code metrics}
     */
    Map<List<String>, Double> getMapValues(prom_url, query, List<String> metrics) {
        def response = postQuery(prom_url, query)
        return response.jsonPath().getList("data.result").stream().collect(
                Collectors.toMap(
                        { result -> metrics.stream().map({metric -> result["metric"][metric.toString()]}).collect() },
                        { result -> ((List<Object>) result["value"]).size() > 1 ? result["value"][1].toString().toDouble() : 0 },
                        Double::sum
                )
        )
    }

}
