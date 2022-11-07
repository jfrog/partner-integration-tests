package tests

import io.restassured.RestAssured
import io.restassured.path.json.JsonPath
import io.restassured.response.Response
import org.hamcrest.Matchers
import org.testng.Assert
import org.testng.Reporter
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test
import steps.DataAnalyticsSteps
import steps.DatadogSteps
import steps.NewRelicSteps
import steps.RepositorySteps
import steps.SecuritytSteps
import steps.XraySteps
import utils.Utils

import static org.hamcrest.Matchers.equalTo


class NewRelicTest  extends DataAnalyticsSteps {
    def artifact = new File("./src/test/resources/repositories/artifact.zip")
    def repoSteps = new RepositorySteps()
    def securitySteps = new SecuritytSteps()
    def newrelic = new NewRelicSteps()
    def testUsers = ["testuser1", "testuser2", "testuser3", "testuser4"]
    int count = 1
    int calls = 5
    def repos = ["docker-dev-local", "docker-local", "docker-prod-local", "docker-push"]
    def imagePrefix = "busybox"
    def numberOfImages = 5


    List<Object[]> license_issues = xraySteps.multipleLicenseIssueEvents()
    List<Object[]> security_issues = xraySteps.multipleIssueEvents()

    @BeforeSuite(groups=["testing", "newrelic", "newrelic_xray", "newrelic_siem"])
    def setUp() {
        RestAssured.useRelaxedHTTPSValidation()
    }

    @Test(priority=0, groups=["newrelic", "newrelic_xray"], testName = "Data generation for NewRelic testing")
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

    @Test(priority=1, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Audit. Audit Actions by Users")
    void auditActionsByUsersTest() throws Exception {
        def query = "SELECT count(*) FROM Log FACET user WHERE log_source = 'jfrog.rt.access.audit' AND user != 'UNKNOWN'"
        def users = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)

        for (i in count..calls) {
            Assert.assertTrue(users.getOrDefault(username, 0) > 0, "User ${(users*.key)[i]} in audit actions?")
        }
    }

    @Test(priority=2, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Audit. Denied Actions by Username")
    void deniedActionsByUsernameTest(){
        def query = "SELECT count(*) FROM Log FACET username WHERE log_source = 'jfrog.rt.artifactory.access' AND username != 'NA ' AND action_response LIKE 'DENIED%'"
        def users = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)
        // ensure all fake users are present
        for (i in count..calls) {
            def user = "fakeuser-${i} ".toString()
            Assert.assertTrue(users.getOrDefault(user, 0) > 0, "User ${user} in denied users?")
        }
        Reporter.log("- NewRelic, Audit. Denied Actions by Username graph test passed", true)
    }

    @Test(priority=3, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Audit. Denied Actions by IP")
    void deniedActionsByIPTest(){
        def query = "SELECT count(ip) as 'count' FROM Log FACET ip WHERE log_source ='jfrog.rt.artifactory.access' AND action_response LIKE 'DENIED%'"
        def ips = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)
        println(ips)
        // at least one IP should exist and it should be a valid IP
        Assert.assertTrue(ips.size() > 0, "Denied IP contains values")
        for (IP in ips.keySet()) {
            Assert.assertTrue(Utils.validateIPAddress(IP.replaceAll("\\s","")), "IP ${IP.replaceAll("\\s","")} matches IPv4 or IPv6")
        }

        Reporter.log("- NewRelic, Audit. Denied Actions by IP graph test passed", true)

    }

    @Test(priority=4, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Audit. Denied Logins By Username")
    void deniedLoginsByUsernameTest(){
        def query = "SELECT count(*) from Log FACET username WHERE log_source = 'jfrog.rt.artifactory.access' and action_response = 'DENIED LOGIN' and username != 'NA '"
        def users = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)

        // ensure all fake users are present
        for (i in count..calls) {
            def user = "fakeuser-${i} ".toString()
            Assert.assertTrue(users.getOrDefault(user, 0) > 0, "User ${user} in denied users?")
        }

        Reporter.log("- NewRelic, Audit. Denied Logins By Username graph test passed", true)

    }

