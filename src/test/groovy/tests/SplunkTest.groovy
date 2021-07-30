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
import utils.Utils
import steps.SecuritytSteps
import steps.SplunkSteps
import steps.XraySteps

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.hasItems


/**
 PTRENG-975 Splunk log analytic integration tests.
 Test will generate traffic on the Artifactory instance (with or without Xray installed), then verify Splunk
 can parse the logs. Splunk API is used to verify the same Splunk search queries, used in the UI charts.
 */

class SplunkTest extends DataAnalyticsSteps{
    def artifact = new File("./src/test/resources/repositories/artifact.zip")
    def repoSteps = new RepositorySteps()
    def securitySteps = new SecuritytSteps()
    def splunk = new SplunkSteps()
    def xraySteps = new XraySteps()
    def earliest = "-10m"
    List<Object[]> license_issues = xraySteps.multipleLicenseIssueEvents()
    List<Object[]> security_issues = xraySteps.multipleIssueEvents()

    @BeforeSuite(groups=["splunk", "splunk_xray", "splunk_siem"])
    def setUp() {
        RestAssured.baseURI = "${artifactoryBaseURL}/artifactory"
        RestAssured.authentication = RestAssured.basic(username, password)
        RestAssured.useRelaxedHTTPSValidation()
    }

    // Artifactory Requests dashboard

    @Test(priority=1, groups=["splunk", "splunk_rt_requests"], testName = "Artifactory - Requests. HTTP 500 Errors")
    void http500errorsTest() throws Exception {
        // Generate error 500 - post callhome data
        int count = 1
        int calls = 5
        http500(count, calls)
        Thread.sleep(30000)
        // Create a search job in Splunk with given parameters, return Search ID
        // 'earliest=' and 'span=' added to the original query to optimize the output
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR ' +
                'log_source="jfrog.rt.artifactory.request")  return_status="5*" earliest=-10m | ' +
                'timechart span=300 count by return_status'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        // Verify the number of errors in the report is => the number of API calls sent
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCounts = jsonPathEvaluator.getList("results.500", Integer.class)
        Assert.assertTrue((errorCounts.sum()) >= calls)
        // Verify the last record in the response has current date
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Utils.verifySplunkDate(date)

        Reporter.log("- Splunk. Splunk successfully detects the number of errors in the past " +
                "24 hours in the Artifactory log. Number of errors: ${errorCounts.sum()} date: ${date.substring(0,10)}", true)
    }

    @Test(priority=2, groups=["splunk", "splunk_rt_requests"], testName = "Artifactory - Requests. HTTP Response Codes")
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
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR ' +
                'log_source="jfrog.rt.artifactory.request") earliest=-10m | timechart span=300 count by return_status'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        // Verify Splunk response
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        JsonPath jsonPathEvaluator = response.jsonPath()

