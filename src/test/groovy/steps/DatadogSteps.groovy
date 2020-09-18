package steps

import org.yaml.snakeyaml.Yaml

import static io.restassured.RestAssured.given

class DatadogSteps {
    Yaml yaml = new Yaml()
    def configFile = new File("./src/test/resources/testenv.yaml")
    def config = yaml.load(configFile.text)



    def datadogQueryTimeSeriesPoints(dd_url, api_key, application_key, from_timestamp, to_timestamp, query) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("DD-API-KEY", api_key)
                .header("DD-APPLICATION-KEY", application_key)
                .param("from", from_timestamp)
                .param("to", to_timestamp)
                .param("query", query)
                .when()
                .get(dd_url + "/api/v1/query")
                .then()
                .extract().response()
    }


}
