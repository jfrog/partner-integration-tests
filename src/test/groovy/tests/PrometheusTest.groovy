package tests

import io.restassured.RestAssured
import org.testng.Assert
import org.testng.Reporter
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test
import steps.DataAnalyticsSteps
import steps.RepositorySteps
import steps.SecuritytSteps
import steps.PrometheusSteps
import utils.Utils


class PrometheusTest extends DataAnalyticsSteps{

    def artifact = new File("./src/test/resources/repositories/artifact.zip")
    def repoSteps = new RepositorySteps()
    def securitySteps = new SecuritytSteps()
    def prometheus = new PrometheusSteps()
    def waitTimeMillis = 20 * 1000

    def query_rtAuditByUsersTest = "sum by (user) (jfrog_rt_access_audit_total{user!=\"UNKNOWN\", user!=\"anonymous\", user!=\"_system_\",user!=\"\"})"
    Map<List<String>, Double> initial_rtAuditByUsersTest
    def query_rtDeniedActionByUsersTest = "sum by (username) (jfrog_rt_access_total{username!=\"UNKNOWN \", username!=\"_system_ \", action_response=~\"DENIED.*\"})"
    Map<List<String>, Double> initial_rtDeniedActionByUsersTest
    def query_rtDeniedActionByUserIPTest = "sum by (username,ip) (jfrog_rt_access_total{username!=\"UNKNOWN \", username!=\"_system_ \", action_response=~\"DENIED.*\"})"
    Map<List<String>, Double> initial_rtDeniedActionByUserIPTest
    def query_rtDeniedLoginsByIPTest = "sum by (ip) (jfrog_rt_access_total{username!=\"UNKNOWN \", username!=\"_system_ \", action_response=~\"DENIED LOGIN\"})"
    Map<List<String>, Double> initial_rtDeniedLoginsByIPTest
    def query_rtDeniedActionsByIPTest = "sum by (ip) (jfrog_rt_access_total{username!=\"UNKNOWN \", username!=\"_system_ \", action_response=~\"DENIED.*\"})"
    Map<List<String>, Double> initial_rtDeniedActionsByIPTest
    def query_rtAcceptedDeploysByUsernameTest = "sum by (username) (jfrog_rt_access_total{username!=\"UNKNOWN \", username!=\"_system_ \", action_response=~\"ACCEPTED DEPLOY\"})"
    Map<List<String>, Double> initial_rtAcceptedDeploysByUsernameTest

    @BeforeSuite(groups=["prometheus", "prometheus_xray"])
    def setUp() {
        RestAssured.baseURI = "${artifactoryBaseURL}/artifactory"
        RestAssured.authentication = RestAssured.basic(username, password)
        RestAssured.useRelaxedHTTPSValidation()
    }

    @Test(priority=1, groups=["prometheus"], testName = "Artifactory. Upload Data Transfers")
    void uploadDataTest() throws Exception {
        def query = "sum(jfrog_rt_data_upload_total)"
        def initial = prometheus.getSingleValue(prometheusBaseURL, query)

        int count = 1
        int calls = 5
        uploadIntoRepo(count, calls)
        Thread.sleep(waitTimeMillis)
        def result = prometheus.getSingleValue(prometheusBaseURL, query)

        // the result is a per-second calculated rate which would be difficult to get an exact value and also depends on network
        Assert.assertTrue(result > initial)

        Reporter.log("- Prometheus. Upload", true)

    }

    @Test(priority=2, groups=["prometheus"], testName = "Artifactory. Download Data Transfers")
    void downloadDataTest() throws Exception {
        def query = "sum(jfrog_rt_data_download_total)"
        def initial = prometheus.getSingleValue(prometheusBaseURL, query)

        int count = 1
        int calls = 5
        downloadArtifact(count, calls)
        Thread.sleep(waitTimeMillis)
        def result = prometheus.getSingleValue(prometheusBaseURL, query)

        // the result is a per-second calculated rate which would be difficult to get an exact value and also depends on network
        Assert.assertTrue(result > initial)

        Reporter.log("- Prometheus. Download", true)

    }