    @Test(priority=5, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Audit. Denied Logins By IP")
    void deniedLoginsByIPTest(){

        def query = "SELECT count(*) FROM Log FACET ip WHERE log_source = 'jfrog.rt.artifactory.access' and action_response ='DENIED LOGIN'"
        def ips = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)

        // at least one IP should exist and it should be a valid IP
        Assert.assertTrue(ips.size() > 0, "Denied IP contains values")
        for (IP in ips.keySet()) {
            Assert.assertTrue(Utils.validateIPAddress(IP.replaceAll("\\s","")), "IP ${IP.replaceAll("\\s","")} matches IPv4 or IPv6")
        }

        Reporter.log("- NewRelic, Audit. Denied Logins By IP graph test passed", true)

    }

    @Test(priority=6, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Audit. Accepted Deploys by Username")
    void acceptedDeploysByUsernameTest(){
        def query = "SELECT count(*) FROM Log FACET username WHERE log_source ='jfrog.rt.artifactory.access' and action_response ='ACCEPTED DEPLOY'"
        def users = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)

        // All users should be present
        for (user in testUsers) {
            Assert.assertTrue(users.getOrDefault("${user} ".toString(), 0) > 0, "${user} in users")
        }

        Reporter.log("- NewRelic, Audit. Accepted Deploys by Username graph test passed", true)
    }

    @Test(priority=7, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Docker. Accessed Images")
    void accessedImagesTest(){
        def query = "SELECT count(*) FROM Log FACET image WHERE log_source = 'jfrog.rt.artifactory.request' AND repo !='NULL' AND image!='NULL' AND repo !='' AND image !='' and repo !='latest' AND request_url LIKE '/api/docker/%' TIMESERIES AUTO"
        def images = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)
        println(images)

        for (i in 1..numberOfImages) {
            def image = "${imagePrefix}${i}".toString()
            Assert.assertTrue(images.getOrDefault(image, 0) > 0, "${image} in images")
        }

        Reporter.log("- NewRelic, Docker. Accessed Images graph test passed", true)

    }

    @Test(priority=8, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Docker. Accessed Repos")
    void accessedReposTest(){
        def query = "SELECT count(*) FROM Log FACET repo WHERE log_source = 'jfrog.rt.artifactory.request' AND repo !='NULL' AND image!='NULL' AND repo !='' AND image !='' and repo !='latest' AND request_url LIKE '/api/docker/%' TIMESERIES AUTO"
        def found_repos = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)

        for (repo in repos) {
            Assert.assertTrue(found_repos.getOrDefault(repo, 0) > 0, "${repo} in repos ${found_repos}")
        }

        Reporter.log("- NewRelic, Docker. Accessed Repos graph test passed", true)

    }

    @Test(priority=9, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Docker. Data Transfers (GBs) Downloads By Repo")
    void downloadDataByRepoTest() throws Exception{
        def query = "SELECT sum(request_content_length) as 'sum' FROM Log FACET repo WHERE log_source = 'jfrog.rt.artifactory.request' AND request_url LIKE '/api/docker/%' AND repo!='NULL' AND image!='NULL' AND repo !='' AND image!=''"
        def found_repos = newrelic.getMapOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)

        for (repo in repos) {
            Assert.assertTrue(found_repos.getOrDefault(repo, 0) > 0, "${repo} in repos ${found_repos}")
        }

        Reporter.log("- NewRelic, Docker. Download Data Transfer by Repo", true)
    }

    @Test(priority=10, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Docker. Data Transfers (GBs) Uploads By Repo")
    void uploadDataByRepoTest(){
        def query = "SELECT sum(response_content_length) as 'sum' FROM Log FACET repo WHERE log_source = 'jfrog.rt.artifactory.request' AND request_url LIKE '/api/docker/%' AND repo!='NULL' AND image!='NULL' AND repo !='' AND image!=''"
        def found_repos = newrelic.getMapOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)

        for (repo in repos) {
            Assert.assertTrue(found_repos.getOrDefault(repo, 0) > 0, "${repo} in repos ${found_repos}")
        }

        Reporter.log("- NewRelic, Docker. Upload Data Transfer by Repo graph test passed", true)

    }

    @Test(priority=11, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Requests. Artifactory HTTP 500 Errors")
    void http500errorsTest(){
        def errorCount = 0
        def query = "SELECT count(*) as 'errors' FROM Log WHERE log_source ='jfrog.rt.artifactory.request' AND return_status LIKE '5%%'"
        def error_list = newrelic.getListOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)

        for (item in error_list) {
            errorCount = item['errors']
            Assert.assertTrue(errorCount >= calls, "Error count ${errorCount} >= expected ${calls}")
        }

        Reporter.log("- NewRelic, Requests. Artifactory HTTP 500 Errors graph test passed", true)

    }

