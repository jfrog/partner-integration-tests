package steps

import org.yaml.snakeyaml.Yaml

import static io.restassured.RestAssured.given

class PrometheusSteps {

    Yaml yaml = new Yaml()
    def configFile = new File("./src/test/resources/testenv.yaml")
    def config = yaml.load(configFile.text)
    def distribution = config.artifactory.distribution
    def username = config.artifactory.rt_username
    def password = config.artifactory.rt_password


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

}