    @Test(priority=3, groups=["prometheus"], testName = "Artifactory. HTTP 500 Errors")
    void http500ErrorsTest() throws Exception {
        // Generate error 500 - post callhome data
        def query = "sum(jfrog_rt_req_total{return_status=~\"5.*\"})"
        def initial = prometheus.getSingleValue(prometheusBaseURL, query)

        int count = 1
        int calls = 20
        http500(count, calls)
        Thread.sleep(waitTimeMillis)
        def result = prometheus.getSingleValue(prometheusBaseURL, query)

        Assert.assertTrue(result >= initial + calls)

        Reporter.log("- Prometheus. Prometheus successfully detects the number of HTTP 500 errors in the past " +
                "Number of errors: ${result - initial}", true)
    }

    @Test(priority=4, groups=["prometheus"], testName = "Artifactory. HTTP Response Codes")
    void httpResponseCodesTest() throws Exception {
        //def query = "sum by (return_status) (increase(jfrog_rt_req[2m]))" // old
        def query = "sum by (return_status) (jfrog_rt_req_total)" // new
        def initial = prometheus.getMapValues(prometheusBaseURL, query, ["return_status"])

        int count = 1
        int calls = 20
        // Generate HTTP responses in Artifactory
        http201(count, calls)
        http204(count, calls)
        http404(count, calls)
        Thread.sleep(waitTimeMillis)
        def result = prometheus.getMapValues(prometheusBaseURL, query, ["return_status"])

        for (String statusCode in ["201", "204", "404"]) {
            Assert.assertTrue(result.getOrDefault([statusCode], 0) >= initial.getOrDefault([statusCode], 0) + calls,
                    "Error ${statusCode} increased by count")
        }

        Reporter.log("- Prometheus. Prometheus successfully detects the number of HTTP responses in the Artifactory log", true)
    }

    @Test(priority=5, groups=["prometheus"], testName = "Artifactory. Top 10 IPs By Uploads")
    void top10ipUploadTest() throws Exception {
        def query = "sum by (remote_address) (jfrog_rt_data_upload_total)"
        def initial = prometheus.getMapValues(prometheusBaseURL, query, ["remote_address"])

        int count = 1
        int calls = 10
        uploadIntoRepo(count, calls)
        Thread.sleep(waitTimeMillis)
        def result = prometheus.getMapValues(prometheusBaseURL, query, ["remote_address"])

        // metric values should be valid IP address
        // at least one should have increased
        def increased = false

        result.each {
            Assert.assertTrue(Utils.validateIPAddress(it.key[0].strip()), "Is IP address")
            if (it.value > initial.getOrDefault(it.key, 0)) {
                increased = true
            }
        }
        Assert.assertTrue(increased, "At least one IP address uploaded")

        Reporter.log("- Prometheus. Top 10 IPs By Uploads verified", true)
    }

    @Test(priority=6, groups=["prometheus"], testName = "Artifactory. Top 10 IPs By Downloads")
    void top10ipDownloadTest() throws Exception {
        def query = "sum by (remote_address) (jfrog_rt_data_download_total)"
        def initial = prometheus.getMapValues(prometheusBaseURL, query, ["remote_address"])

        int count = 1
        int calls = 10
        downloadArtifact(count, calls)
        Thread.sleep(waitTimeMillis)
        def result = prometheus.getMapValues(prometheusBaseURL, query, ["remote_address"])

        // metric values should be valid IP address
        // at least one should have increased
        def increased = false

        result.each {
            Assert.assertTrue(Utils.validateIPAddress(it.key[0].strip()), "Is IP address")
            if (it.value > initial.getOrDefault(it.key, 0)) {
                increased = true
            }
        }
        Assert.assertTrue(increased, "At least one IP address downloaded")

        Reporter.log("- Prometheus. Top 10 IPs By Downloads verified", true)
    }

    @Test(priority=7, groups=["prometheus"], testName = "Artifactory, Audit. Get initial values for tests")
    void rtInitialUserStats() {
        initial_rtAuditByUsersTest = prometheus.getMapValues(prometheusBaseURL, query_rtAuditByUsersTest, ["user"])
        initial_rtDeniedActionByUsersTest = prometheus.getMapValues(prometheusBaseURL, query_rtDeniedActionByUsersTest, ["username"])
        initial_rtDeniedActionByUserIPTest = prometheus.getMapValues(prometheusBaseURL, query_rtDeniedActionByUserIPTest, ["username", "ip"])
        initial_rtDeniedLoginsByIPTest = prometheus.getMapValues(prometheusBaseURL, query_rtDeniedLoginsByIPTest, ["ip"])
        initial_rtDeniedActionsByIPTest = prometheus.getMapValues(prometheusBaseURL, query_rtDeniedActionsByIPTest, ["ip"])
        initial_rtAcceptedDeploysByUsernameTest = prometheus.getMapValues(prometheusBaseURL, query_rtAcceptedDeploysByUsernameTest, ["username"])
    }