    @Test(priority=12, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Requests. HTTP Response Codes\n")
    void httpResponseCodesTest(){
        int count = 1
        int calls = 5
        def respCount = 0
        // Generate HTTP responses in Artifactory
        http200(count, calls)
        http201(count, calls)
        http204(count, calls)
        http403(count, calls)
        http404(count, calls)
        http500(count, calls)
        Thread.sleep(30000)

        def query = "SELECT count(*) FROM Log FACET return_status WHERE log_source = 'jfrog.rt.artifactory.request'"
        def response = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def responseCodes = ["200","201","204","403","404","500"]
        for (responseCode in responseCodes) {
            Assert.assertTrue(responseCode in response.keySet(), "Response code ${responseCode} in the list of responseCodes")
            respCount = response.get(responseCode)
            Assert.assertTrue(respCount >= calls)
        }

        Reporter.log("- NewRelic, Requests. HTTP Response Codes test passed", true)

    }

    @Test(priority=13, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Requests. Download IP's by Data Volume")
    void downloadIPTest(){
        def query = "SELECT sum(request_content_length) as 'upload size' FROM Log FACET remote_address WHERE log_source = 'jfrog.rt.artifactory.request' AND request_content_length != '-1' limit 10"
        def ips = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        Assert.assertTrue(ips.size() > 0, "IP's have downloaded")

        for (IP in ips.keySet()) {
            Assert.assertTrue(Utils.validateIPAddress(IP.replaceAll("\\s","")), "IP ${IP.replaceAll("\\s","")} matches IPv4 or IPv6")
        }

        Reporter.log("- NewRelic, Requests. Download IP's by Data Volume graph test passed", true)

    }

    @Test(priority=14, groups=["newrelic", "newrelic_artifactory"], testName = "Artifcatory, Requests. Upload IP's by Data Volume")
    void uploadIPTest(){
        def query = "SELECT sum(response_content_length) as 'upload size' FROM Log FACET remote_address WHERE log_source = 'jfrog.rt.artifactory.request' AND response_content_length != '-1' limit 10"
        def ips = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        Assert.assertTrue(ips.size() > 0, "IP's have uploaded")

        for (IP in ips.keySet()) {
            Assert.assertTrue(Utils.validateIPAddress(IP.replaceAll("\\s","")), "IP ${IP.replaceAll("\\s","")} matches IPv4 or IPv6")
        }

        Reporter.log("- NewRelic, Requests. Upload IP's by Data Volume graph test passed", true)

    }

    @Test(priority=15, groups=["newrelic", "newrelic_artifactory"], testName = "Artifactory, Application. Log Volume")
    void rtLogVolumeTest() throws Exception {
        int count = 1
        int calls = 5
        def logCount = 0
        // Generate artifactory calls
        http200(count, calls)
        http201(count, calls)
        http204(count, calls)
        http403(count, calls)
        http404(count, calls)
        http500(count, calls)

        def query = "SELECT count(log_source) as 'count' FROM Log FACET log_source TIMESERIES AUTO WHERE log_source != 'jfrog.xray.*' AND log_source != 'NULL'"
        def response = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def logSources = ["jfrog.rt.access.request","jfrog.rt.artifactory.request","jfrog.rt.router.request"]
        for (logSource in logSources) {
            Assert.assertTrue(logSource in response.keySet(), "Log Source ${logSource} in the list of logSources")
            logCount = response.get(logSource)
            Assert.assertTrue(logCount >= calls)
        }

        Reporter.log("- NewRelic. Application, Log volume verification. Each log record has values", true)
    }

    @Test(priority=16, groups=["newrelic", "newrelic_artifactory"], testName = "Artifactory, Application. Artifactory Log Errors")
    void rtLogErrorsTest(){
        def query = "SELECT count(*) FROM Log WHERE log_source ='jfrog.rt.artifactory.service' AND log_level ='ERROR'"
        def results = newrelic.getListOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)
        def errorCount = results[0]['count']

        Assert.assertTrue(errorCount >= 1, "Error count ${errorCount} >= 1")

        Reporter.log("- NewRelic. Application, Artifactory Log Errors graph test passed", true)

    }

    @Test(priority=17, groups=["newrelic", "newrelic_xray"], testName = "Xray, Logs. Log Volume")
    void xrayLogVolumeTest() throws Exception {
        int count = 1
        int calls = 5
        def logCount = 0
        // Generate artifactory calls
        http200(count, calls)
        http201(count, calls)
        http204(count, calls)
        http403(count, calls)
        http404(count, calls)
        http500(count, calls)

        def query = "SELECT count(*) FROM Log FACET log_source WHERE log_source LIKE 'jfrog.xray.%' AND log_source!='NULL'"
        def response = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def logSources = ["jfrog.xray.router.request","jfrog.xray.xray.request","jfrog.xray.server.service"]
        for (logSource in logSources) {
            Assert.assertTrue(logSource in response.keySet(), "Log Source ${logSource} in the list of logSources")
            logCount = response.get(logSource)
            Assert.assertTrue(logCount >= calls)
        }

        Reporter.log("- NewRelic. Xray Logs, Log volume verification. Each log record has values", true)
    }

    @Test(priority=18, groups=["newrelic", "newrelic_xray"], testName = "Xray, Logs. Xray Log Errors")
    void xrayLogErrorsTest(){
        def query = "SELECT count(*) FROM Log FACET log_source WHERE log_source LIKE 'jfrog.xray.%.service' AND log_level ='ERROR'"
        def results = newrelic.getListOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)
        def errorCount = results[0]['count']

        Assert.assertTrue(errorCount >= 1, "Error count ${errorCount} >= 1")

        Reporter.log("- NewRelic. Xray Logs, Log Errors graph test passed", true)

    }

