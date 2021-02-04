package tests

import io.restassured.path.json.JsonPath
import io.restassured.response.Response
import org.hamcrest.Matchers
import org.testng.Assert
import org.testng.Reporter
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test
import steps.RepositorySteps

class HealthCheckTest extends RepositorySteps{
    def artifactoryURL

    @BeforeSuite(groups=["common"])
    def setUp() {
        artifactoryURL = "${artifactoryBaseURL}/artifactory"
    }

    @Test(priority=0, groups="common", testName = "Health check for all 4 services")
    void healthCheckTest(){
        Response response = getHealthCheckResponse(artifactoryBaseURL)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("router.state", Matchers.equalTo("HEALTHY"))

        int bodySize = response.body().jsonPath().getList("services").size()
        for (int i = 0; i < bodySize; i++) {
            JsonPath jsonPathEvaluator = response.jsonPath()
            String serviceID = jsonPathEvaluator.getString("services[" + i + "].service_id")
            String nodeID = jsonPathEvaluator.getString("services[" + i + "].node_id")
            response.then().
                    body("services[" + i + "].state", Matchers.equalTo("HEALTHY"))

            Reporter.log("- Health check. Service \"" + serviceID + "\" on node \"" + nodeID + "\" is healthy", true)
        }

    }

    @Test(priority=1, groups=["ping","common"], testName = "Ping (In HA 200 only when licences were added)")
    void pingTest() {
        Response response = ping(artifactoryURL)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body(Matchers.hasToString("OK"))
        Reporter.log("- Ping test. Service is OK", true)
    }

    @Test(priority=2, groups=["jcr"], testName = "Accept EULA before testing")
    void acceptEULATest() {
        println username
        println "bash ./scripts/accept_eula.sh ${username},${password},${artifactoryBaseURL}"
        def proc = "bash ./scripts/accept_eula.sh ${username} ${password} ${artifactoryBaseURL}".execute()
        proc.waitForProcessOutput(System.out, System.err)
        Assert.assertTrue(proc.exitValue().equals(0))
    }
}