    @Test(priority=8, groups=["prometheus"], dataProvider = "users", testName = "Artifactory, Audit. Generate data with data provider")
    void generateDataTest(usernameRt, emailRt, passwordRt, incorrectPasswordRt) {
        // Deploy as non-existent users, 401
        deployArtifactAs(usernameRt, passwordRt, 401)
        createUsers(usernameRt, emailRt, passwordRt)
        // Deploy with incorrect password, 401 expected
        deployArtifactAs(usernameRt, incorrectPasswordRt, 401)
        // Users have no access to target repo, 403 expected
        deployArtifactAs(usernameRt, passwordRt, 403)
        // Give access
        addPermissions(usernameRt)
        // Deploy again, 201 expected
        deployArtifactAs(usernameRt, passwordRt, 201)
        // Delete users
        securitySteps.deleteUser(artifactoryURL, usernameRt)
        Thread.sleep(waitTimeMillis)
    }



    @Test(priority=9, groups=["prometheus"], testName = "Artifactory, Audit. Audit Actions by Users")
    void rtAuditByUsersTest() throws Exception {
        // query_rtAuditByUsersTest
        // initial_rtAuditByUsersTest

        def result = prometheus.getMapValues(prometheusBaseURL, query_rtAuditByUsersTest, ["user"])

        Assert.assertTrue(result.getOrDefault([username], 0) > initial_rtAuditByUsersTest.getOrDefault([username], 0))

        Reporter.log("- Prometheus. Artifactory, Audit Actions by Users verification.", true)
    }

    @Test(priority=10, groups=["prometheus"], testName = "Artifactory, Audit. Denied Actions by Username")
    void rtDeniedActionByUsersTest() throws Exception {
        // query_rtDeniedActionByUsersTest
        // initial_rtDeniedActionByUsersTest

        def result = prometheus.getMapValues(prometheusBaseURL, query_rtDeniedActionByUsersTest, ["username"])

        for(user in users()) {
            def username = "${user[0]} ".toString()  // has extra space
            Assert.assertTrue(result.getOrDefault([username], 0) > initial_rtDeniedActionByUsersTest.getOrDefault([username], 0))
        }

        Reporter.log("- Prometheus. Artifactory, Denied Actions by Username verification.", true)
    }

    @Test(priority=11, groups=["prometheus"], testName = "Artifactory, Audit. Denied Logins By username and IP")
    void rtDeniedActionByUserIPTest() throws Exception {
        // query_rtDeniedActionByUserIPTest
        // initial_rtDeniedActionByUserIPTest
        def result = prometheus.getMapValues(prometheusBaseURL, query_rtDeniedActionByUserIPTest, ["username", "ip"])

        // we already know usernames are valid from last test, lets just check that IPs are indeed IPs
        result.each {
            Assert.assertTrue(Utils.validateIPAddress(it.key[1].strip()), "Is IP address")
            Assert.assertTrue(!it.key[0].startsWith("testuser") || it.value > initial_rtDeniedActionByUserIPTest.getOrDefault(it.key, 0))
        }

        Reporter.log("- Prometheus. Artifactory, Denied Actions by Username verification.", true)
    }

    @Test(priority=12, groups=["prometheus"], testName = "Artifactory, Audit. Denied Logins by IP")
    void rtDeniedLoginsByIPTest() throws Exception {
        // query_rtDeniedLoginsByIPTest
        // initial_rtDeniedLoginsByIPTest
        def result = prometheus.getMapValues(prometheusBaseURL, query_rtDeniedLoginsByIPTest, ["ip"])
        def increased = false
        result.each {
            Assert.assertTrue(Utils.validateIPAddress(it.key[0].strip()), "Is IP address")
            if (it.value > initial_rtDeniedLoginsByIPTest.getOrDefault(it.key, 0)) {
                increased = true
            }
        }
        Assert.assertTrue(increased, "At least one IP increased")

        Reporter.log("- Prometheus. Artifactory, Denied Logins by IP verification.", true)
    }

