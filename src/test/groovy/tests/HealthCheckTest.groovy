package tests

import io.restassured.path.json.JsonPath
import io.restassured.response.Response
import org.hamcrest.Matchers
import org.testng.Reporter
import org.testng.annotations.Test
import steps.RepositorySteps
import steps.SecuritytSteps

class HealthCheckTest extends RepositorySteps{
    def artifactoryURL = "${artifactoryBaseURL}/artifactory"
    def securitySteps = new SecuritytSteps()

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

    @Test(priority=1, groups=["common"], testName = "Ping (In HA 200 only when licences were added)")
    void pingTest() {
        Response response = ping(artifactoryURL)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body(Matchers.hasToString("OK"))
        Reporter.log("- Ping test. Service is OK", true)
    }

    @Test(priority=2, groups=["common"], testName = "Change default password")
    void changeDefaultPassword() {
        if (System.env.NEW_RT_PASSWORD != null) {
            Response response = securitySteps.changePassword(artifactoryURL, username, default_password, password)
            if(response.then().assertThat().statusCode(401) || response.then().assertThat().statusCode(400)){
                Reporter.log("- This Artifactory instance doesn't use default password for admin user", true)
            } else if(response.then().assertThat().log().ifValidationFails().statusCode(200).
                    body(Matchers.hasToString("Password has been successfully changed"))){
                    Reporter.log("- Default password has been changed with NEW_RT_PASSWORD value", true)
            }
        } else {Reporter.log("- NEW_RT_PASSWORD env var was not set. " +
                "Default password has not been changed! Please change it in the UI", true)
        }
    }

    @Test(priority=3, groups=["jcr"], testName = "Accept EULA before testing")
    void acceptEULATest() {
        Response response = acceptEula(artifactoryURL, username, password)
        response.then().assertThat().log().ifValidationFails().statusCode(200)
    }
}
