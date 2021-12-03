package tests

import io.restassured.path.json.JsonPath
import io.restassured.response.Response
import org.hamcrest.Matchers
import org.testng.Reporter
import org.testng.annotations.Ignore
import org.testng.annotations.Test
import steps.RepositorySteps
import steps.SecuritytSteps

class HealthCheckTest extends RepositorySteps {
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
            if(response.getStatusCode()==401 || response.getStatusCode()==400){
                Reporter.log("- This Artifactory instance doesn't use default password for admin user", true)
            } else if(response.getStatusCode()==200){
                    Reporter.log("- Default password has been changed with NEW_RT_PASSWORD value", true)
            }
        } else { Reporter.log("- NEW_RT_PASSWORD env var was not set. " +
                "Default password has not been changed! Please change it in the UI", true)
        }
    }

    @Test(priority=3, groups=["common"], testName = "Set base URL")
    void setBaseURLTest() {
        Response response = setBaseUrl(artifactoryURL, username, password, artifactoryBaseURL)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body(Matchers.startsWith("URL base has been successfully updated to"))
        Reporter.log("- Update Custom URL Base. Updated with ${artifactoryBaseURL}", true)
    }

    @Test(priority=4, testName = "Check number of licenses/nodes")
    void checkLicensesTest() throws AssertionError {
        Response licenses = securitySteps.getLicenseInformation(artifactoryURL, username, password)
        licenses.then().log().ifValidationFails().statusCode(200)
        def body = licenses.then().extract().path("licenses")
        if (body == null){
            def licensedTo = licenses.then().extract().path("licensedTo")
            Reporter.log("- Get license information. Non-HA installation, licensed to ${licensedTo}", true)
        } else {
            def totalNumber = licenses.then().extract().path("licenses.size()")
            List nodeIDs = licenses.jsonPath().getList("licenses.nodeId")
            def numberNotInUse = nodeIDs.findAll {it == "Not in use"}.size()
            def numberInUse = nodeIDs.findAll {it != "Not in use"}.size()
            if (numberInUse > 1){
                Reporter.log("- Get license information. Installation is HA, more than one license installed and used." +
                        " Number of licenses installed: ${totalNumber}, not used: ${numberNotInUse}", true)
            } else if (numberInUse == 1){
                Reporter.log("- Get license information. Number of licenses installed: ${totalNumber}}. " +
                        "Installation in HA, but only one node is up and has a license installed", true)
            }
        }
    }

    @Test(priority=5, groups=["jcr"], testName = "Accept EULA before testing")
    void acceptEULATest() {
        Response response = acceptEula(artifactoryURL, username, password)
        response.then().assertThat().log().ifValidationFails().statusCode(200)
    }
}