    @Test(priority=13, groups=["prometheus"], testName = "Artifactory, Audit. Denied Actions by IP")
    void rtDeniedActionsByIPTest() throws Exception {
        // query_rtDeniedActionsByIPTest
        // initial_rtDeniedActionsByIPTest
        def result = prometheus.getMapValues(prometheusBaseURL, query_rtDeniedActionsByIPTest, ["ip"])
        def increased = false
        result.each {
            Assert.assertTrue(Utils.validateIPAddress(it.key[0].strip()), "Is IP address")
            if (it.value > initial_rtDeniedActionsByIPTest.getOrDefault(it.key, 0)) {
                increased = true
            }
        }
        Assert.assertTrue(increased, "At least one IP increased")

        Reporter.log("- Prometheus. Artifactory, Denied Logins by IP verification.", true)
    }

    @Test(priority=14, groups=["prometheus"], testName = "Artifactory, Audit. Accepted Deploys by Username")
    void rtAcceptedDeploysByUsernameTest() throws Exception {
        // query_rtAcceptedDeploysByUsernameTest
        // initial_rtAcceptedDeploysByUsernameTest

        def result = prometheus.getMapValues(prometheusBaseURL, query_rtAcceptedDeploysByUsernameTest, ["username"])

        for(user in users()) {
            def username = "${user[0]} ".toString()  // has extra space
            println "${username} initial ${initial_rtAcceptedDeploysByUsernameTest.getOrDefault([username], 0)} final ${result.getOrDefault([username], 0)}"
            Assert.assertTrue(!username.startsWith("testuser") || result.getOrDefault([username], 0) > initial_rtAcceptedDeploysByUsernameTest.getOrDefault([username], 0))
        }

        Reporter.log("- Prometheus. Artifactory, Accepted Deploys by Username verification.", true)
    }


    @Test(priority=15, groups=["prometheus_xray"], testName = "Xray. HTTP Response Codes")
    void xrayLogErrorsTest() throws Exception {
        def query = "sum(jfrog_xray_log_level{log_level=\"ERROR\"})"
        def initial = prometheus.getSingleValue(prometheusBaseURL, query)

        int count = 1
        int calls = 20
        // Generate xray calls
        xray200(count, calls)
        xray500(count, calls)
        Thread.sleep(waitTimeMillis)
        def result = prometheus.getSingleValue(prometheusBaseURL, query)

        Assert.assertTrue(result > initial)

        Reporter.log("- Prometheus. Xray, Log errors verification. Prometheus shows response codes generated by Xray" +
                " during the test", true)
    }

    @Test(priority=16, groups=["prometheus_xray"], testName = "Xray. HTTP 500 Errors")
    void xrayHttp500ErrorsTest() throws Exception {
        def query = "sum(jfrog_xray_req{return_status=~\"5.*\"})"
        def initial = prometheus.getSingleValue(prometheusBaseURL, query)

        int count = 1
        int calls = 20
        // Generate xray calls
        xray500(count, calls)
        Thread.sleep(waitTimeMillis)
        def result = prometheus.getSingleValue(prometheusBaseURL, query)

        Assert.assertTrue(result > initial)

        Reporter.log("- Prometheus. Xray, HTTP 500 Errors verification. Prometheus shows errors generated by Xray" +
                " during the test", true)
    }

    @Test(priority=17, groups=["prometheus_xray"], testName = "Xray. HTTP Response Codes")
    void xrayHttpResponseCodesTest() throws Exception {
        def query = "sum by (return_status) (jfrog_xray_req)"
        def initial = prometheus.getMapValues(prometheusBaseURL, query, ["return_status"])

        int count = 1
        int calls = 20
        // Generate xray calls
        xray201(count, calls)
        xray500(count, calls)
        Thread.sleep(waitTimeMillis)
        def result = prometheus.getMapValues(prometheusBaseURL, query, ["return_status"])

        for (String statusCode in ["201", "500"]) {
            Assert.assertTrue(result.getOrDefault([statusCode], 0) >= initial.getOrDefault([statusCode], 0) + calls,
                    "Error ${statusCode} increased by count")
        }

        Reporter.log("- Prometheus. Xray, HTTP Response Codes verification. Prometheus shows responses generated by Xray" +
                " during the test.", true)
    }
}
