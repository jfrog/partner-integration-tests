package tests

import groovy.json.JsonSlurper
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
        dockerURL = config.artifactory.url
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
        Thread.sleep(60000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:denied_actions_by_username{!username:na} by {username}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
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

        Reporter.log("- Datadog, Audit. Denied Actions by Username graph test passed", true)

    }

    @Test(priority=2, groups=["datadog", "datadog_xray"], testName = "Denied Actions by IP")
    void deniedActionsByIPTest(){
        int count = 1
        int calls = 10
        // Try to create a new user with incorrect admin credentials, HTTP response 401
        createUsers401(count, calls)
        Thread.sleep(40000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:denied_actions_based_on_ip{*} by {ip}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        def IPv4andIPv6Regex = "(^ip:)(([a-za-z:])([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3})"
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.scope", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("query", Matchers.equalTo(query))

        Reporter.log("- Datadog, Audit. Denied Actions by IP graph test passed", true)

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
        response.then().assertThat().log().ifValidationFails().statusCode(200).

        JsonPath jsonPathEvaluator = response.jsonPath()
        def responseUserNames = jsonPathEvaluator.getList("series.scope")
        def usersVerification = users.collect { "username:$it" }.join(' ')
        List<String> list = usersVerification.split(' ')
        println "Expected username list:"
        println list.sort()
        println "Actual username list:"
        println responseUserNames.sort()
        Assert.assertTrue(list.intersect(responseUserNames) as boolean)

        Reporter.log("- Datadog, Audit. Accepted Deploys by Username graph test passed", true)

    }

    @Test(priority=4, groups=["datadog", "datadog_xray"], testName = "Denied Logins By IP")
    void deniedLoginsByIPTest(){
        int count = 1
        int calls = 10
        // Try to create a new user with incorrect admin credentials, HTTP response 401
        createUsers401(count, calls)
        Thread.sleep(50000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:denied_logins_based_on_ip{*} by {ip}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        def IPv4andIPv6Regex = "(^ip:)(([a-za-z:])([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3})"
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.scope", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("query", Matchers.equalTo(query))
        JsonPath jsonPathEvaluator = response.jsonPath()
        int size = response.then().extract().body().path("series.size()")
        List<Integer> result = jsonPathEvaluator.getList("series.length", Integer.class)
        println result.sum()
        println calls/size
        println size
        Assert.assertTrue((result.sum()) >= calls/size)

        Reporter.log("- Datadog, Audit. Denied Logins By IP graph test passed", true)

    }

    @Test(priority=5, groups=["datadog", "datadog_xray"], testName = "Denied Logins By Username")
    void deniedLoginsByUsernameTest(){
        int count = 1
        int calls = 10
        def username = "badguy-"
        def password = "badpassword"
        login(username, password, artifactoryURL, count, calls)
        Thread.sleep(60000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:denied_logins_by_username{!username:na} by {username}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
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

        Reporter.log("- Datadog, Audit. Denied Logins By Username graph test passed", true)

    }


    @Test(priority=6, groups=["datadog", "datadog_xray"], testName = "Artifactory HTTP 500 Errors")
    void http500errorsTest(){
        int count = 1
        int calls = 10
        http500(count, calls)
        Thread.sleep(40000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "avg:artifactory_http_500_errors{*}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.pointlist.size()", Matchers.greaterThan(0)).
                body("query", Matchers.equalTo(query))

        Reporter.log("- Datadog, Request. Artifactory HTTP 500 Errors graph test passed", true)

    }

    @Test(priority=7, groups=["datadog", "datadog_xray"], testName = "Accessed Images")
    void accessedImagesTest(){
        def image = "busybox"
        def numberOfImages = 5
        def repos = ["docker-dev-local", "docker-local"]
        // Docker login, pull busybox, generate and push multiple dummy images
        utils.dockerLogin(username, password, dockerURL)
        utils.dockerPullImage(image)
        utils.dockerGenerateImages(repos, numberOfImages, image, dockerURL)
        Thread.sleep(40000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:accessed_images{*} by {image}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().
                body("series.pointlist.size()", Matchers.greaterThan(0)).
                body("query", Matchers.equalTo(query)).
                log().ifValidationFails().statusCode(200)

        def imageNames = []
        def count = 1
        while (count <= numberOfImages) {
            imageNames.add("image:${image}${count}")
            count++
        }
        JsonPath jsonPathEvaluator = response.jsonPath()
        def responseImages = jsonPathEvaluator.getList("series.scope")
        println "Expected images list:"
        println imageNames.sort()
        println "Actual images list:"
        println responseImages.sort()
        Assert.assertTrue(imageNames.intersect(responseImages) as boolean)


        Reporter.log("- Datadog, Request. Accessed Images graph test passed", true)

    }

    //TODO: ONLY DOCKER REPOS! add docker requests
    @Test(priority=8, groups=["datadog", "datadog_xray"], testName = "Accessed Repos")
    void accessedReposTest(){
        int count = 1
        int calls = 5
        uploadIntoRepo(count, calls)
        def image = "busybox"
        def numberOfImages = 5
        def repos = ["docker-dev-local", "docker-local"]
        // Docker login, pull busybox, generate and push multiple dummy images
        utils.dockerLogin(username, password, dockerURL)
        utils.dockerPullImage(image)
        utils.dockerGenerateImages(repos, numberOfImages, image, dockerURL)

        Thread.sleep(40000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:accessed_repos{*} by {repo}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.pointlist.size()", Matchers.greaterThanOrEqualTo(1)).
                body("query", Matchers.equalTo(query))
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> length = jsonPathEvaluator.getList("series.length", Integer.class)
        Assert.assertTrue((length.sum()) >= calls)
        def repoNames = ["repo:docker-dev-local", "repo:docker-local"]
        def responseRepos = jsonPathEvaluator.getList("series.scope")
        println "Expected images list:"
        println repoNames.sort()
        println "Actual images list:"
        println responseRepos.sort()
        Assert.assertTrue(repoNames.intersect(responseRepos) as boolean)

        Reporter.log("- Datadog, Request. Accessed Repos graph test passed", true)

    }

    // current graph doesn't show the repo names
    @Test(priority=9, groups=["datadog", "datadog_xray"], testName = "Upload Data Transfer by Repo")
    void uploadDataByRepoTest(){
        int count = 1
        int calls = 10
        uploadIntoRepo(count, calls)
        Thread.sleep(40000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:upload_data_transfers_by_repo_test2{*} by {repo}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.pointlist.size()", Matchers.greaterThanOrEqualTo(1)).
                body("query", Matchers.equalTo(query))
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> length = jsonPathEvaluator.getList("series.length", Integer.class)
        Assert.assertTrue((length.sum()) >= calls)

        Reporter.log("- Datadog, Request. Upload Data Transfer by Repo graph test passed", true)

    }

    // current graph doesn't show the repo names
    @Test(priority=10, groups=["datadog", "datadog_xray"], testName = "Download Data Transfer by Repo")
    void downloadDataByRepoTest() throws Exception{
        int count = 1
        int calls = 10
        downloadArtifact(count, calls)
        Thread.sleep(40000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:download_data_transfers_by_repo{*} by {repo}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.pointlist.size()", Matchers.greaterThanOrEqualTo(1)).
                body("query", Matchers.equalTo(query))
        JsonPath jsonPathEvaluator = response.jsonPath()
        int seriesSize = response.then().extract().body().path("series.size()")
        int size = response.then().extract().body().path("series[${seriesSize-1}].pointlist.size()")
        def counter = 0
        while(counter < size){
        List<Float> numbers = jsonPathEvaluator.getFloat("series[${seriesSize-1}].pointlist[${counter}][1]") as List<Float>
            for(i in numbers){
                Assert.assertTrue(i == 0.0 || i > 2890000.0)
            }
            counter++
        }

        List<Integer> length = jsonPathEvaluator.getList("series.length", Integer.class)
        Assert.assertTrue((length.sum()) >= calls)

        Reporter.log("- Datadog, Request. Download Data Transfer by Repo", true)

    }

    @Test(priority=11, groups=["datadog", "datadog_xray"], testName = "Download IP's by Data Volume")
    void downloadIPTest(){
        int count = 1
        int calls = 10
        downloadArtifact(count, calls)
        Thread.sleep(30000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:active_downloading_ips{*} by {remote_address}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        def IPv4andIPv6Regex = "(^remote_address:)(([a-za-z:])([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3})"
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.scope", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("query", Matchers.equalTo(query))

        Reporter.log("- Datadog, Request. Download IP's by Data Volume graph test passed", true)

    }

    @Test(priority=12, groups=["datadog", "datadog_xray"], testName = "Upload IP's by Data Volume")
    void uploadIPTest(){
        int count = 1
        int calls = 10
        uploadIntoRepo(count, calls)
        Thread.sleep(30000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:active_uploading_ips{*} by {remote_address}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        def IPv4andIPv6Regex = "(^remote_address:)(([a-za-z:])([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3})"
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.scope", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("query", Matchers.equalTo(query))

        Reporter.log("- Datadog, Request. Upload IP's by Data Volume graph test passed", true)

    }

    @Test(priority=13, groups=["datadog", "datadog_xray"], testName = "Artifactory Log Errors")
    void logErrorsTest(){
        int count = 1
        int calls = 10
        http404(count, calls)
        http500(count, calls)
        Thread.sleep(50000)
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "avg:artifactory_errors{*}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadog_url,
                datadog_api_key, datadog_application_key, from_timestamp, to_timestamp, query)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.pointlist.size()", Matchers.greaterThanOrEqualTo(1)).
                body("query", Matchers.equalTo(query))

        Reporter.log("- Datadog, Request. Artifactory Log Errors graph test passed", true)

    }

    // AUDIT
    // Denied Actions by Username
    // Denied Actions by IP
    // Accepted Deploys by Username
    // Denied Logins By IP
    // Denied Logins By Username

    // REQUESTS
    // Artifactory HTTP 500 Errors

    // -- no docker on the current instance
    // Accessed Images

    // Accessed Repos
    // Upload Data Transfer by Repo
    // Download Data Transfer by Repo
    // Download IP's by Data Volume
    // Upload IP's by Data Volume

    // APPLICATION
    // Artifactory Log Errors



}
