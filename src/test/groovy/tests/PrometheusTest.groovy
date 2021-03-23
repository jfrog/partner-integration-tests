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
import steps.RepositorySteps
import steps.SecuritytSteps
import steps.PrometheusSteps
import utils.Utils

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.hasItems

class PrometheusTest extends DataAnalyticsSteps{

    def artifact = new File("./src/test/resources/repositories/artifact.zip")
    def repoSteps = new RepositorySteps()
    def securitySteps = new SecuritytSteps()
    def prometheus = new PrometheusSteps()
    def utils = new Utils()

    @BeforeSuite(groups=["prometheus", "prometheus_xray"])
    def setUp() {
        RestAssured.baseURI = "${artifactoryBaseURL}/artifactory"
        RestAssured.authentication = RestAssured.basic(username, password)
        RestAssured.useRelaxedHTTPSValidation()
    }

    @Test(priority=1, groups=["prometheus"], testName = "Artifactory. Upload Data Transfers")
    void uploadDataTest() throws Exception {

        int count = 1
        int calls = 5
        uploadIntoRepo(count, calls)
        Thread.sleep(10000)
        def query = "sum(rate(jfrog_rt_data_upload[5m]))"
        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        JsonPath jsonPathEvaluator = response.jsonPath()
        def result = jsonPathEvaluator.get("data.result[0].value[1]").toString().toDouble()
        // the result is a per-second calculated rate which would be difficult to get an exact value and also depends on network
        Assert.assertTrue(result > 0)

        Reporter.log("- Prometheus. Upload", true)

    }

    @Test(priority=2, groups=["prometheus"], testName = "Artifactory. Download Data Transfers")
    void downloadDataTest() throws Exception {

        int count = 1
        int calls = 5
        downloadArtifact(count, calls)
        Thread.sleep(10000)
        def query = "sum(rate(jfrog_rt_data_download[5m]))"
        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        JsonPath jsonPathEvaluator = response.jsonPath()
        def result = jsonPathEvaluator.get("data.result[0].value[1]").toString().toDouble()
        // the result is a per-second calculated rate which would be difficult to get an exact value and also depends on network
        Assert.assertTrue(result > 0)

        Reporter.log("- Prometheus. Download", true)

    }

    @Test(priority=1, groups=["prometheus"], testName = "Artifactory. HTTP 500 Errors")
    void http500ErrorsTest() throws Exception {
        // Generate error 500 - post callhome data
        int count = 1
        int calls = 20
        http500(count, calls)
        Thread.sleep(10000)
        def query = "sum(increase(jfrog_rt_req{return_status=~\"5.*\"}[1m]))"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        JsonPath jsonPathEvaluator = response.jsonPath()
        def result = jsonPathEvaluator.get("data.result[0].value[1]").toString().toDouble()
        Assert.assertTrue((result > 0))

        Reporter.log("- Prometheus. Prometheus successfully detects the number of HTTP 500 errors in the past " +
                "Number of errors: ${result}", true)
    }

    @Test(priority=2, groups=["prometheus"], testName = "Artifactory. HTTP Response Codes")
    void httpResponseCodesTest() throws Exception {
        int count = 1
        int calls = 20
        // Generate HTTP responses in Artifactory
        http201(count, calls)
        http204(count, calls)
        http404(count, calls)
        Thread.sleep(10000)
        def query = "sum by (return_status) (increase(jfrog_rt_req[2m]))"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<String> statusCodes = jsonPathEvaluator.getList("data.result.metric.return_status")
        for (int i=0;i<statusCodes.size();i++) {
            if (statusCodes[i] == "201" || statusCodes[i] == "204" || statusCodes[i] == "404") {
                def result = jsonPathEvaluator.get("data.result[${i}].value[1]").toString().toDouble()
                Assert.assertTrue((result > 0))
            }
        }

        Reporter.log("- Prometheus. Prometheus successfully detects the number of HTTP responses in the Artifactory log", true)
    }