    @Test(priority=19, groups=["newrelic", "newrelic_xray"], testName = "Xray, Logs. HTTP 500 Errors")
    void xrayHttp500errorsTest(){
        def errorCount = 0
        def query = "SELECT count(*) as 'errors' FROM Log FACET return_status WHERE log_source = 'jfrog.xray.xray.request' AND return_status LIKE '5%%' AND return_status != 'undefined'"
        def error_list = newrelic.getListOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId,query)

        for (item in error_list) {
            errorCount = item['errors']
            Assert.assertTrue(errorCount >= calls, "Error count ${errorCount} >= expected ${calls}")
        }

        Reporter.log("- NewRelic, Xray. HTTP 500 Errors graph test passed", true)

    }

    @Test(priority=20, groups=["newrelic", "newrelic_xray"], testName = "Xray, Logs. HTTP Response Codes\n")
    void xrayHttpResponseCodesTest(){
        int count = 1
        int calls = 5
        def respCount = 0
        // Generate HTTP responses in Xray
        xray200(count, calls)
        xray201(count, calls)
        xray409(count, calls+1)
        xray500(count, calls)
        Thread.sleep(35000)

        def query = "SELECT count(*) FROM Log WHERE log_source = 'jfrog.xray.xray.request' FACET return_status"
        def response = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def responseCodes = ["200","201","409","500"]
        for (responseCode in responseCodes) {
            Assert.assertTrue(responseCode in response.keySet(), "Response code ${responseCode} in the list of responseCodes")
            respCount = response.get(responseCode)
            Assert.assertTrue(respCount >= calls)
        }

        Reporter.log("- NewRelic, Xray. HTTP Response Codes test passed", true)

    }

    //==============VIOLATIONS======================

    @Test(priority = 21, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Watches")
    void watchesCountTest() {
        def query = "SELECT uniqueCount(signature) as 'watches' FROM Log WHERE log_source = 'jfrog.xray.siem.vulnerabilities'"
        def response = newrelic.getListOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def watchesCounts = response[0]['watches']
        println(watchesCounts)
        println(license_issues.size())
        Assert.assertTrue(watchesCounts >= license_issues.size() + 1)
        Reporter.log("- NewRelic, Xray Violations. Count of watches test passed.", true)
    }

    @Test(priority = 22, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Vulnerabilities")
    void securityIssuesCountTest() {
        def query = "SELECT count(*) as 'Vulnerabilities' FROM Log WHERE log_source = 'jfrog.xray.siem.vulnerabilities' and category = 'Security'"
        def response = newrelic.getListOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def vulnerabilities = response[0]['Vulnerabilities']
        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues)

        Assert.assertTrue(vulnerabilities >= expected['security'])

        Reporter.log("- NewRelic, Xray Violations. Count of security issues test passed.", true)
    }

    @Test(priority = 23, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, License Issues")
    void licenseIssuesCountTest() {
        def query = "SELECT count(*) as 'License Issues' FROM Log WHERE log_source = 'jfrog.xray.siem.vulnerabilities' AND category = 'License'"
        def response = newrelic.getListOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def licenseIssues = response[0]['License Issues']
        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues)
        expected.remove("security")
        Assert.assertTrue(licenseIssues >= expected.values().sum())
        Reporter.log("- NewRelic, Xray Violations. Count of License issues test passed.", true)

    }

    @Test(priority = 24, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Violations")
    void violationsCountTest() {
        def query = "SELECT count(*) as 'Violations' FROM Log WHERE log_source = 'jfrog.xray.siem.vulnerabilities'"
        def violations = newrelic.getListOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)[0]['Violations']

        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues).values().sum()
        Assert.assertTrue(violations >= expected)
        Reporter.log("- NewRelic, Xray Violations. Count of violations test passed.", true)
    }

    @Test(priority = 25, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Infected Components")
    void infectedComponentsCountTest() {
        def query = "SELECT uniqueCount(infected_components) as 'Infected Components' FROM Log WHERE log_source = 'jfrog.xray.siem.vulnerabilities' AND infected_components LIKE '%'"
        def infectedComponentsCounts = newrelic.getListOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)[0]['Infected Components']

        def expected = XraySteps.getExpectedComponentCounts(license_issues, security_issues).size()
        Assert.assertTrue(infectedComponentsCounts >= expected)
        Reporter.log("- NewRelic, Xray Violations. Count of infected components test passed.", true)
    }

    @Test(priority = 26, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Impacted Artifacts")
    void impactedArtifactsCountTest() {
        def query = "SELECT uniqueCount(impacted_artifacts) as 'Impacted Artifacts' FROM Log WHERE log_source = 'jfrog.xray.siem.vulnerabilities' AND impacted_artifacts LIKE '%'"
        def impactedArtifactsCounts = newrelic.getListOfResultsLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)[0]['Impacted Artifacts']

        def expected = XraySteps.getExpectedComponentCounts(license_issues, security_issues).size()
        Assert.assertTrue(impactedArtifactsCounts >= expected)
        Reporter.log("- NewRelic, Xray Violations. Count of impacted artifacts test passed.", true)
    }

    @Test(priority = 27, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Violations per Watch")
    void violationsPerWatchTest() {
        def query = "SELECT count(*) FROM Log FACET signature WHERE log_source = 'jfrog.xray.siem.vulnerabilities'"
        def violations = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues)
        def actual = DatadogSteps.renameMapKeysForWatches(violations)

        Assert.assertTrue(actual.size() >= expected.size())
        Reporter.log("- NewRelic, Xray Violations. Violations per watch test passed.", true)
    }

    @Test(priority = 28, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Violations Severity")
    void violationsSeverityCount() {
        def query = "SELECT count(*) FROM Log FACET severity WHERE log_source = 'jfrog.xray.siem.vulnerabilities' and severity != 'Unknown'"
        def violations = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def expected = XraySteps.getExpectedSeverities(license_issues, security_issues)
        def actual = DatadogSteps.renameMapKeysForWatches(violations)

        Assert.assertTrue(actual.size() >= expected.size())
        Reporter.log("- NewRelic, Xray Violations. Violations severities test passed.", true)
    }

    @Test(priority = 29, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Violations by Policy")
    void violationsByPolicyTest() {
        def query = "SELECT count(*) FROM Log FACET policies WHERE log_source = 'jfrog.xray.siem.vulnerabilities'"
        def violations = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues)
        def actual = DatadogSteps.renameMapKeysForWatches(violations)

        Assert.assertTrue(actual.size() >= expected.size())
        Reporter.log("- NewRelic, Xray Violations. Violations by Policy test passed.", true)
    }

    @Test(priority = 30, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Violations by Rule")
    void violationsByRuleTest() {
        def query = "SELECT count(*) FROM Log FACET rules WHERE log_source = 'jfrog.xray.siem.vulnerabilities'"
        def violations = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues)
        def actual = DatadogSteps.renameMapKeysForWatches(violations)

        Assert.assertTrue(actual.size() >= expected.size())
        Reporter.log("- NewRelic, Xray Violations. Violations by rule test passed.", true)
    }

    @Test(priority = 31, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Violations over Time (By Type)")
    void violationTypesOverTimeStatsTest() {
        def query = "SELECT count(*) FROM Log FACET category WHERE log_source = 'jfrog.xray.siem.vulnerabilities'"
        def response = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def expected = XraySteps.getExpectedViolationCounts(license_issues, security_issues)
        def expectedSecurityViolations = expected.remove("security") ?: 0
        def expectedLicenseViolations = expected.values().sum()

        Assert.assertTrue(response['License'] >= expectedLicenseViolations)
        Assert.assertTrue(response['Security'] >= expectedSecurityViolations)
        Reporter.log("- NewRelic, Xray Violations. Violations by type test passed.", true)
    }

    @Test(priority = 32, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Violation over Time (By Severity)")
    void violationOverTimeSeverityTest() {
        violationsSeverityCount() // This widget is the exact same as "Violations Severity"
    }

    @Test(priority = 26, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Top Infected Components")
    void topInfectedComponentsTest() {
        def query = "SELECT count(*) FROM Log FACET infected_components WHERE log_source = 'jfrog.xray.siem.vulnerabilities'"
        def response = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def infected = NewRelicSteps.extractArtifactNamesToMap(response)
        def expected = XraySteps.getExpectedComponentCounts(license_issues, security_issues)
        Assert.assertTrue(infected.size() >= expected.size())
        Reporter.log("- NewRelic, Xray Violations. Top Infected Components test passed.", true)
    }

    @Test(priority = 27, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Top Impacted Artifacts")
    void topImpactedArtifactsTest() {
        def query = "SELECT count(*) FROM Log FACET impacted_artifacts WHERE log_source = 'jfrog.xray.siem.vulnerabilities'"
        def response = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)

        def impacted = NewRelicSteps.extractArtifactNamesToMap(response)
        def expected = XraySteps.getExpectedComponentCounts(license_issues, security_issues)
        Assert.assertTrue(impacted.size() >= expected.size())
        Reporter.log("- NewRelic, Xray Violations. Top Impacted Artifacts test passed.", true)
    }

    @Test(priority = 28, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Top Vulnerabilities")
    void topVulnerabilitiesTest(){
        def query = "SELECT count(*) FROM Log FACET cve WHERE log_source = 'jfrog.xray.siem.vulnerabilities'"
        def cveCounts = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)
        def expectedCVECounts = XraySteps.getExpectedCVECounts(security_issues)

        Assert.assertTrue(cveCounts.size() >= expectedCVECounts.size())
        Reporter.log("- NewRelic, Xray Violations. Top vulnerabilities test passed.", true)
    }

    @Test(priority = 29, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Top Vulnerable Artifact by Count of IP Download")
    void topVulnerableArtifactsByIPDownloads() {
        def query = "SELECT uniqueCount(ip) FROM Log FACET impacted_artifacts WHERE log_source = 'jfrog.rt.artifactory.access' AND action_response ='ACCEPTED DOWNLOAD'"
        def artifacts = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)
        def expectedArtifactCount = XraySteps.getExpectedComponentCounts(license_issues, security_issues).size()

        Assert.assertTrue(artifacts.size() >= expectedArtifactCount, "Actual ${artifacts.size()} >= expected ${expectedArtifactCount}")
        Reporter.log("- NewRelic, Xray Violations. Top Vulnerable Artifact by Count of IP Download test passed.", true)
    }

    @Test(priority = 30, groups = ["newrelic", "newrelic_xray", "newrelic_siem"], testName = "NewRelic. Xray Violations, Top Vulnerable Artifact by Count of User Download")
    void topVulnerableArtifactsByUserDownloads() {
        def query = "SELECT uniqueCount(username) FROM Log FACET impacted_artifacts WHERE log_source = 'jfrog.rt.artifactory.access' AND action_response ='ACCEPTED DOWNLOAD'"
        def artifacts = newrelic.getMapOfCountLogAggregation(newrelicBaseURL, newrelicApiKey, newrelicAccountId, query)
        def expectedArtifactCount = XraySteps.getExpectedComponentCounts(license_issues, security_issues).size()

        Assert.assertTrue(artifacts.size() >= expectedArtifactCount, "Actual ${artifacts.size()} >= expected ${expectedArtifactCount}")
        Reporter.log("- NewRelic, Xray Violations. Top Vulnerable Artifact by Count of User Download test passed.", true)
    }

}