        def responseCodes = ["200","201","204","403","404","500"]
        for (responseCode in responseCodes) {
            List<Integer> respCount = jsonPathEvaluator.getList("results.${responseCode}", Integer.class)
            Assert.assertTrue((respCount.sum()) >= calls)
        }
        // Verify the last record in the response has current date
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == Utils.getUTCdate())

        Reporter.log("- Splunk. Splunk successfully detects the number of HTTP responses in the Artifactory log", true)
    }

    @Test(priority=3, groups=["splunk", "splunk_rt_requests"], testName = "Artifactory - Requests. Top 10 IPs By Uploads")
    void top10ipUploadTest() throws Exception {
        int count = 1
        int calls = 5
        uploadIntoRepo(count, calls)
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR ' +
                'log_source="jfrog.rt.artifactory.request") response_content_length!="-1" | ' +
                'eval gb=response_content_length/1073741824 | stats sum(gb) as upload_size by remote_address | ' +
                'top limit=10 remote_address,upload_size | fields - count,percent'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().log().ifValidationFails().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        response.then().
                body("results.upload_size", Matchers.notNullValue())
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<String> IPAddresses = jsonPathEvaluator.getList("results.remote_address")
        for(ip in IPAddresses){
            Assert.assertTrue(splunk.validateIPAddress(ip.replaceAll("\\s","")))
        }

        Reporter.log("- Splunk. Top 10 IPs By Uploads verified", true)
    }

    @Test(priority=4, groups=["splunk", "splunk_rt_requests"], testName = "Artifactory - Requests. Top 10 IPs By Downloads")
    void top10ipDownloadTest() throws Exception {
        int count = 1
        int calls = 5
        downloadArtifact(count, calls)
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR ' +
                'log_source="jfrog.rt.artifactory.request") request_content_length!="-1" | ' +
                'eval gb=request_content_length/1073741824 | stats sum(gb) as download_size by remote_address | ' +
                'top limit=10 remote_address,download_size | fields - count,percent'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        response.then().
                body("results.upload_size", Matchers.notNullValue())
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<String> IPAddresses = jsonPathEvaluator.getList("results.remote_address")
        for(ip in IPAddresses){
            Assert.assertTrue(splunk.validateIPAddress(ip.replaceAll("\\s","")))
        }


        Reporter.log("- Splunk. Top 10 IPs By Downloads verified", true)
    }

    // Artifactory Docker dashboard

    @Test(priority=5, groups=["splunk", "splunk_rt_docker"], testName = "Artifactory - Docker. Dockerhub Pull Requests Trends Per 6 Hours")
    void dockerhubPRTrendsTest() throws Exception {
        def images = ["traefik", "alpine", "hello-world", "busybox"]
        Utils.dockerLogin(username, password, dockerURL)
        for(imageName in images) {
            Utils.dockerPullImage("${dockerURL}" + "/docker-remote/" + imageName)
        }
        // Clean up docker remote repository cache, to prevent Artifactory using it on the next run
        splunk.dockerCleanupCache(artifactoryBaseURL, username, password,
                "artifactory/docker-remote-cache/library/", images)
        Thread.sleep(30000)
        def search_string = 'search=search "downloading" log_source="jfrog.rt.artifactory.service" "manifests/" "docker.io" |' +
                ' spath message | search message !="*/manifests/sha256:*" | timechart count(message) span=6h as DockerPullRequests'
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        int size = response.then().extract().body().path("results.size()")
        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            String date = response.then().extract().body().path("results[${size - 1}]._time")
            Utils.verifySplunkDate(date)
            def requests = response.then().extract().body().path("results[${size - 1}].DockerPullRequests") as Integer
            Assert.assertTrue(requests >= images.size())
            Reporter.log("The number of image pulls - " + requests, true)
        }

        Reporter.log("- Splunk. Dockerhub Pull Requests Trends Per 6 Hours are verified", true)
    }


    @Test(priority=6, groups=["splunk", "splunk_rt_docker"], testName = "Artifactory - Docker. Docker Repositories Cache Hit Ratio")
    void dockerhubCacheHitRatioTest() throws Exception {
        def images = ["traefik", "alpine", "hello-world", "busybox"]
        Utils.dockerLogin(username, password, dockerURL)
        // Run docker pull from the remote repository twice. First loop will use dockerhub, second - Artifactory cache
        2.times {
            for(imageName in images) {
                Utils.dockerPullImage("${dockerURL}" + "/docker-remote/" + imageName)
            }
        }
        splunk.dockerCleanupCache(artifactoryBaseURL, username, password,
                "artifactory/docker-remote-cache/library/", images)
        Thread.sleep(30000)
        def startTime = "earliest_time=-6h"
        def search_string = 'search=search log_source="jfrog.rt.artifactory.access" action_response="ACCEPTED DOWNLOAD" ' +
                '"list\\.manifest" ' + startTime + ' | stats count as aCount | ' +
                'appendcols [search log_source="jfrog.rt.artifactory.access" action_response="ACCEPTED DEPLOY" ' +
                '"list\\.manifest" "*-cache" '+ startTime +' | stats count as bCount ] | eval pct=bCount/aCount | ' +
                'eval inversePct=1-pct | fields - aCount,bCount,pct'
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        int size = response.then().extract().body().path("results.size()")
        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            def inversePct = response.then().extract().body().path("results[0].inversePct") as Double
            Assert.assertTrue(inversePct > 0)
            Reporter.log("Cache Hit Ratio - " + inversePct, true)
        }

        Reporter.log("- Splunk. Docker Repositories Cache Hit Ratio verified", true)
    }

    @Test(priority=7, groups=["splunk", "splunk_rt_docker"], testName = "Artifactory - Docker. Dockerhub Pull Requests in rolling 6 Hr window")
    void dockerhubPRsIn6hrWindowTest() throws Exception {
        def images = ["traefik", "alpine", "hello-world", "busybox"]
        Utils.dockerLogin(username, password, dockerURL)
        for(imageName in images) {
            Utils.dockerPullImage("${dockerURL}" + "/docker-remote/" + imageName)
        }
        splunk.dockerCleanupCache(artifactoryBaseURL, username, password,
                "artifactory/docker-remote-cache/library/", images)
        def startTime = "earliest_time=-6h"
        def search_string = 'search=search "downloading" log_source="jfrog.rt.artifactory.service" ' +
                '"manifests/" "docker.io" ' + startTime + ' | spath message | search message !="*/manifests/sha256:*" | ' +
                'timechart span=1h count(message) as Count | streamstats sum(Count) as Count window=6 | ' +
                'eval warning = 100 | eval critical = 200'
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        int size = response.then().extract().body().path("results.size()")
        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            def numberOfPulls = response.then().extract().body().path("results["+ (size-1) +"].Count") as Integer
            Assert.assertTrue(numberOfPulls >= images.size())
            Reporter.log("Number of pulls - " + numberOfPulls, true)
        }

        Reporter.log("- Splunk. Dockerhub Pull Requests in rolling 6 Hr window verified", true)
    }

    @Test(priority=8, groups=["splunk", "splunk_rt_docker"], testName = "Artifactory - Docker. Dockerhub Pull Requests Total")
    void dockerhubPRsTotalTest() throws Exception {
        def images = ["traefik", "alpine", "hello-world", "busybox"]
        Utils.dockerLogin(username, password, dockerURL)
        for(imageName in images) {
            Utils.dockerPullImage("${dockerURL}" + "/docker-remote/" + imageName)
        }
        splunk.dockerCleanupCache(artifactoryBaseURL, username, password,
                "artifactory/docker-remote-cache/library/", images)
        def search_string = 'search=search "downloading" log_source="jfrog.rt.artifactory.service" ' +
                '"manifests/" "docker.io" | spath message | search message !="*/manifests/sha256:*" | ' +
                'timechart count(message) as Count'
        Thread.sleep(30000)
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        int size = response.then().extract().body().path("results.size()")
        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            JsonPath jsonPathEvaluator = response.jsonPath()
            List<Integer> pullsCount = jsonPathEvaluator.getList("results.Count", Integer.class)
            Assert.assertTrue((pullsCount.sum()) >= images.size())
        }

        Reporter.log("- Splunk. Dockerhub Pull Requests Total verified", true)
    }

    @Test(priority=9, groups=["splunk", "splunk_rt_docker"], testName = "Artifactory - Docker. Top 10 Users By Docker Pulls")
    void dockerhubTop10UsersTest() throws Exception {
        def images = ["traefik", "alpine", "hello-world", "busybox"]
        Utils.dockerLogin(username, password, dockerURL)
        for(imageName in images) {
            Utils.dockerPullImage("${dockerURL}" + "/docker-remote/" + imageName)
        }
        splunk.dockerCleanupCache(artifactoryBaseURL, username, password,
                "artifactory/docker-remote-cache/library/", images)
        def search_string = 'search=search log_source="jfrog.rt.artifactory.access" "list\\.manifest" "ACCEPTED DEPLOY" ' +
                '| top limit=10 username'
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        int size = response.then().extract().body().path("results.size()")
        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            response.then().
                    body("results.username", Matchers.notNullValue()).
                    body("results.count", Matchers.notNullValue()).
                    body("results.percent", Matchers.notNullValue())
        }

        Reporter.log("- Splunk. Top 10 Users By Docker Pulls verified", true)
    }

    @Test(priority=10, groups=["splunk", "splunk_rt_docker"], testName = "Artifactory - Docker. Top 10 IPs By Docker Pulls")
    void dockerhubTop10IPsTest() throws Exception {
        def images = ["traefik", "alpine", "hello-world", "busybox"]
        Utils.dockerLogin(username, password, dockerURL)
        for(imageName in images) {
            Utils.dockerPullImage("${dockerURL}" + "/docker-remote/" + imageName)
        }
        splunk.dockerCleanupCache(artifactoryBaseURL, username, password,
                "artifactory/docker-remote-cache/library/", images)
        def search_string = 'search=search log_source="jfrog.rt.artifactory.access" "list\\.manifest" "ACCEPTED DEPLOY"' +
                ' | top limit=10 ip'
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        int size = response.then().extract().body().path("results.size()")
        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            response.then().
                    body("results.count", Matchers.notNullValue()).
                    body("results.percent", Matchers.notNullValue())
            JsonPath jsonPathEvaluator = response.jsonPath()
            List<String> IPAddresses = jsonPathEvaluator.getList("results.ip")
            for(ip in IPAddresses){
                Assert.assertTrue(splunk.validateIPAddress(ip.replaceAll("\\s","")))
            }
        }
        Reporter.log("- Splunk. Top 10 IPs By Docker Pulls verified", true)
    }

    @Test(priority=11, groups=["splunk", "splunk_rt_docker"], testName = "Artifactory - Docker. Accessed Docker Images")
    void accessedImagesTest() throws Exception {
        def image = "busybox"
        def numberOfImages = 3
        def repos = ["docker-dev-local", "docker-local"]
        // Docker login, pull busybox, generate and push multiple dummy images
        Utils.dockerLogin(username, password, dockerURL)
        Utils.dockerPullImage(image)
        Utils.dockerGenerateImages(repos, numberOfImages, image, dockerURL)
        Thread.sleep(60000)
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR ' +
                'log_source="jfrog.rt.artifactory.request") request_url="/api/docker/*" ' +
                'repo!="NULL" image!="NULL" repo!="" image!="" repo!="latest" earliest=-10m | ' +
                'timechart span=300 count by image'
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)

        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        JsonPath jsonPathEvaluator = response.jsonPath()
        for (int i = 1; i <= numberOfImages; i++) {
            List<Integer> result = jsonPathEvaluator.getList("results.${image}${i}", Integer.class)
            Assert.assertTrue((result.sum()) >= numberOfImages)
        }
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Utils.verifySplunkDate(date)

        Reporter.log("- Splunk. Accessed Docker Images information is verified", true)
    }

    @Test(priority=12, groups=["splunk", "splunk_rt_docker"], testName = "Artifactory - Docker. Accessed Docker Repos")
    void accessedReposTest() throws Exception {
        def repos = ["${dockerURL}/docker-dev-local/busybox1:1.1", "${dockerURL}/docker-local/busybox1:1.1"]
        Utils.dockerLogin(username, password, dockerURL)
        for(i in repos) {
            Utils.dockerPullImage(i)
        }
        Thread.sleep(30000)
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR ' +
                'log_source="jfrog.rt.artifactory.request") request_url="/api/docker/*" ' +
                'repo!="NULL" image!="NULL" repo!="" image!="" repo!="latest" earliest=-10m | ' +
                'timechart span=300 count by repo'
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)

        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        JsonPath jsonPathEvaluator = response.jsonPath()
        def repoNames = ["docker-dev-local", "docker-local"]
        for (i in repoNames) {
            List<Integer> result = jsonPathEvaluator.getList("results.${i}", Integer.class)
            Assert.assertTrue((result.sum()) > 0)
        }
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Utils.verifySplunkDate(date)

        Reporter.log("- Splunk. Accessed Docker Repos information is verified", true)
    }

    @Test(priority=13, groups=["splunk", "splunk_rt_docker"], testName = "Artifactory - Docker. Data Transfers (GBs) Uploads By Repo")
    void dataTransferUploadeTest() throws Exception {

        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR ' +
                'log_source="jfrog.rt.artifactory.request") request_url="/api/docker/*" ' +
                'repo!="NULL" image!="NULL" repo!="" image!="" repo!="latest" | ' +
                'eval gb=response_content_length/1073741824 | stats sum(gb) as GB by repo | where GB > 0'
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)

        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        List<String> repoNames = ["docker-dev-local", "docker-local"]
        for(repo in repoNames) {
            response.then().
                    body("results.repo", hasItems(repo)).
                    body("results.GB", Matchers.notNullValue())
        }
        Reporter.log("- Splunk. Data Transfers (GBs) Uploads By Repo information is verified", true)
    }

    @Test(priority=14, groups=["splunk", "splunk_rt_docker"], testName = "Artifactory - Docker. Data Transfers (GBs) Downloads By Repo")
    void dataTransferDownloadsTest() throws Exception {

        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.request" OR ' +
                'log_source="jfrog.rt.artifactory.request") request_url="/api/docker/*" ' +
                'repo!="NULL" image!="NULL" repo!="" image!="" repo!="latest" | ' +
                'eval gb=request_content_length/1073741824 | stats sum(gb) by repo'
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)

        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        List<String> repoNames = ["docker-dev-local", "docker-local"]
        for(repo in repoNames) {
            response.then().
                    body("results.repo", hasItems(repo))
        }

        Reporter.log("- Splunk. Data Transfers (GBs) Downloads By Repo information is verified", true)
    }

    @Test(priority=15, groups=["splunk"], testName = "Artifactory, Application. Log Volume")
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
        def search_string = 'search=search log_source!="NULL" log_source!="jfrog.xray.*" | timechart count by log_source'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)

        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)

        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == Utils.getUTCdate())

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> accessCounts = jsonPathEvaluator.getList("results.'jfrog.rt.access.request'", Integer.class)
        List<Integer> artifactoryCounts = jsonPathEvaluator.getList("results.'jfrog.rt.artifactory.request'", Integer.class)
        List<Integer> routerCounts = jsonPathEvaluator.getList("results.'jfrog.rt.router.request'", Integer.class)
        Assert.assertTrue((accessCounts.sum()) >= calls)
        Assert.assertTrue((artifactoryCounts.sum()) >= calls)
        Assert.assertTrue((routerCounts.sum()) >= calls)

        Reporter.log("- Splunk. Artifactory, Log volume verification. Each log record has values", true)
    }

    @Test(priority=16, groups=["splunk"], testName = "Artifcatory, Application. Log Errors")
    void rtLogErrorsTest() throws Exception {
        int count = 1
        int calls = 5
        // Generate Artifactory calls
        http403(count, calls)
        http404(count, calls)
        http500(count, calls)
        Thread.sleep(40000)
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.service" OR ' +
                'log_source="jfrog.rt.artifactory.service") log_level="ERROR" | timechart count by log_level'

        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.ERROR", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= calls)
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == Utils.getUTCdate())

        Reporter.log("- Splunk. Artifactory, Log Errors verification. Splunk shows errors generated by Artifactory" +
                " during the test", true)
    }

    @Test(priority=17, groups=["splunk"], dataProvider = "users", testName = "Artifcatory, Audit. Generate data with data provider")
    void generateDataTest(usernameRt, emailRt, passwordRt, incorrectPasswordRt) {
        // Deploy as non-existent users, 401
        deployArtifactAs(usernameRt, passwordRt, 403)
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

    @Test(priority=18, groups=["splunk"], testName = "Artifcatory, Audit. Audit Actions by Users")
    void rtAuditByUsersTest() throws Exception {
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.access.audit" OR ' +
                'log_source="jfrog.rt.access.audit") user!="UNKNOWN" | stats count by user'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        response.then().assertThat().statusCode(200)
                .body("results[0].user", equalTo(username))
        JsonPath jsonPathEvaluator = response.jsonPath()
        def count = jsonPathEvaluator.getInt("results[0].count")
        Assert.assertTrue((count >= 1))

        Reporter.log("- Splunk. Artifactory, Audit Actions by Users verification.", true)
    }

    @Test(priority=19, groups=["splunk"], testName = "Artifcatory, Audit. Denied Actions by Username")
    void rtDeniedActionByUsersTest() throws Exception {
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.access" OR ' +
                'log_source="jfrog.rt.artifactory.access") action_response="DENIED*" username!="NA " | ' +
                'stats  count by username'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.count", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= 1)
        List<String> usernames = ["testuser0 ", "testuser1 ", "testuser2 "]
        for(user in usernames) {
            response.then().
                    body("results.username", hasItems(user))
        }

        Reporter.log("- Splunk. Artifactory, Denied Actions by Username verification.", true)
    }

    @Test(priority=20, groups=["splunk"], testName = "Artifcatory, Audit. Denied Logins By username and IP")
    void rtDeniedActionByUserIPTest() throws Exception {
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.access" OR ' +
                'log_source="jfrog.rt.artifactory.access")  action_response="DENIED LOGIN" username!="NA " | ' +
                'stats count by ip,username'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.count", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= 1)

        List<String> IPAddresses = jsonPathEvaluator.getList("results.ip")
        for(ip in IPAddresses){
            Assert.assertTrue(splunk.validateIPAddress(ip.replaceAll("\\s","")))
        }
        List<String> usernames = ["testuser0 ", "testuser1 ", "testuser2 "]
        for(user in usernames) {
            response.then().
                    body("results.username", hasItems(user))
        }
        Reporter.log("- Splunk. Artifactory, Denied Actions by Username verification.", true)
    }

    @Test(priority=21, groups=["splunk"], testName = "Artifcatory, Audit. Denied Logins by IP")
    void rtDeniedLoginsByIPTest() throws Exception {
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.access" OR ' +
                'log_source="jfrog.rt.artifactory.access") action_response="DENIED LOGIN" | stats count by ip'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.count", Integer.class)
        List<String> IPAddresses = jsonPathEvaluator.getList("results.ip")
        for(ip in IPAddresses){
            Assert.assertTrue(splunk.validateIPAddress(ip.replaceAll("\\s","")))
        }
        Assert.assertTrue((errorCount.sum()) >= 1)

        Reporter.log("- Splunk. Artifactory, Denied Logins by IP verification.", true)
    }

    @Test(priority=22, groups=["splunk"], testName = "Artifcatory, Audit. Denied Actions by IP")
    void rtDeniedActionsByIPTest() throws Exception {
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.access" OR ' +
                'log_source="jfrog.rt.artifactory.access") action_response="DENIED*" | stats count by ip'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.count", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= 1)
        List<String> IPAddresses = jsonPathEvaluator.getList("results.ip")
        for(ip in IPAddresses){
            Assert.assertTrue(splunk.validateIPAddress(ip.replaceAll("\\s","")))
        }

        Reporter.log("- Splunk. Artifactory, Denied Actions by Username verification.", true)
    }

    @Test(priority=23, groups=["splunk"], testName = "Artifcatory, Audit. Accepted Deploys by Username")
    void rtAcceptedDeploysByUsernameTest() throws Exception {
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search (sourcetype="jfrog.rt.artifactory.access" OR ' +
                'log_source="jfrog.rt.artifactory.access") action_response="ACCEPTED DEPLOY" | stats count by username'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)

        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.count", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= 1)
        List<String> usernames = ["testuser0 ", "testuser1 ", "testuser2 "]
        for(user in usernames) {
            response.then().
                    body("results.username", hasItems(user))
        }

        Reporter.log("- Splunk. Artifactory, Accepted Deploys by Username verification.", true)
    }

    @Test(priority=24, groups=["splunk_xray"], testName = "Xray. Log Volume")
    void xrayLogVolumeTest() throws Exception {
        int count = 1
        int calls = 5
        // Generate xray calls
        xray200(count, calls)
        xray201(count, calls)
        // Create a search job in Splunk with given parameters, return Search ID
        def search_string = 'search=search sourcetype!="NULL" log_source="jfrog.xray.*" | timechart count by log_source'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)

        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
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
        Assert.assertTrue((date.substring(0,10)) == Utils.getUTCdate())

        Reporter.log("- Splunk. Xray, Log volume verification. Each log record has values", true)
    }


    @Test(priority=25, groups=["splunk_xray"], testName = "Xray. Log Errors")
    void logErrorsTest() throws Exception {
        int count = 1
        int calls = 5
        // Generate xray calls
        xray200(count, calls)
        xray500(count, calls)
        Thread.sleep(20000)
        // 'earliest=' and 'span=' added to the original query to optimize the output
        def search_string = 'search=search (sourcetype="jfrog.xray.*.service" OR ' +
                'log_source="jfrog.xray.*.service") log_level="ERROR" earliest=-10m | ' +
                'timechart span=300 count by log_level'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)

        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.ERROR", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= calls)
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == Utils.getUTCdate())

        Reporter.log("- Splunk. Xray, Log Errors verification. Splunk shows errors generated by Xray" +
                " during the test", true)
    }

    @Test(priority=26, groups=["splunk_xray"], testName = "Xray. HTTP 500 Errors")
    void error500Test() throws Exception {
        int count = 1
        int calls = 5
        // Generate xray calls
        xray500(count, calls)
        Thread.sleep(30000)
        // Create a search job in Splunk with given parameters, return Search ID
        // 'earliest=' and 'span=' added to the original query to optimize the output
        def search_string = 'search=search (sourcetype="jfrog.xray.xray.request" OR ' +
                'log_source="jfrog.xray.xray.request") return_status="5*" earliest=-10m | ' +
                'timechart span=300 count by return_status'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)

        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == Utils.getUTCdate())
        JsonPath jsonPathEvaluator = response.jsonPath()
        List<Integer> errorCount = jsonPathEvaluator.getList("results.500", Integer.class)
        Assert.assertTrue((errorCount.sum()) >= calls)

        Reporter.log("- Splunk. Xray, HTTP 500 Errors verification. Splunk shows errors generated by Xray" +
                " during the test", true)
    }

    @Test(priority=27, groups=["splunk_xray"], testName = "Xray. HTTP Response Codes")
    void httpResponsesTest() throws Exception {
        int count = 1
        int calls = 5
        // Generate xray calls (on a clean instance initial run of xray409() will return 201, because record doesn't exist)
        xray200(count, calls)
        xray201(count, calls)
        xray409(count, calls+1)
        xray500(count, calls)
        Thread.sleep(35000)
        // Create a search job in Splunk with given parameters, return Search ID
        // 'earliest=' and 'span=' added to the original query to optimize the output
        def search_string = 'search=search (sourcetype="jfrog.xray.xray.request" OR ' +
                'log_source="jfrog.xray.xray.request") earliest=-10m | timechart span=300 count by return_status'
        Response createSearch = splunk.createSearch(splunk_username, splunk_password, splunkBaseURL, search_string)
        createSearch.then().statusCode(201)
        def searchID = splunk.getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)

        splunk.waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        Response response = splunk.getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
        int size = response.then().extract().body().path("results.size()")
        String date = response.then().extract().body().path("results[${size-1}]._time")
        Assert.assertTrue((date.substring(0,10)) == Utils.getUTCdate())

        JsonPath jsonPathEvaluator = response.jsonPath()
        def responseCodes = ["200","201","409","500"]
        for (responseCode in responseCodes) {
            List<Integer> respCount = jsonPathEvaluator.getList("results.${responseCode}", Integer.class)
            Assert.assertTrue((respCount.sum()) >= calls)
        }

        Reporter.log("- Splunk. Xray, HTTP Response Codes verification. Splunk shows responses generated by Xray" +
                " during the test.", true)
    }

    @Test(priority = 28, groups = ["splunk_siem"], testName = "Xray, Violations. Watches")
    void watchesCountTest() throws Exception {
        def search_string = /search=search log_source="jfrog.xray.siem.vulnerabilities" earliest=$earliest | stats dc(signature) as watches/
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)

        int size = response.then().extract().body().path("results.size()")

        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            def requests = response.then().extract().body().path("results[${size - 1}].watches") as Integer
            println(requests)
            // One watch per license in multipleLicenseIssueEvents and one watch for all security violations
            Assert.assertEquals(requests, license_issues.size() + 1)
            Reporter.log("- Splunk. Xray, Violations, Watches verification. Splunk shows responses generated by Xray " +
                    "The number of watches returned by splunk are " + requests, true)
        }
    }

    @Test(priority = 29, groups = ["splunk_siem"], testName = "Xray, Violations. Vulnerabilities")
    void vulnerabilitiesCountTest() throws Exception {
        def vulnerability_count = SplunkSteps.getExpectedCounts(license_issues, security_issues).getOrDefault("security", 0)

        def search_string = /search=search log_source="jfrog.xray.siem.vulnerabilities" category="Security" earliest=$earliest |/ +
                /stats count as Vulnerabilities/
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)

        int size = response.then().extract().body().path("results.size()")

        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            def requests = response.then().extract().body().path("results[${size - 1}].Vulnerabilities") as Integer
            println(requests)
            Assert.assertEquals(requests, vulnerability_count)
            Reporter.log("- Splunk. Xray, Violations, Vulnerabilities verification. Splunk shows responses generated by Xray " +
                    "\" +The number of Vulnerabilities returned by splunk are " + requests, true)
        }
    }

    @Test(priority = 30, groups = ["splunk_siem"], testName = "Xray, Violations. License Issues")
    void licenseIssuesCountTest() throws Exception {
        def expected = SplunkSteps.getExpectedCounts(license_issues, security_issues)
        expected.remove("security")

        def search_string = /search=search log_source="jfrog.xray.siem.vulnerabilities" category="License" earliest=$earliest | stats count as licenses/
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)

        int size = response.then().extract().body().path("results.size()")

        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            def requests = response.then().extract().body().path("results[${size - 1}].licenses") as Integer
            println(requests)
            // One watch per license in multipleLicenseIssueEvents and one watch for all security violations
            Assert.assertEquals(requests, expected.values().sum())
            Reporter.log("- Splunk. Xray, Violations, License Issues verification. Splunk shows responses generated by Xray " +
                    "\" +The number of license issues returned by splunk are " + requests, true)
        }
    }

    @Test(priority = 31, groups = ["splunk_siem"], testName = "Xray, Violations. Violations")
    void violationsCountTest() throws Exception {

        def search_string = /search=search log_source="jfrog.xray.siem.vulnerabilities" earliest=$earliest | stats count as violations/
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        int size = response.then().extract().body().path("results.size()")

        // sum of all security and license issues
        def expected = SplunkSteps.getExpectedCounts(license_issues, security_issues).values().sum()

        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            def requests = response.then().extract().body().path("results[${size - 1}].violations") as Integer
            println(requests)
            Assert.assertEquals(requests, expected)
            Reporter.log("- Splunk. Xray, Violations, Violations verification. Splunk shows responses generated by Xray " +
                    "\" +The number of violations returned by splunk are " + requests, true)
        }
    }

    @Test(priority = 33, groups = ["splunk_siem"], testName = "Xray, Violations. Infected Components")
    void infectedComponentsCountTest() throws Exception {
        def infected_components_count =  xraySteps.artifacts().length

        def search_string = /search=search log_source="jfrog.xray.siem.vulnerabilities" earliest=$earliest | stats dc(infected_components{}) as infected_components/
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)

        int size = response.then().extract().body().path("results.size()")

        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            def requests = response.then().extract().body().path("results[${size - 1}].infected_components") as Integer
            println(requests)
            // One watch per license in multipleLicenseIssueEvents and one watch for all security violations
            Assert.assertTrue(requests == infected_components_count)
            Reporter.log("- Splunk. Xray, Violations, Infected Components verification. Splunk shows responses generated by Xray " +
                    "\" +The number of infected components returned by splunk are " + requests, true)
        }
    }

    @Test(priority = 34, groups = ["splunk_siem"], testName = "Xray, Violations. Impacted Artifacts")
    void impactedArtifactsCountTest() throws Exception {
        def impacted_artifacts_count = xraySteps.artifacts().length

        def search_string = /search=search log_source="jfrog.xray.siem.vulnerabilities" earliest=$earliest | stats dc(impacted_artifacts{}) as impacted_artifacts/
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        int size = response.then().extract().body().path("results.size()")

        if (size == 0){
            Assert.fail("Empty response from Splunk")
        } else {
            def requests = response.then().extract().body().path("results[${size - 1}].impacted_artifacts") as Integer
            println(requests)
            // One watch per license in multipleLicenseIssueEvents and one watch for all security violations
            Assert.assertEquals(requests, impacted_artifacts_count)
            Reporter.log("- Splunk. Xray, Violations, Impacted Artifacts verification. Splunk shows responses generated by Xray " +
                    "\" +The number of impacted artifacts returned by splunk are " + requests, true)
        }
    }

    @Test(priority = 35, groups = ["splunk_siem"], testName = "Xray, Violations. Violations per Watch")
    void violationsPerWatchTest() throws Exception {
        def search_string = /search=search log_source="jfrog.xray.siem.vulnerabilities" earliest=$earliest | stats count by signature/
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        def actual = SplunkSteps.getMatchedPolicyWatchCounts(response, "signature")

        def expected = SplunkSteps.getExpectedCounts(license_issues, security_issues)
        Assert.assertEquals(actual, expected)

        Reporter.log("- Splunk. Xray, Violations, Violations per Watch verification. " +
                "Splunk shows responses generated by Xray ", true)

    }

    @Test(priority = 36, groups = ["splunk_siem"], testName = "Xray, Violations. Violations Severity")
    void violationsSeverityTest() throws Exception {
        def search_string = /search=search log_source="jfrog.xray.siem.vulnerabilities" severity!=Unknown earliest=$earliest | stats count by severity/
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        def severities = SplunkSteps.getSeverities(response)
        def expected = SplunkSteps.getExpectedSeverities(license_issues, security_issues)

        Assert.assertEquals(severities, expected)

        Reporter.log("- Splunk. Xray, Violations, Violations Severity verification. " +
                "Splunk shows responses generated by Xray ", true)
    }

    @Test(priority = 37, groups = ["splunk_siem"], testName = "Xray, Violations. Violations by Policy")
    void violationsByPolicyTest() {
        def search_string = /search=search log_source="jfrog.xray.siem.vulnerabilities" earliest=$earliest | stats count by matched_policies{}.policy/
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        def actual = SplunkSteps.getMatchedPolicyWatchCounts(response, "matched_policies{}.policy")

        def expected = SplunkSteps.getExpectedCounts(license_issues, security_issues)
        Assert.assertEquals(actual, expected)
        Reporter.log("- Splunk. Xray, Violations, Violations by Rule. " +
                "Splunk shows responses generated by Xray ", true)
    }

    @Test(priority = 38, groups = ["splunk_siem"], testName = "Xray, Violations. Violations by Rule")
    void violationsByRuleTest() {
        def search_string = /search=search log_source="jfrog.xray.siem.vulnerabilities" earliest=$earliest | stats count by matched_policies{}.rule/
        Response response = splunk.splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string)
        def actual = SplunkSteps.getMatchedRuleCounts(response)

        def expected = SplunkSteps.getExpectedCounts(license_issues, security_issues)
        Assert.assertEquals(actual, expected)
        Reporter.log("- Splunk. Xray, Violations, Violations by Rule. " +
                "Splunk shows responses generated by Xray ", true)
    }

}