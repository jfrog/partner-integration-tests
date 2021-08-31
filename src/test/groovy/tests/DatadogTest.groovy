package tests

import io.restassured.RestAssured
import io.restassured.response.Response
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
    int count = 1
    int calls = 5
    def repos = ["docker-dev-local", "docker-local", "docker-prod-local", "docker-push"]
    def imagePrefix = "busybox"
    def numberOfImages = 5


    List<Object[]> license_issues = xraySteps.multipleLicenseIssueEvents()
    List<Object[]> security_issues = xraySteps.multipleIssueEvents()

    @BeforeSuite(groups=["testing", "datadog", "datadog_xray", "datadog_siem"])
    def setUp() {
        RestAssured.useRelaxedHTTPSValidation()
    }

    @Test(priority=0, groups=["datadog", "datadog_xray"], testName = "Data generation for Datadog testing")
    void dataGeneration() {
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
        // Docker login, pull busybox, generate and push multiple dummy images
        Utils.dockerLogin(username, password, dockerURL)
        Utils.dockerPullImage(imagePrefix)
        Utils.dockerGenerateImages(repos, numberOfImages, imagePrefix, dockerURL)
        // Upload Data Transfer by Repo
        // Upload IP's by Data Volume
        uploadIntoRepo(count, calls)
        // Download Data Transfer by Repo
        // Download IP's by Data Volume
        downloadArtifact(count, calls)
        // Artifactory Log Errors
        http404(count, calls)
        http500(count, calls)

        // Wait for things to settle before testing further
        Thread.sleep(90 * 1000)
    }


    @Test(priority=1, groups=["datadog", "datadog_xray"], testName = "Denied Actions by Username")
    void deniedActionsByUsernameTest(){
        def query = "@log_source:jfrog.rt.artifactory.access @action_response:DENIED* -@username:'NA '"
        def users = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,query, "@username")
        // ensure all fake users are present
        println users
        for (i in count..calls) {
            def user = "fakeuser-${i} ".toString()
            Assert.assertTrue(users.getOrDefault(user, 0) > 0, "User ${user} in denied users?")
        }
        Reporter.log("- Datadog, Audit. Denied Actions by Username graph test passed", true)

    }

    @Test(priority=2, groups=["datadog", "datadog_xray"], testName = "Denied Actions by IP")
    void deniedActionsByIPTest(){
        def query = "@log_source:jfrog.rt.artifactory.access @action_response:DENIED*"
        def ips = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey, query, "@ip")

        // at least one IP should exist and it should be a valid IP
        Assert.assertTrue(ips.size() > 0, "Denied IP contains values")
        for (IP in ips.keySet()) {
            Assert.assertTrue(Utils.validateIPAddress(IP.strip()), "IP ${IP.strip()} matches IPv4 or IPv6")
        }

        Reporter.log("- Datadog, Audit. Denied Actions by IP graph test passed", true)

    }

    @Test(priority=3, groups=["datadog", "datadog_xray"], testName = "Accepted Deploys by Username")
    void acceptedDeploysByUsernameTest(){
        def query = "@log_source:jfrog.rt.artifactory.access @action_response:\\\"ACCEPTED DEPLOY\\\""
        def users = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,query, "@username")

        // All users should be present
        for (user in testUsers) {
            Assert.assertTrue(users.getOrDefault("${user} ".toString(), 0) > 0, "${user} in users")
        }

        Reporter.log("- Datadog, Audit. Accepted Deploys by Username graph test passed", true)
    }

    @Test(priority=4, groups=["datadog", "datadog_xray"], testName = "Denied Logins By IP")
    void deniedLoginsByIPTest(){

        def query = "@log_source:jfrog.rt.artifactory.access @action_response:\\\"DENIED LOGIN\\\""
        def ips = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey, query, "@ip")

        // at least one IP should exist and it should be a valid IP
        Assert.assertTrue(ips.size() > 0, "Denied IP contains values")
        for (IP in ips.keySet()) {
            Assert.assertTrue(Utils.validateIPAddress(IP.strip()), "IP ${IP.strip()} matches IPv4 or IPv6")
        }

        Reporter.log("- Datadog, Audit. Denied Logins By IP graph test passed", true)

    }

    @Test(priority=5, groups=["datadog", "datadog_xray"], testName = "Denied Logins By Username")
    void deniedLoginsByUsernameTest(){
        def query = "@log_source:jfrog.rt.artifactory.access @action_response:\\\"DENIED LOGIN\\\" -@username:\\\"NA \\\""
        def users = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,query, "@username")

        // ensure all fake users are present
        for (i in count..calls) {
            def user = "fakeuser-${i} ".toString()
            Assert.assertTrue(users.getOrDefault(user, 0) > 0, "User ${user} in denied users?")
        }

        Reporter.log("- Datadog, Audit. Denied Logins By Username graph test passed", true)

    }


    @Test(priority=6, groups=["datadog", "datadog_xray"], testName = "Artifactory HTTP 500 Errors")
    void http500errorsTest(){
        def query = datadog.getCountQuery("@log_source:jfrog.rt.artifactory.request @return_status:5**")
        Response response = DatadogSteps.aggregateLogs(datadogBaseURL, datadogApiKey, datadogApplicationKey, query)
        response.then().log().ifValidationFails().statusCode(200).body("meta.status",Matchers.equalTo("done"))

        def errorCount = response.jsonPath().get("data.buckets[0].computes.c0") ?: 0
        Assert.assertTrue(errorCount >= calls, "Error count ${errorCount} >= expected ${calls}")
        Reporter.log("- Datadog, Request. Artifactory HTTP 500 Errors graph test passed", true)

    }

    @Test(priority=7, groups=["datadog", "datadog_xray"], testName = "Accessed Images")
    void accessedImagesTest(){
        def query = "@log_source:jfrog.rt.artifactory.request @repo:*?* @image:*"
        def images = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,query, "@image")

        for (i in 1..numberOfImages) {
            def image = "${imagePrefix}${i}".toString()
            Assert.assertTrue(images.getOrDefault(image, 0) > 0, "${image} in images")
        }

        Reporter.log("- Datadog, Request. Accessed Images graph test passed", true)

    }

    // current graph shows only docker repos
    @Test(priority=8, groups=["datadog", "datadog_xray"], testName = "Accessed Repos")
    void accessedReposTest(){
        def query = "@log_source:jfrog.rt.artifactory.request @repo:*?*"
        def found_repos = datadog.getMapOfCountLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,query, "@repo")

        for (repo in repos) {
            Assert.assertTrue(found_repos.getOrDefault(repo, 0) > 0, "${repo} in repos ${found_repos}")
        }

        Reporter.log("- Datadog, Request. Accessed Repos graph test passed", true)

    }

    // current graph doesn't show the repo names
    @Test(priority=9, groups=["datadog", "datadog_xray"], testName = "Upload Data Transfer by Repo")
    void uploadDataByRepoTest(){
        def query = "@log_source:jfrog.rt.artifactory.request @response_content_length:>=0 -@repo:\\\"\\\""
        def metric = "@response_content_length"
        def groupby = "@repo"
        def found_repos = datadog.getMapOfMetricCardinalityLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,query, metric, groupby)

        for (repo in repos) {
            Assert.assertTrue(found_repos.getOrDefault(repo, 0) > 0, "${repo} in repos ${found_repos}")
        }

        Reporter.log("- Datadog, Request. Upload Data Transfer by Repo graph test passed", true)

    }

    // current graph doesn't show the repo names
    @Test(priority=10, groups=["datadog", "datadog_xray"], testName = "Download Data Transfer by Repo")
    void downloadDataByRepoTest() throws Exception{
        def query = "@log_source:jfrog.rt.artifactory.request -@repo:\\\"\\\""
        def metric = "@request_content_length"
        def groupby = "@repo"
        def found_repos = datadog.getMapOfMetricCardinalityLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,query, metric, groupby)

        for (repo in repos) {
            Assert.assertTrue(found_repos.getOrDefault(repo, 0) > 0, "${repo} in repos ${found_repos}")
        }

        Reporter.log("- Datadog, Request. Download Data Transfer by Repo", true)

    }

    @Test(priority=11, groups=["datadog", "datadog_xray"], testName = "Download IP's by Data Volume")
    void downloadIPTest(){
        def query = "@log_source:jfrog.rt.artifactory.request @request_content_length:>0"
        def metric = "@request_content_length"
        def groupby = "@remote_address"
        def ips = datadog.getMapOfMetricCardinalityLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,query, metric, groupby)

        Assert.assertTrue(ips.size() > 0, "IP's have downloaded")

        for (IP in ips.keySet()) {
            Assert.assertTrue(Utils.validateIPAddress(IP.strip()), "IP ${IP.strip()} matches IPv4 or IPv6")
        }

        Reporter.log("- Datadog, Request. Download IP's by Data Volume graph test passed", true)

    }

    @Test(priority=12, groups=["datadog", "datadog_xray"], testName = "Upload IP's by Data Volume")
    void uploadIPTest(){
        def query = "@log_source:jfrog.rt.artifactory.request @response_content_length:>=0"
        def metric = "@response_content_length"
        def groupby = "@remote_address"
        def ips = datadog.getMapOfMetricCardinalityLogAggregation(datadogBaseURL, datadogApiKey, datadogApplicationKey,query, metric, groupby)

        Assert.assertTrue(ips.size() > 0, "IP's have downloaded")

        for (IP in ips.keySet()) {
            Assert.assertTrue(Utils.validateIPAddress(IP.strip()), "IP ${IP.strip()} matches IPv4 or IPv6")
        }

        Reporter.log("- Datadog, Request. Upload IP's by Data Volume graph test passed", true)

    }

    @Test(priority=13, groups=["datadog", "datadog_xray"], testName = "Artifactory Log Errors")
    void logErrorsTest(){
        def query = datadog.getCountQuery("@log_source:jfrog.rt.artifactory.service @log_level:ERROR")
        Response response = DatadogSteps.aggregateLogs(datadogBaseURL, datadogApiKey, datadogApplicationKey, query)
        response.then().log().ifValidationFails().statusCode(200).body("meta.status",Matchers.equalTo("done"))

        def errorCount = response.jsonPath().get("data.buckets[0].computes.c0") ?: 0
        Assert.assertTrue(errorCount >= 1, "Error count ${errorCount} >= 1")

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