    @Test(priority=3, groups=["prometheus"], testName = "Artifactory. Top 10 IPs By Uploads")
    void top10ipUploadTest() throws Exception {
        int count = 1
        int calls = 10
        uploadIntoRepo(count, calls)
        Thread.sleep(10000)
        def query = "sum by (remote_address) (increase(jfrog_rt_data_upload[5m])) > 0"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        def IPv4andIPv6Regex = "((^\\s*((([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5]))\\s*\$)|(^\\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*\$))"
        response.then().
                body("data.result.metric.remote_address", hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("data.result.value[1]", Matchers.notNullValue())

        Reporter.log("- Prometheus. Top 10 IPs By Uploads verified", true)
    }

    @Test(priority=4, groups=["prometheus"], testName = "Artifactory. Top 10 IPs By Downloads")
    void top10ipDownloadTest() throws Exception {
        int count = 1
        int calls = 10
        downloadArtifact(count, calls)
        Thread.sleep(10000)
        def query = "sum by (remote_address) (increase(jfrog_rt_data_download[5m])) > 0"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        def IPv4andIPv6Regex = "((^\\s*((([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5]))\\s*\$)|(^\\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*\$))"
        response.then().
                body("data.result.metric.remote_address", hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("data.result[0].value[1]", Matchers.notNullValue())
        Reporter.log("- Prometheus. Top 10 IPs By Downloads verified", true)
    }

    @Test(priority=5, groups=["prometheus"], dataProvider = "users", testName = "Artifcatory, Audit. Generate data with data provider")
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
    }

    @Test(priority=6, groups=["prometheus"], testName = "Artifcatory, Audit. Audit Actions by Users")
    void rtAuditByUsersTest() throws Exception {
        def query = "sum by (user) (increase(jfrog_rt_access_audit{user!=\"UNKNOWN\", user!=\"_system_\",user!=\"\"}[10m]))"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()
        response.then().assertThat().statusCode(200)
                .body("data.result.metric.user[0]", equalTo(username))
        JsonPath jsonPathEvaluator = response.jsonPath()
        def count = jsonPathEvaluator.get("data.result[0].value[1]").toString().toDouble()
        Assert.assertTrue((count >= 1))

        Reporter.log("- Prometheus. Artifactory, Audit Actions by Users verification.", true)
    }

    @Test(priority=7, groups=["prometheus"], testName = "Artifcatory, Audit. Denied Actions by Username")
    void rtDeniedActionByUsersTest() throws Exception {
        def query = "sum by (user) (increase(jfrog_rt_access{user!=\"UNKNOWN\", user!=\"_system_\", action_response=~\"DENIED.*\"}[10m]) > 0)"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        List<String> usernames = ["splunktest0", "splunktest1", "splunktest2"]
        for(user in usernames) {
            response.then().
                    body("data.result.metric.user", hasItems(user))
        }

        Reporter.log("- Prometheus. Artifactory, Denied Actions by Username verification.", true)
    }

    @Test(priority=8, groups=["prometheus"], testName = "Artifcatory, Audit. Denied Logins By username and IP")
    void rtDeniedActionByUserIPTest() throws Exception {
        def query = "sum by (user,ip) (increase(jfrog_rt_access{user!=\"UNKNOWN\", user!=\"_system_\", action_response=~\"DENIED.*\"}[10m]) > 0)"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        List<String> usernames = ["splunktest0", "splunktest1", "splunktest2"]
        for(user in usernames) {
            response.then().
                    body("data.result.metric.user", hasItems(user))
        }

        response.then().
            body("data.result.metric.ip", hasItems(Matchers.notNullValue()))

        Reporter.log("- Prometheus. Artifactory, Denied Actions by Username verification.", true)
    }

    @Test(priority=9, groups=["prometheus"], testName = "Artifcatory, Audit. Denied Logins by IP")
    void rtDeniedLoginsByIPTest() throws Exception {
        def query = "sum by (ip) (increase(jfrog_rt_access{user!=\"UNKNOWN\", user!=\"_system_\", action_response=~\"DENIED LOGIN\"}[10m]) > 0)"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        response.then().
                body("data.result.metric.ip", hasItems(Matchers.notNullValue()))

        Reporter.log("- Prometheus. Artifactory, Denied Logins by IP verification.", true)
    }

    @Test(priority=10, groups=["prometheus"], testName = "Artifcatory, Audit. Denied Actions by IP")
    void rtDeniedActionsByIPTest() throws Exception {
        def query = "sum by (ip) (increase(jfrog_rt_access{user!=\"UNKNOWN\", user!=\"_system_\", action_response=~\"DENIED.*\"}[10m]) > 0)"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        response.then().
                body("data.result.metric.ip", hasItems(Matchers.notNullValue()))

        Reporter.log("- Prometheus. Artifactory, Denied Logins by IP verification.", true)

        Reporter.log("- Prometheus. Artifactory, Denied Actions by Username verification.", true)
    }

    @Test(priority=11, groups=["prometheus"], testName = "Artifcatory, Audit. Accepted Deploys by Username")
    void rtAcceptedDeploysByUsernameTest() throws Exception {
        def query = "sum by (user) (increase(jfrog_rt_access{user!=\"UNKNOWN\", user!=\"_system_\", action_response=~\"ACCEPTED DEPLOY\"}[10m]) > 0)"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        List<String> usernames = ["splunktest0", "splunktest1", "splunktest2"]
        for(user in usernames) {
            response.then().
                    body("data.result.metric.user", hasItems(user))
        }

        Reporter.log("- Prometheus. Artifactory, Accepted Deploys by Username verification.", true)
    }

    @Test(priority=12, groups=["prometheus_xray"], testName = "Xray. HTTP Response Codes")
    void xrayLogErrorsTest() throws Exception {
        int count = 1
        int calls = 20
        // Generate xray calls
        xray200(count, calls)
        xray500(count, calls)
        Thread.sleep(10000)
        def query = "sum(increase(jfrog_xray_log_level{log_level=\"ERROR\"}[1m]))"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        JsonPath jsonPathEvaluator = response.jsonPath()
        def result = jsonPathEvaluator.get("data.result[0].value[1]").toString().toDouble()
        Assert.assertTrue((result > 0))

        Reporter.log("- Prometheus. Xray, Log errors verification. Prometheus shows response codes generated by Xray" +
                " during the test", true)
    }

    @Test(priority=13, groups=["prometheus_xray"], testName = "Xray. HTTP 500 Errors")
    void xrayHttp500ErrorsTest() throws Exception {
        int count = 1
        int calls = 20
        // Generate xray calls
        xray500(count, calls)
        Thread.sleep(30000)
        def query = "sum(increase(jfrog_xray_req{return_status=~\"5.*\"}[1m]))"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        JsonPath jsonPathEvaluator = response.jsonPath()
        def result = jsonPathEvaluator.get("data.result[0].value[1]").toString().toDouble()
        Assert.assertTrue(result > 0)

        Reporter.log("- Prometheus. Xray, HTTP 500 Errors verification. Prometheus shows errors generated by Xray" +
                " during the test", true)
    }

    @Test(priority=14, groups=["prometheus_xray"], testName = "Xray. HTTP Response Codes")
    void xrayHttpResponseCodesTest() throws Exception {
        int count = 1
        int calls = 20
        // Generate xray calls
        xray201(count, calls)
        xray500(count, calls)
        Thread.sleep(30000)
        def query = "sum by (return_status) (increase(jfrog_xray_req[2m]))"

        Response response = prometheus.postQuery(prometheusBaseURL, query)
        response.then().log().everything()

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<String> statusCodes = jsonPathEvaluator.getList("data.result.metric.return_status")
        for (int i=0;i<statusCodes.size();i++) {
            if (statusCodes[i] == "201" || statusCodes[i] == "500") {
                def result = jsonPathEvaluator.get("data.result[${i}].value[1]").toString().toDouble()
                Assert.assertTrue(result > 0)
            }
        }

        Reporter.log("- Prometheus. Xray, HTTP Response Codes verification. Prometheus shows responses generated by Xray" +
                " during the test.", true)
    }
}
