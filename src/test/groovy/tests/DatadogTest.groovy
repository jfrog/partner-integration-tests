package tests

import io.restassured.RestAssured
import io.restassured.path.json.JsonPath
import io.restassured.response.Response
import org.hamcrest.Matchers
import org.testng.Assert
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test
import org.yaml.snakeyaml.Yaml
import steps.DataAnalyticsSteps
import steps.DatadogSteps
import steps.RepositorySteps
import steps.SecuritytSteps
import utils.Utils

import java.lang.reflect.Array

/**
 PTRENG-976 Datadog integration tests for log analytics in test framework.
 Test will generate traffic on the Artifactory instance (with or without Xray installed), then verify Datadog
 can parse the logs. Datadog API is used to verify the dashboards.
 */


class DatadogTest extends DataAnalyticsSteps {

    Yaml yaml = new Yaml()
    def configFile = new File("./src/test/resources/testenv.yaml")
    def config = yaml.load(configFile.text)
    def artifact = new File("./src/test/resources/repositories/artifact.zip")
    def repoSteps = new RepositorySteps()
    def securitySteps = new SecuritytSteps()
    def datadog = new DatadogSteps()
    def utils = new Utils()
    def artifactoryURL
    def dockerURL
    def distribution
    def username
    def password
    def datadog_api_key
    def datadog_application_key
    def datadog_url


    @BeforeSuite(groups=["datadog", "datadog_xray"])
    def setUp() {
        artifactoryURL = config.artifactory.external_ip
        dockerURL = config.artifactory.url
        distribution = config.artifactory.distribution
        username = config.artifactory.rt_username
        password = config.artifactory.rt_password
        datadog_api_key = config.datadog.api_key
        datadog_application_key = config.datadog.application_key
        datadog_url = "https://api.datadoghq.com"
    }

    @Test(priority=1, groups=["datadog", "datadog_xray"], testName = "Denied Actions by Username")
    void deniedActionsByUsernameTest(){
        int count = 1
        int calls = 10
        // Try to create a new user with incorrect admin credentials, HTTP response 401
        createUsers401(count, calls)
        Thread.sleep(10000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:denied_actions_by_username{*} by {username}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().statusCode(200).
                body("series.pointlist", Matchers.notNullValue()).
                body("query", Matchers.equalTo(query))
        def userNames = []
        while (count <= calls) {
            userNames.add("username:fakeuser-${count}")
            count++
        }
        JsonPath jsonPathEvaluator = response.jsonPath()
        def responseUserNames = jsonPathEvaluator.getList("series.scope")
        println "Expected username list:"
        println userNames.sort()
        println "Actual username list:"
        println responseUserNames.sort()
        Assert.assertTrue(userNames.sort() == responseUserNames.sort())

    }

    @Test(priority=2, groups=["datadog", "datadog_xray"], testName = "Denied Actions by IP")
    void deniedActionsByIPTest(){
        int count = 1
        int calls = 10
        // Try to create a new user with incorrect admin credentials, HTTP response 401
        createUsers401(count, calls)
        Thread.sleep(10000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:denied_actions_by_ip{*} by {ip}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        //TODO add IP versification back after it's fixed in Fluentd
        def IPv4andIPv6Regex = "((^\\s*((([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5]))\\s*\$)|(^\\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*\$))"
        response.then().assertThat().statusCode(200).
                //body("series.scope", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("query", Matchers.equalTo(query))
        JsonPath jsonPathEvaluator = response.jsonPath()
        int size = response.then().extract().body().path("series.size()")
        List<Integer> result = jsonPathEvaluator.getList("series.length", Integer.class)
        Assert.assertTrue((result.sum()) >= calls/size)


    }

    @Test(priority=3, groups=["datadog", "datadog_xray"], dataProvider = "users", testName = "Artifcatory, Audit. Generate data with data provider")
    void generateDataTest(usernameRt, emailRt, passwordRt, incorrectPasswordRt) {
        // Deploy as non-existent users, 401
        deployArtifactAs(usernameRt, passwordRt)
        createUsers(usernameRt, emailRt, passwordRt)
        // Deploy with incorrect password
        deployArtifactAs(usernameRt, incorrectPasswordRt)
        // Users have no access to target repo, 403 expected
        deployArtifactAs(usernameRt, passwordRt)
        // Give access
        addPermissions(usernameRt)
        // Deploy again
        deployArtifactAs(usernameRt, passwordRt)
        // Delete users
        securitySteps.deleteUser(usernameRt)
    }


    @Test(priority=4, groups=["datadog", "datadog_xray"], testName = "Accepted Deploys by Username")
        void acceptedDeploysByUsernameTest(){

        deployArtifactAs(username, password)

        Thread.sleep(30000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "avg:accepted_deploys_based_on_username{*} by {username}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().log().everything()
    }





    @Test(groups=["datadog", "datadog_xray"])
    void test(){

//        int count = 1
//        int calls = 10
//        // Generate HTTP responses in Artifactory
//        http200(count, calls)
//        http201(count, calls)
//        http204(count, calls)
//        http403(count, calls)
//        http404(count, calls)
//        http500(count, calls)
//        uploadIntoRepo(count, calls)
//        downloadArtifact(count, calls)





//        def from_timestapm = "1600067404"
//        def to_timestamp = "1600107004"
//        def query = ""
        // denied_logins_by_username{*}by{username}
//        Response response = datadog.query(datadog_url, datadog_api_key, datadog_application_key, from_timestapm, to_timestamp, query)
//        response.then().log().everything()

    }


    // AUDIT
    // Denied Actions by Username
    // Denied Actions by IP
    // Accepted Deploys by Username
    // Denied Logins By IP
    // Denied Logins By Username

    // REQUESTS
    // Artifactory HTTP 500 Errors
    // Accessed Images
    // Accessed Repos
    // Upload Data Transfer by Repo
    // Download Data Transfer by Repo
    // Active Downloading IPs
    // Active Uploading IPs

    // APPLICATION
    // Artifactory Errors



}
