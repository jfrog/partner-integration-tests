package tests

import io.restassured.RestAssured
import io.restassured.path.json.JsonPath
import io.restassured.response.Response
import org.hamcrest.Matchers
import org.testng.Assert
import org.testng.Reporter
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
    def dockerURL
    def distribution
    def artifactoryURL
    def artifactoryBaseURL
    def username
    def password
    def datadog_api_key
    def datadog_application_key
    def datadog_url


    @BeforeSuite(groups=["testing", "datadog", "datadog_xray"])
    def setUp() {
        dockerURL = config.artifactory.xrayBaseUrl
        protocol = config.artifactory.protocol
        artifactoryURL = config.artifactory.external_ip
        artifactoryBaseURL = "${protocol}${config.artifactory.external_ip}/artifactory"
        distribution = config.artifactory.distribution
        username = config.artifactory.rt_username
        password = config.artifactory.rt_password
        datadog_api_key = config.datadog.api_key
        datadog_application_key = config.datadog.application_key
        datadog_url = "https://api.datadoghq.com"
        RestAssured.useRelaxedHTTPSValidation()

    }

    @Test(priority=1, groups=["datadog", "datadog_xray"], testName = "Denied Actions by Username")
    void deniedActionsByUsernameTest(){
        int count = 1
        int calls = 10
        // Try to create a new user with incorrect admin credentials, HTTP response 401
        createUsers401(count, calls)
        Thread.sleep(30000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:denied_actions_by_username{!username:na} by {username}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().statusCode(200).
                body("series.pointlist", Matchers.notNullValue()).
                body("query", Matchers.equalTo(query))
        def userNames = []
        while (count <= calls) {
            userNames.add("username:fakeuser-${count},!username:na")
            count++
        }
        JsonPath jsonPathEvaluator = response.jsonPath()
        def responseUserNames = jsonPathEvaluator.getList("series.scope")
        println "Expected username list:"
        println userNames.sort()
        println "Actual username list:"
        println responseUserNames.sort()
        Assert.assertTrue(userNames.intersect(responseUserNames) as boolean)

        Reporter.log("- Datadog. Denied Actions by Username graph test passed", true)

    }

    @Test(priority=2, groups=["datadog", "datadog_xray"], testName = "Denied Actions by IP")
    void deniedActionsByIPTest(){
        int count = 1
        int calls = 10
        // Try to create a new user with incorrect admin credentials, HTTP response 401
        createUsers401(count, calls)
        Thread.sleep(30000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:denied_actions_based_on_ip{*} by {ip}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        def IPv4andIPv6Regex = "(^ip:)(([a-za-z:])([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3})"
        response.then().assertThat().statusCode(200).log().everything().
                body("series.scope", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("query", Matchers.equalTo(query))
        JsonPath jsonPathEvaluator = response.jsonPath()
        int size = response.then().extract().body().path("series.size()")
        List<Integer> result = jsonPathEvaluator.getList("series.length", Integer.class)
        println result.sum()
        println calls/size
        println size
        Assert.assertTrue((result.sum()) >= calls/size)

        Reporter.log("- Datadog. Denied Actions by IP graph test passed", true)

    }

    @Test(priority=3, groups=["datadog", "datadog_xray"], testName = "Accepted Deploys by Username")
        void acceptedDeploysByUsernameTest(){

        def users = ["testuser1", "testuser2", "testuser3", "testuser4"]
        def emailRt = "testEmail@jfrog.com"
        def passwordRt = "password123"
        for(user in users) {
            createUsers(user, emailRt, passwordRt)
            addPermissions(user)
            deployArtifactAs(user, passwordRt)
        }

        Thread.sleep(50000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:accepted_deploys_based_on_username{*} by {username}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().statusCode(200)

        JsonPath jsonPathEvaluator = response.jsonPath()
        def responseUserNames = jsonPathEvaluator.getList("series.scope")
        def usersVerification = users.collect { "username:$it" }.join(' ')
        List<String> list = usersVerification.split(' ')
        println "Expected username list:"
        println list.sort()
        println "Actual username list:"
        println responseUserNames.sort()
        Assert.assertTrue(list.intersect(responseUserNames) as boolean)

        Reporter.log("- Datadog. Accepted Deploys by Username graph test passed", true)

    }

    @Test(priority=4, groups=["datadog", "datadog_xray"], testName = "Denied Logins By IP")
    void deniedLoginsByIPTest(){
        int count = 1
        int calls = 10
        // Try to create a new user with incorrect admin credentials, HTTP response 401
        createUsers401(count, calls)
        Thread.sleep(30000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:denied_logins_based_on_ip{*} by {ip}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        def IPv4andIPv6Regex = "(^ip:)(([a-za-z:])([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3})"
        response.then().assertThat().statusCode(200).log().everything().
                body("series.scope", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("query", Matchers.equalTo(query))
        JsonPath jsonPathEvaluator = response.jsonPath()
        int size = response.then().extract().body().path("series.size()")
        List<Integer> result = jsonPathEvaluator.getList("series.length", Integer.class)
        println result.sum()
        println calls/size
        println size
        Assert.assertTrue((result.sum()) >= calls/size)

        Reporter.log("- Datadog. Denied Logins By IP graph test passed", true)

    }

    @Test(priority=5, groups=["datadog", "datadog_xray"], testName = "Denied Logins By Username")
    void deniedLoginsByUsernameTest(){
        int count = 1
        int calls = 10
        def username = "badguy-"
        def password = "badpassword"
        login(username, password, artifactoryURL, count, calls)
        Thread.sleep(30000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:denied_logins_by_username{!username:na} by {username}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().statusCode(200).log().everything().
                body("series.pointlist", Matchers.notNullValue()).
                body("query", Matchers.equalTo(query))
        def userNames = []
        while (count <= calls) {
            userNames.add("username:badguy-${count},!username:na")
            count++
        }
        JsonPath jsonPathEvaluator = response.jsonPath()
        def responseUserNames = jsonPathEvaluator.getList("series.scope")
        println "Expected username list:"
        println userNames.sort()
        println "Actual username list:"
        println responseUserNames.sort()
        Assert.assertTrue(userNames.intersect(responseUserNames) as boolean)

        Reporter.log("- Datadog. Denied Actions by Username graph test passed", true)

    }



//    @Test(priority=3, groups=["datadog", "datadog_xray"], dataProvider = "users", testName = "Artifcatory, Audit. Generate data with data provider")
//    void generateDataTest(usernameRt, emailRt, passwordRt, incorrectPasswordRt) {
//
//    }



//    @Test(groups=["datadog", "datadog_xray"])
//    void test(){


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





//    }
//    // Deploy as non-existent users, 401
//    deployArtifactAs(usernameRt, passwordRt)
//    createUsers(usernameRt, emailRt, passwordRt)
//    // Deploy with incorrect password
//    deployArtifactAs(usernameRt, incorrectPasswordRt)
//    // Users have no access to target repo, 403 expected
//    deployArtifactAs(usernameRt, passwordRt)
//    // Give access
//    addPermissions(usernameRt)
//    // Deploy again
//    deployArtifactAs(usernameRt, passwordRt)
//    // Delete users
//    securitySteps.deleteUser(usernameRt)

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
