package steps


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

}
