package tests

import io.restassured.RestAssured
import io.restassured.response.Response
import org.awaitility.Awaitility
import org.hamcrest.Matchers
import org.testng.Assert
import org.testng.Reporter
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test
import org.yaml.snakeyaml.Yaml
import steps.RepositorySteps
import steps.SecuritytSteps
import steps.SplunkSteps

import java.util.concurrent.TimeUnit

/**
 PTRENG-975 Splunk log analytic integration tests.
 Test will generate traffic on the Artifactory instance, then verify Splunk can parse the logs and return correct
 response thru it's API
 */

class SplunkTest extends SplunkSteps{

    Yaml yaml = new Yaml()
    def configFile = new File("./src/test/resources/testenv.yaml")
    def config = yaml.load(configFile.text)
    def repoSteps = new RepositorySteps()
    def securitySteps = new SecuritytSteps()
    def artifactoryURL
    def distribution
    def username
    def password
    def splunk_username
    def splunk_password
    def splunk_url


    @BeforeSuite(groups=["terraform"])
    def setUp() {
        artifactoryURL = config.artifactory.external_ip
        distribution = config.artifactory.distribution
        username = config.artifactory.rt_username
        password = config.artifactory.rt_password
        splunk_username = config.splunk.username
        splunk_password = config.splunk.password
        splunk_url = "${config.splunk.protocol}" + "${config.splunk.url}" + ":" + "${config.splunk.port}"
        RestAssured.baseURI = "http://${artifactoryURL}/artifactory"
        RestAssured.authentication = RestAssured.basic(username, password);
        RestAssured.useRelaxedHTTPSValidation();
    }

    // Artifactory Log Errors Past 24 Hours
    @Test(priority=1, groups=["splunk"], testName = "Verify Artifactory Log Errors Past 24 Hours")
    void waitTest() throws Exception {

        def search_string = 'search=search * | spath log_source | search log_source="jfrog.rt.artifactory.service" | spath log_level | search log_level="ERROR" | timechart count by log_level&output_mode=json'
        // Generate error 500 - post callhome data
        int count = 1
        int calls = 50
        while (count < calls) {
            Response error500 = securitySteps.generateError500()
            error500
            count++
        }
        // Create a search job in Splunk with given parameters, return Search ID
        Response createSearch = createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = createSearch.then().extract().path("sid")
        println "Search ID is " + searchID

        Awaitility.await().atMost(90, TimeUnit.SECONDS).until(() ->
                (getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)

        Response response = getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        response.then().log().everything()
        int size = response.then().extract().body().path("results.size()")
        String errorCount = response.then().extract().body().path("results[${size-1}].ERROR")
        Assert.assertTrue((Integer.parseInt(errorCount)) >= calls)

        Reporter.log("- Splunk. Splunk successfully detects the number of errors in the past " +
                "24 hours in the Artifactory log. Number of errors: ${errorCount}", true)
    }

}


