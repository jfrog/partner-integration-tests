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
import org.yaml.snakeyaml.Yaml
import steps.DataAnalyticsSteps
import steps.RepositorySteps
import utils.Utils
import steps.SecuritytSteps
import steps.SplunkSteps
import java.util.concurrent.TimeUnit

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.hasItems


/**
 PTRENG-975 Splunk log analytic integration tests.
 Test will generate traffic on the Artifactory instance (with or without Xray installed), then verify Splunk
 can parse the logs. Splunk API is used to verify the same Splunk search queries, used in the UI charts.
 */

class SplunkTest extends DataAnalyticsSteps{

    Yaml yaml = new Yaml()
    def configFile = new File("./src/test/resources/testenv.yaml")
    def config = yaml.load(configFile.text)
    def artifact = new File("./src/test/resources/repositories/artifact.zip")
    def repoSteps = new RepositorySteps()
    def securitySteps = new SecuritytSteps()
    def splunk = new SplunkSteps()
    def utils = new Utils()
    def artifactoryURL
    def dockerURL
    def distribution
    def username
    def password
    def splunk_username
    def splunk_password
    def splunk_url


    @BeforeSuite(groups=["splunk", "splunk_xray"])
    def setUp() {
        artifactoryURL = config.artifactory.external_ip
        dockerURL = config.artifactory.url
        distribution = config.artifactory.distribution
        username = config.artifactory.rt_username
        password = config.artifactory.rt_password
        splunk_username = config.splunk.username
        splunk_password = config.splunk.password
        splunk_url = "${config.splunk.protocol}" + "${config.splunk.xrayBaseUrl}" + ":" + "${config.splunk.port}"
        RestAssured.baseURI = "http://${artifactoryURL}/artifactory"
        RestAssured.authentication = RestAssured.basic(username, password)
        RestAssured.useRelaxedHTTPSValidation()
    }

    @Test(priority=1, groups=["splunk"], testName = "Artifactory. HTTP 500 Errors")
    void http500errorsTest() throws Exception {
        // Generate error 500 - post callhome data
        int count = 1
        int calls = 5
        http500(count, calls)
        Thread.sleep(30000)
        // Create a search job in Splunk with given parameters, return Search ID
        // 'earliest=' and 'span=' added to the original query to optimize the output
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR log_source="jfrog.rt.artifactory.request")  return_status="5*" earliest=-10m | timechart span=300 count by return_status'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        // Verify the number of errors in the report is => the number of API calls sent
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCounts = jsonPathEvaluator.getList("results.500", Integer.class)
        Assert.assertTrue((errorCounts.sum()) >= calls)
        // Verify the last record in the response has current date
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        utils.verifySplunkDate(date)

