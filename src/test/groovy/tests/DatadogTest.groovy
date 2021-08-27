package tests

import io.restassured.RestAssured
import io.restassured.path.json.JsonPath
import io.restassured.response.Response
import org.awaitility.Awaitility
import org.hamcrest.Matchers
import org.testng.Assert
import org.testng.Reporter
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test
import steps.DataAnalyticsSteps
import steps.DatadogSteps
import steps.RepositorySteps
import steps.SecuritytSteps
import steps.XraySteps
import utils.Utils

import java.util.concurrent.TimeUnit

/**
 PTRENG-976 Datadog integration tests for log analytics in test framework.
 Test will generate traffic on the Artifactory instance (with or without Xray installed), then verify Datadog
 can parse the logs. Datadog API is used to verify the dashboards.
 */


class DatadogTest extends DataAnalyticsSteps {
    def artifact = new File("./src/test/resources/repositories/artifact.zip")
    def repoSteps = new RepositorySteps()
    def securitySteps = new SecuritytSteps()
    def datadog = new DatadogSteps("now-15m", "now")
    def testUsers = ["testuser1", "testuser2", "testuser3", "testuser4"]
    def from_timestamp
    def to_timestamp

    List<Object[]> license_issues = xraySteps.multipleLicenseIssueEvents()
    List<Object[]> security_issues = xraySteps.multipleIssueEvents()

    @BeforeSuite(groups=["testing", "datadog", "datadog_xray", "datadog_siem"])
    def setUp() {
        RestAssured.useRelaxedHTTPSValidation()
        def now = new Date()
        from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        to_timestamp = (now.getTime()).toString().substring(0,10)
    }

    @Test(priority=0, groups=["datadog", "datadog_xray"], testName = "Data generation for Datadog testing")
    void dataGeneration(){
        int count = 1
        int calls = 5
        // Try to create a new user with incorrect admin credentials, HTTP response 401
        // Denied Actions by Username
        // Denied Actions by IP
        createUsers401(count, calls)
        // Accepted Deploys by Username
        def emailRt = "testEmail@jfrog.com"
        def passwordRt = "Password123!"
        for(user in testUsers) {
            createUsers(user, emailRt, passwordRt)
            addPermissions(user)
            deployArtifactAs(user, passwordRt, 201)
        }
        // Denied Logins by IP
        createUsers401(count, calls)
        // Denied Logins By Username
        def badUsername = "badguy-"
        def badPassword = "badpassword"
        login(badUsername, badPassword, artifactoryURL, count, calls)
        // Artifactory HTTP 500 Errors
        http500(count, calls)
        // Accessed Images
        // Accessed Repos
        uploadIntoRepo(count, calls)
        def image = "busybox"
        def numberOfImages = 5
        def repos = ["docker-dev-local", "docker-local", "docker-prod-local", "docker-push"]
        // Docker login, pull busybox, generate and push multiple dummy images
        Utils.dockerLogin(username, password, dockerURL)
        Utils.dockerPullImage(image)
        Utils.dockerGenerateImages(repos, numberOfImages, image, dockerURL)
        // Upload Data Transfer by Repo
        // Upload IP's by Data Volume
        uploadIntoRepo(count, calls)
        // Download Data Transfer by Repo
        // Download IP's by Data Volume
        downloadArtifact(count, calls)
        // Artifactory Log Errors
        http404(count, calls)
        http500(count, calls)

    }


    @Test(priority=1, groups=["datadog", "datadog_xray"], testName = "Denied Actions by Username")
    void deniedActionsByUsernameTest(){
        def users = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,"@log_source:jfrog.rt.artifactory.access @action_response:DENIED* -@username:'NA '", "@username")
        for (count in 1..5) {
            def user = "fakeuser-${count} ".toString()
            Assert.assertTrue(users.getOrDefault(user, 0) > 0, "User ${user} in denied users?")
        }
        Reporter.log("- Datadog, Audit. Denied Actions by Username graph test passed", true)

    }

    @Test(priority=2, groups=["datadog", "datadog_xray"], testName = "Denied Actions by IP")
    void deniedActionsByIPTest(){
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:denied_actions_based_on_ip{*} by {ip}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
        def IPv4andIPv6Regex = "(^ip:)(([a-za-z:])([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3})"
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.scope", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("query", Matchers.equalTo(query))

        Reporter.log("- Datadog, Audit. Denied Actions by IP graph test passed", true)

    }

    @Test(priority=3, groups=["datadog", "datadog_xray"], testName = "Accepted Deploys by Username")
        void acceptedDeploysByUsernameTest(){
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:accepted_deploys_based_on_username{*} by {username}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
        response.then().assertThat().log().ifValidationFails().statusCode(200)

        JsonPath jsonPathEvaluator = response.jsonPath()
        def responseUserNames = jsonPathEvaluator.getList("series.scope")
        def usersVerification = testUsers.collect { "username:$it" }.join(' ')
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
        int calls = 5
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:denied_logins_based_on_ip{*} by {ip}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
        def IPv4andIPv6Regex = "(^ip:)(([a-za-z:])([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3})"
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.scope", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("query", Matchers.equalTo(query))
        def numbers = getDatadogFloatList(response)
        Assert.assertTrue((numbers.sum()) >= calls)

        Reporter.log("- Datadog, Audit. Denied Logins By IP graph test passed", true)

    }

    @Test(priority=5, groups=["datadog", "datadog_xray"], testName = "Denied Logins By Username")
    void deniedLoginsByUsernameTest(){
        int count = 1
        int calls = 5
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:denied_logins_by_username{!username:na} by {username}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
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
        int calls = 5
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "avg:artifactory_http_500_errors{*}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (response.then().assertThat().extract().path("series.pointlist.size()") > 0))
        def numbers = getDatadogFloatList(response)
        Assert.assertTrue((numbers.sum()) >= calls)

        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("query", Matchers.equalTo(query))

        Reporter.log("- Datadog, Request. Artifactory HTTP 500 Errors graph test passed", true)

    }

    @Test(priority=7, groups=["datadog", "datadog_xray"], testName = "Accessed Images")
    void accessedImagesTest(){
        def image = "busybox"
        def numberOfImages = 5
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:accessed_images{*} by {image}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
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

    // current graph shows only docker repos
    @Test(priority=8, groups=["datadog", "datadog_xray"], testName = "Accessed Repos")
    void accessedReposTest(){
        int calls = 5
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "count:accessed_repos{*} by {repo}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.pointlist.size()", Matchers.greaterThanOrEqualTo(1)).
                body("query", Matchers.equalTo(query))
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> length = jsonPathEvaluator.getList("series.length", Integer.class)
        Assert.assertTrue((length.sum()) >= calls)
        def repoNames = ["repo:docker-dev-local", "repo:docker-local", "repo:docker-prod-local", "repo:docker-push"]
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
        int calls = 5
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:upload_data_transfer_by_repo{*} by {repo}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
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
        int calls = 5
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:download_data_transfer_by_repo{*} by {repo}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.pointlist.size()", Matchers.greaterThanOrEqualTo(1)).
                body("query", Matchers.equalTo(query))
        def numbers = getDatadogStringList(response)
        for (i in numbers) {
            def x = i.substring(0, i.indexOf("."))
            Assert.assertTrue(x as int == 0 || x as int > 750000)
        }
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> length = jsonPathEvaluator.getList("series.length", Integer.class)
        Assert.assertTrue((length.sum()) >= calls)

        Reporter.log("- Datadog, Request. Download Data Transfer by Repo", true)

    }

    @Test(priority=11, groups=["datadog", "datadog_xray"], testName = "Download IP's by Data Volume")
    void downloadIPTest(){
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:active_downloading_ips{*} by {remote_address}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
        def IPv4andIPv6Regex = "(^remote_address:)(([a-za-z:])([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3})"
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.scope", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("query", Matchers.equalTo(query))

        Reporter.log("- Datadog, Request. Download IP's by Data Volume graph test passed", true)

    }

    @Test(priority=12, groups=["datadog", "datadog_xray"], testName = "Upload IP's by Data Volume")
    void uploadIPTest(){
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "sum:active_uploading_ips{*} by {remote_address}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
        def IPv4andIPv6Regex = "(^remote_address:)(([a-za-z:])([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3})"
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("series.scope", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("query", Matchers.equalTo(query))

        Reporter.log("- Datadog, Request. Upload IP's by Data Volume graph test passed", true)

    }

    @Test(priority=13, groups=["datadog", "datadog_xray"], testName = "Artifactory Log Errors")
    void logErrorsTest(){
        def now = new Date()
        def from_timestamp = (now.getTime()-1800000).toString().substring(0,10)
        def to_timestamp = (now.getTime()).toString().substring(0,10)
        def query = "avg:artifactory_errors{*}.as_count()"
        Response response = datadog.datadogQueryTimeSeriesPoints(datadogBaseURL,
                datadogApiKey, datadogApplicationKey, from_timestamp, to_timestamp, query)
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


    @Test(priority = 14, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Watches")
    void watchesCountTest() {
        def watchesCounts = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,
                "@log_source:jfrog.xray.siem.vulnerabilities", "@watch_name"
        )

        Assert.assertEquals(watchesCounts.size(), license_issues.size() + 1)
        Reporter.log("- Datadog, Xray Violations. Count of watches test passed.", true)
    }

    @Test(priority = 15, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Vulnerabilities")
    void vulnerabilitiesCountTest() {
        def query = datadog.getCountQuery("@log_source:jfrog.xray.siem.vulnerabilities @type:Security")
        Response response = DatadogSteps.aggregateLogs(datadogBaseURL, datadogApiKey, datadogApplicationKey, query)
        response.then().log().ifValidationFails().statusCode(200).body("meta.status",Matchers.equalTo("done"))

        // sum of all security
        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues).getOrDefault("security", 0)
        Assert.assertEquals(response.jsonPath().get("data.buckets[0].computes.c0") ?: 0, expected)
        Reporter.log("- Datadog, Xray Violations. Count of vulnerabilities test passed.", true)
    }

    @Test(priority = 16, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, License Issues")
    void licenseIssuesCountTest() {
        def query = datadog.getCountQuery("@log_source:jfrog.xray.siem.vulnerabilities @type:License")
        Response response = DatadogSteps.aggregateLogs(datadogBaseURL, datadogApiKey, datadogApplicationKey, query)
        response.then().log().ifValidationFails().statusCode(200).body("meta.status",Matchers.equalTo("done"))

        // sum of all license issues
        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues)
        expected.remove("security")
        Assert.assertEquals(response.jsonPath().get("data.buckets[0].computes.c0") ?: 0, expected.values().sum())
        Reporter.log("- Datadog, Xray Violations. Count of licensing issues test passed.", true)
    }

    @Test(priority = 17, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Violations")
    void violationsCountTest() {
        def query = datadog.getCountQuery("@log_source:jfrog.xray.siem.vulnerabilities")
        Response response = DatadogSteps.aggregateLogs(datadogBaseURL, datadogApiKey, datadogApplicationKey, query)
        response.then().log().ifValidationFails().statusCode(200).body("meta.status",Matchers.equalTo("done"))

        // sum of all license and security issues
        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues).values().sum()
        Assert.assertEquals(response.jsonPath().get("data.buckets[0].computes.c0") ?: 0, expected)
        Reporter.log("- Datadog, Xray Violations. Count of violations test passed.", true)
    }

    @Test(priority = 18, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Infected Components")
    void infectedComponentsCountTest() {
        def infectedComponentsCounts = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,
            "@log_source:jfrog.xray.siem.vulnerabilities", "@infected_components"
        )

        def expected = XraySteps.getExpectedComponentCounts(license_issues, security_issues).size()
        Assert.assertEquals(infectedComponentsCounts.size(), expected)
        Reporter.log("- Datadog, Xray Violations. Count of infected components test passed.", true)
    }

    @Test(priority = 19, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Impacted Artifacts")
    void impactedArtifactsCountTest() {
        def impactedArtifactsCounts = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,
                "@log_source:jfrog.xray.siem.vulnerabilities", "@impacted_artifacts"
        )

        def expected = XraySteps.getExpectedComponentCounts(license_issues, security_issues).size()
        Assert.assertEquals(impactedArtifactsCounts.size(), expected)
        Reporter.log("- Datadog, Xray Violations. Count of impacted artifacts test passed.", true)
    }

    @Test(priority = 20, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Violations per Watch")
    void violationsPerWatchTest() {
        def watchesCounts = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,
                "@log_source:jfrog.xray.siem.vulnerabilities", "@watch_name"
        )
        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues)
        def actual = DatadogSteps.renameMapKeysForWatches(watchesCounts)
        Assert.assertEquals(actual, expected)
        Reporter.log("- Datadog, Xray Violations. Violations per watch test passed.", true)
    }

    @Test(priority = 21, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Violations Severity")
    void violationsSeverityCount() {
        def severities = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,
                "@log_source:jfrog.xray.siem.vulnerabilities", "@severity"
        )
        def expected = XraySteps.getExpectedSeverities(license_issues, security_issues)
        Assert.assertEquals(severities, expected)
        Reporter.log("- Datadog, Xray Violations. Violations severities test passed.", true)
    }

    @Test(priority = 22, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Violations by Policy")
    void violationsByPolicyTest() {
        def watchesCounts = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,
                "@log_source:jfrog.xray.siem.vulnerabilities", "@policies"
        )
        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues)
        def actual = DatadogSteps.renameMapKeysForWatches(watchesCounts)
        Assert.assertEquals(actual, expected)
        Reporter.log("- Datadog, Xray Violations. Violations by Policy test passed.", true)
    }

    @Test(priority = 23, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Violations by Rule")
    void violationsByRuleTest() {
        def rulesCounts = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,
                "@log_source:jfrog.xray.siem.vulnerabilities", "@rules"
        )
        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues)
        def actual = DatadogSteps.renameMapKeysForPolicies(rulesCounts)
        Assert.assertEquals(actual, expected)
        Reporter.log("- Datadog, Xray Violations. Violations by rule test passed.", true)
    }

    @Test(priority = 24, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Violation Types over Time (stats)")
    void violationTypesOverTimeStatsTest() {
        def typeCounts = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,
                "@log_source:jfrog.xray.siem.vulnerabilities", "@type"
        )

        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues)
        def expectedSecurityViolations = expected.remove("security") ?: 0
        def expectedLicenseViolations = expected.values().sum()

        Assert.assertEquals(typeCounts, ['License':expectedLicenseViolations, 'Security':expectedSecurityViolations])
        Reporter.log("- Datadog, Xray Violations. Violations by type test passed.", true)
    }

    @Test(priority = 25, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Violation over Time (By Severity)")
    void violationOverTimeSeverityTest() {
        violationsSeverityCount() // This widget is the exact same as "Violations Severity"
    }

    @Test(priority = 26, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Top Infected Components")
    void topInfectedComponentsTest() {
        def infected = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,
                "@log_source:jfrog.xray.siem.vulnerabilities", "@infected_components"
        )

        def expected = XraySteps.getExpectedComponentCounts(license_issues, security_issues)
        Assert.assertEquals(DatadogSteps.extractArtifactNamesToMap(infected), expected)
        Reporter.log("- Datadog, Xray Violations. Top Infected Components test passed.", true)
    }

    @Test(priority = 27, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Top Impacted Artifacts")
    void topImpactedArtifactsTest() {
        def infected = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,
                "@log_source:jfrog.xray.siem.vulnerabilities", "@impacted_artifacts"
        )

        def expected = XraySteps.getExpectedComponentCounts(license_issues, security_issues)
        Assert.assertEquals(DatadogSteps.extractArtifactNamesToMap(infected), expected)
        Reporter.log("- Datadog, Xray Violations. Top Impacted Artifacts test passed.", true)
    }

    @Test(priority = 28, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Top Vulnerabilities")
    void topVulnerabilitiesTest(){
        def cveCounts = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,
                "@log_source:jfrog.xray.siem.vulnerabilities", "@cve"
        )
        def expectedCVECounts = XraySteps.getExpectedCVECounts(security_issues)

        Assert.assertEquals(cveCounts, expectedCVECounts)
        Reporter.log("- Datadog, Xray Violations. Top vulnerabilities test passed.", true)
    }

    @Test(priority = 29, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Top Vulnerable Artifact by Count of IP Download")
    void topVulnerableArtifactsByIPDownloads() {
        def artifacts = datadog.topVulnerableArtifacts(datadogBaseURL, datadogApiKey, datadogApplicationKey, "@ip")
        def expectedArtifactCount = XraySteps.getExpectedComponentCounts(license_issues, security_issues).size()

        Assert.assertEquals(artifacts.size(), expectedArtifactCount)
        Reporter.log("- Datadog, Xray Violations. Top Vulnerable Artifact by Count of IP Download test passed.", true)
    }

    @Test(priority = 30, groups = ["datadog_siem"], testName = "Datadog. Xray Violations, Top Vulnerable Artifact by Count of User Download")
    void topVulnerableArtifactsByUserDownloads() {
        def artifacts = datadog.topVulnerableArtifacts(datadogBaseURL, datadogApiKey, datadogApplicationKey, "@username")
        def expectedArtifactCount = XraySteps.getExpectedComponentCounts(license_issues, security_issues).size()

        Assert.assertEquals(artifacts.size(), expectedArtifactCount)
        Reporter.log("- Datadog, Xray Violations. Top Vulnerable Artifact by Count of User Download test passed.", true)
    }

}