        Reporter.log("- Splunk. Splunk successfully detects the number of errors in the past " +
                "24 hours in the Artifactory log. Number of errors: ${errorCounts.sum()} date: ${date.substring(0,10)}", true)
    }

    @Test(priority=2, groups=["splunk"], testName = "Artifactory. HTTP Response Codes")
    void httpResponseCodesTest() throws Exception {
        int count = 1
        int calls = 5
        // Generate HTTP responses in Artifactory
        http200(count, calls)
        http201(count, calls)
        http204(count, calls)
        http403(count, calls)
        http404(count, calls)
        http500(count, calls)
        Thread.sleep(30000)
        // Create a search job in Splunk with given parameters, return Search ID
        // 'earliest=' and 'span=' added to the original query to optimize the output
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR log_source="jfrog.rt.artifactory.request") earliest=-10m | timechart span=300 count by return_status'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)
        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        // Verify Splunk response
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        JsonPath jsonPathEvaluator = response.jsonPath()

        def responseCodes = ["200","201","204","403","404","500"]
        for (responseCode in responseCodes) {
            List<Integer> respCount = jsonPathEvaluator.getList("results.${responseCode}", Integer.class)
            Assert.assertTrue((respCount.sum()) >= calls)
        }
        // Verify the last record in the response has current date
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == utils.getUTCdate())

        Reporter.log("- Splunk. Splunk successfully detects the number of HTTP responses in the Artifactory log", true)
    }

    @Test(priority=3, groups=["splunk"], testName = "Artifactory. Top 10 IPs By Uploads")
    void top10ipUploadTest() throws Exception {
        int count = 1
        int calls = 5
        uploadIntoRepo(count, calls)
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR log_source="jfrog.rt.artifactory.request") response_content_length!="-1" | eval gb=response_content_length/1073741824 | stats sum(gb) as upload_size by remote_address | top limit=10 remote_address,upload_size | fields - count,percent'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        def IPv4andIPv6Regex = "((^\\s*((([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5]))\\s*\$)|(^\\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*\$))"
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        response.then().
                body("results.remote_address", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("results.upload_size", Matchers.notNullValue())

        Reporter.log("- Splunk. Top 10 IPs By Uploads verified", true)
    }

    @Test(priority=4, groups=["splunk"], testName = "Artifactory. Top 10 IPs By Downloads")
    void top10ipDownloadTest() throws Exception {
        int count = 1
        int calls = 5
        downloadArtifact(count, calls)
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR log_source="jfrog.rt.artifactory.request") request_content_length!="-1" | eval gb=request_content_length/1073741824 | stats sum(gb) as download_size by remote_address | top limit=10 remote_address,download_size | fields - count,percent'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        def IPv4andIPv6Regex = "((^\\s*((([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5]))\\s*\$)|(^\\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*\$))"
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        response.then().
                body("results.remote_address", Matchers.hasItems(Matchers.matchesRegex(IPv4andIPv6Regex))).
                body("results.upload_size", Matchers.notNullValue())

        Reporter.log("- Splunk. Top 10 IPs By Downloads verified", true)
    }

    @Test(priority=5, groups=["splunk"], testName = "Artifactory. Accessed Docker Images")
    void accessedImagesTest() throws Exception {
        def image = "busybox"
        def numberOfImages = 5
        def repos = ["docker-dev-local", "docker-local"]
        // Docker login, pull busybox, generate and push multiple dummy images
        utils.dockerLogin(username, password, dockerURL)
        utils.dockerPullImage(image)
        utils.dockerGenerateImages(repos, numberOfImages, image, dockerURL)
        Thread.sleep(60000)
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR log_source="jfrog.rt.artifactory.request") request_url="/api/docker/*" repo!="NULL" image!="NULL" repo!="" image!="" repo!="latest" earliest=-10m | timechart span=300 count by image'
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        JsonPath jsonPathEvaluator = response.jsonPath()
        for (int i = 1; i <= numberOfImages; i++) {
            List<Integer> result = jsonPathEvaluator.getList("results.${image}${i}", Integer.class)
            Assert.assertTrue((result.sum()) >= numberOfImages)
        }
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        utils.verifySplunkDate(date)

        Reporter.log("- Splunk. Accessed Docker Images information is verified", true)
    }

    @Test(priority=6, groups=["splunk"], testName = "Artifactory. Accessed Docker Repos")
    void accessedReposTest() throws Exception {
        def repos = ["${dockerURL}/docker-dev-local/busybox1:1.1", "${dockerURL}/docker-local/busybox1:1.1"]
        utils.dockerLogin(username, password, dockerURL)
        for(i in repos) {
            utils.dockerPullImage(i)
        }
        Thread.sleep(30000)
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR log_source="jfrog.rt.artifactory.request") request_url="/api/docker/*" repo!="NULL" image!="NULL" repo!="" image!="" repo!="latest" earliest=-10m | timechart span=300 count by repo'
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        JsonPath jsonPathEvaluator = response.jsonPath()
        def repoNames = ["docker-dev-local", "docker-local"]
        for (i in repoNames) {
            List<Integer> result = jsonPathEvaluator.getList("results.${i}", Integer.class)
            Assert.assertTrue((result.sum()) > 0)
        }
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        utils.verifySplunkDate(date)

        Reporter.log("- Splunk. Accessed Docker Repos information is verified", true)
    }

    @Test(priority=7, groups=["splunk"], testName = "Artifactory. Data Transfers (GBs) Uploads By Repo")
    void dataTransferUploadeTest() throws Exception {

        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR log_source="jfrog.rt.artifactory.request") request_url="/api/docker/*" repo!="NULL" image!="NULL" repo!="" image!="" repo!="latest" | eval gb=response_content_length/1073741824 | stats sum(gb) as GB by repo | where GB > 0'
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        List<String> repoNames = ["docker-dev-local", "docker-local"]
        for(repo in repoNames) {
            response.then().
                    body("results.repo", Matchers.hasItems(repo)).
                    body("results.GB", Matchers.notNullValue())
        }
        Reporter.log("- Splunk. Data Transfers (GBs) Uploads By Repo information is verified", true)
    }

    @Test(priority=8, groups=["splunk"], testName = "Artifactory. Data Transfers (GBs) Downloads By Repo")
    void dataTransferDownloadsTest() throws Exception {

        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR log_source="jfrog.rt.artifactory.request") request_url="/api/docker/*" repo!="NULL" image!="NULL" repo!="" image!="" repo!="latest" | eval gb=request_content_length/1073741824 | stats sum(gb) by repo'
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        List<String> repoNames = ["docker-dev-local", "docker-local"]
        for(repo in repoNames) {
            response.then().
                    body("results.repo", Matchers.hasItems(repo))
        }

        Reporter.log("- Splunk. Data Transfers (GBs) Downloads By Repo information is verified", true)
    }

    @Test(priority=9, groups=["splunk"], testName = "Artifactory, Application. Log Volume")
    void rtLogVolumeTest() throws Exception {
        int count = 1
        int calls = 5
        // Generate artifactory calls
        http200(count, calls)
        http201(count, calls)
        http204(count, calls)
        http403(count, calls)
        http404(count, calls)
        http500(count, calls)
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search log_source!="NULL" | timechart count by log_source'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)

        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == utils.getUTCdate())

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> accessCounts = jsonPathEvaluator.getList("results.'jfrog.rt.access.request'", Integer.class)
        List<Integer> artifactoryCounts = jsonPathEvaluator.getList("results.'jfrog.rt.artifactory.request'", Integer.class)
        List<Integer> routerCounts = jsonPathEvaluator.getList("results.'jfrog.rt.router.request'", Integer.class)
        Assert.assertTrue((accessCounts.sum()) >= calls)
        Assert.assertTrue((artifactoryCounts.sum()) >= calls)
        Assert.assertTrue((routerCounts.sum()) >= calls)

        Reporter.log("- Splunk. Artifactory, Log volume verification. Each log record has values", true)
    }

    @Test(priority=10, groups=["splunk"], testName = "Artifcatory, Application. Log Errors")
    void rtLogErrorsTest() throws Exception {
        int count = 1
        int calls = 5
        // Generate Artifactory calls
        http403(count, calls)
        http404(count, calls)
        http500(count, calls)
        Thread.sleep(40000)
        // Create a search job in Splunk with given parameters, return Search ID
        // 'earliest=' and 'span=' added to the original query to optimize the output
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.service" OR log_source="jfrog.rt.artifactory.service") log_level="ERROR" | timechart count by log_level'

        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)
        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.ERROR", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= calls)
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == utils.getUTCdate())

        Reporter.log("- Splunk. Artifactory, Log Errors verification. Splunk shows errors generated by Artifactory" +
                " during the test", true)
    }

    @Test(priority=11, groups=["splunk"], dataProvider = "users", testName = "Artifcatory, Audit. Generate data with data provider")
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

    @Test(priority=12, groups=["splunk"], testName = "Artifcatory, Audit. Audit Actions by Users")
    void rtAuditByUsersTest() throws Exception {
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.access.audit" OR log_source="jfrog.rt.access.audit") user!="UNKNOWN" | stats count by user'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)
        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        response.then().assertThat().statusCode(200)
                .body("results[0].user", equalTo(username))
        JsonPath jsonPathEvaluator = response.jsonPath()
        def count = jsonPathEvaluator.getInt("results[0].count")
        Assert.assertTrue((count >= 1))

        Reporter.log("- Splunk. Artifactory, Audit Actions by Users verification.", true)
    }

    @Test(priority=13, groups=["splunk"], testName = "Artifcatory, Audit. Denied Actions by Username")
    void rtDeniedActionByUsersTest() throws Exception {
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.access" OR log_source="jfrog.rt.artifactory.access") action_response="DENIED*" username!="NA " | stats  count by username'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)
        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.count", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= 1)
        List<String> usernames = ["testUser0 ", "testUser1 ", "testUser2 "]
        for(user in usernames) {
            response.then().
                    body("results.username", hasItems(user))
        }

        Reporter.log("- Splunk. Artifactory, Denied Actions by Username verification.", true)
    }

    @Test(priority=14, groups=["splunk"], testName = "Artifcatory, Audit. Denied Logins By username and IP")
    void rtDeniedActionByUserIPTest() throws Exception {
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.access" OR log_source="jfrog.rt.artifactory.access")  action_response="DENIED LOGIN" username!="NA " | stats count by ip,username'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)
        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)

        def IPv4andIPv6Regex = "([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3}"
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.count", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= 1)
//        response.then().
//            body("results.ip", hasItems(Matchers.matchesRegex(IPv4andIPv6Regex)))
        List<String> usernames = ["splunktest0 ", "splunktest1 ", "splunktest2 "]
        for(user in usernames) {
            response.then().
                    body("results.username", hasItems(user))
        }
        Reporter.log("- Splunk. Artifactory, Denied Actions by Username verification.", true)
    }

    @Test(priority=15, groups=["splunk"], testName = "Artifcatory, Audit. Denied Logins by IP")
    void rtDeniedLoginsByIPTest() throws Exception {
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.access" OR log_source="jfrog.rt.artifactory.access") action_response="DENIED LOGIN" | stats count by ip'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)
        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)

        def IPv4andIPv6Regex = "([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3}"
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.count", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= 1)
//        response.then().
//            body("results.ip", hasItems(Matchers.matchesRegex(IPv4andIPv6Regex)))

        Reporter.log("- Splunk. Artifactory, Denied Logins by IP verification.", true)
    }

    @Test(priority=16, groups=["splunk"], testName = "Artifcatory, Audit. Denied Actions by IP")
    void rtDeniedActionsByIPTest() throws Exception {
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.access" OR log_source="jfrog.rt.artifactory.access") action_response="DENIED*" | stats count by ip'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)
        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)

        def IPv4andIPv6Regex = "([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(\\d{1,3}\\.){3}\\d{1,3}"
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.count", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= 1)
//        response.then().
//            body("results.ip", hasItems(Matchers.matchesRegex(IPv4andIPv6Regex)))

        Reporter.log("- Splunk. Artifactory, Denied Actions by Username verification.", true)
    }

    @Test(priority=17, groups=["splunk"], testName = "Artifcatory, Audit. Accepted Deploys by Username")
    void rtAcceptedDeploysByUsernameTest() throws Exception {
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.access" OR log_source="jfrog.rt.artifactory.access") action_response="ACCEPTED DEPLOY" | stats count by username'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)
        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.count", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= 1)
        List<String> usernames = ["splunktest0 ", "splunktest1 ", "splunktest2 "]
        for(user in usernames) {
            response.then().
                    body("results.username", hasItems(user))
        }

        Reporter.log("- Splunk. Artifactory, Accepted Deploys by Username verification.", true)
    }

    @Test(priority=18, groups=["splunk_xray"], testName = "Xray. Log Volume")
    void xrayLogVolumeTest() throws Exception {
        int count = 1
        int calls = 5
        // Generate xray calls
        xray200(count, calls)
        xray201(count, calls)
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search sourcetype!="NULL" | timechart count by log_source'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        response.then().
                body("results.jfrog.rt.access.audit", Matchers.notNullValue()).
                body("results.jfrog.rt.access.request", Matchers.notNullValue()).
                body("results.jfrog.rt.artifactory.access", Matchers.notNullValue()).
                body("results.jfrog.rt.artifactory.request", Matchers.notNullValue()).
                body("results.jfrog.rt.artifactory.service", Matchers.notNullValue()).
                body("results.jfrog.rt.metadata.request", Matchers.notNullValue()).
                body("results.jfrog.rt.router.request", Matchers.notNullValue()).
                body("results.jfrog.xray.router.request", Matchers.notNullValue()).
                body("results.jfrog.xray.server.service", Matchers.notNullValue()).
                body("results.jfrog.xray.xray.request", Matchers.notNullValue()).
                body("results.OTHER", Matchers.notNullValue())
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == utils.getUTCdate())

        Reporter.log("- Splunk. Xray, Log volume verification. Each log record has values", true)
    }


    @Test(priority=19, groups=["splunk_xray"], testName = "Xray. Log Errors")
    void logErrorsTest() throws Exception {
        int count = 1
        int calls = 5
        // Generate xray calls
        xray200(count, calls)
        xray500(count, calls)
        Thread.sleep(20000)
        // Create a search job in Splunk with given parameters, return Search ID
        // 'earliest=' and 'span=' added to the original query to optimize the output
        def search_string = 'search=search (sourcetype="jfrog.xray.*.service" OR log_source="jfrog.xray.*.service") log_level="ERROR" earliest=-10m | timechart span=300 count by log_level'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.ERROR", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= calls)
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == utils.getUTCdate())

        Reporter.log("- Splunk. Xray, Log Errors verification. Splunk shows errors generated by Xray" +
                " during the test", true)
    }

    @Test(priority=20, groups=["splunk_xray"], testName = "Xray. HTTP 500 Errors")
    void error500Test() throws Exception {
        int count = 1
        int calls = 5
        // Generate xray calls
        xray500(count, calls)
        Thread.sleep(30000)
        // Create a search job in Splunk with given parameters, return Search ID
        // 'earliest=' and 'span=' added to the original query to optimize the output
        def search_string = 'search=search (sourcetype="jfrog.xray.xray.request" OR log_source="jfrog.xray.xray.request") return_status="5*" earliest=-10m | timechart span=300 count by return_status'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == utils.getUTCdate())
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.500", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= calls)

        Reporter.log("- Splunk. Xray, HTTP 500 Errors verification. Splunk shows errors generated by Xray" +
                " during the test", true)
    }

    @Test(priority=21, groups=["splunk_xray"], testName = "Xray. HTTP Response Codes")
    void httpResponsesTest() throws Exception {
        int count = 1
        int calls = 5
        // Generate xray calls
        xray200(count, calls)
        xray201(count, calls)
        xray409(count, calls)
        xray500(count, calls)
        Thread.sleep(30000)
        // Create a search job in Splunk with given parameters, return Search ID
        // 'earliest=' and 'span=' added to the original query to optimize the output
        def search_string = 'search=search (sourcetype="jfrog.xray.xray.request" OR log_source="jfrog.xray.xray.request") earliest=-10m | timechart span=300 count by return_status'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string)

        Awaitility.await().atMost(120, TimeUnit.SECONDS).until(() ->
                (splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)).then().extract().statusCode() == 200)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunk_url, searchID)
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == utils.getUTCdate())

        JsonPath jsonPathEvaluator = response.jsonPath()
        def responseCodes = ["200","201","409","500"]
        for (responseCode in responseCodes) {
            List<Integer> respCount = jsonPathEvaluator.getList("results.${responseCode}", Integer.class)
            Assert.assertTrue((respCount.sum()) >= calls)
        }

        Reporter.log("- Splunk. Xray, HTTP Response Codes verification. Splunk shows responses generated by Xray" +
                " during the test.", true)
    }

}

