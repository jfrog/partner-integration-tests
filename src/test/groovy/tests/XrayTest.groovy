package tests

import io.restassured.RestAssured
import io.restassured.response.Response
import org.awaitility.Awaitility
import org.testng.Assert
import org.testng.Reporter
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test
import steps.RepositorySteps
import steps.XraySteps
import utils.Utils

import java.time.LocalDate
import java.util.concurrent.TimeUnit

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.notNullValue


class XrayTest extends XraySteps{
    def artifact = new File("./src/test/resources/repositories/artifact_go_vuln.zip")
    def repoSteps = new RepositorySteps()
    def xrayBaseUrl
    def artifactoryURL
    def randomIndex
    def policyName
    def watchName

    @BeforeSuite(groups=["xray"])
    def setUp() {
        xrayBaseUrl = "${artifactoryBaseURL}/xray/api"
        artifactoryURL = "${artifactoryBaseURL}/artifactory"
        RestAssured.authentication = RestAssured.basic(username, password)
        RestAssured.useRelaxedHTTPSValidation()
        Random random = new Random()
        randomIndex = random.nextInt(10000000)
        policyName = "security_policy_${randomIndex}"
        watchName = "all-repositories_${randomIndex}"
    }

    @Test(priority=1, groups=["xray"], dataProvider = "issueEvents", testName = "Create Issue Event")
    void createIssueEventTest(issueID, cve, summary, description){
        Response create = createIssueEvent(issueID+randomIndex, cve, summary, description, username, password, xrayBaseUrl)
        create.then().log().ifValidationFails().statusCode(201)
        Response get = getIssueEvent(issueID+randomIndex, username, password, xrayBaseUrl)
        get.then().log().ifValidationFails().statusCode(200)
        def issueIDverification = get.then().extract().path("id")
        // def cveVerification = get.then().extract().path("source_id")
        def summaryVerification = get.then().extract().path("summary")
        def descriptionVerification = get.then().extract().path("description")
        Assert.assertTrue(issueID+randomIndex == issueIDverification)
        // Assert.assertTrue(cve == cveVerification)
        Assert.assertTrue(summary == summaryVerification)
        Assert.assertTrue(description == descriptionVerification)

        Reporter.log("- Create issue event. Issue event with ID ${issueID+randomIndex} created and verified successfully", true)
    }
// Temporary removed due to bug in Xray 3.52.4 - error 405 on update.
//    @Test(priority=2, groups=["xray"], dataProvider = "issueEvents", testName = "Update Issue Event",
//            dependsOnMethods = "createIssueEventTest")
//    void updateIssueEventTest(issueID, cve, summary, description){
//        cve = "CVE-2017-0000000"
//        summary = "Updated"
//        description = "Updated"
//        Response update = updateIssueEvent(issueID+randomIndex, cve, summary, description, username, password, xrayBaseUrl)
//        update.then().log().ifValidationFails().statusCode(200)
//        Response get = getIssueEvent(issueID+randomIndex, username, password, xrayBaseUrl)
//        get.then().log().ifValidationFails().statusCode(200)
//        def summaryVerification = get.then().extract().path("summary")
//        def descriptionVerification = get.then().extract().path("description")
//        Assert.assertTrue(summary == summaryVerification)
//        Assert.assertTrue(description == descriptionVerification)
//
//        Reporter.log("- Update issue event. Issue event with ID ${issueID+randomIndex} updated and verified successfully", true)
//    }

    // Policies, watch
    @Test(priority=3, groups=["xray"], testName = "Create policy")
    void createPolicyTest(){
        Response create = createPolicy(policyName, username, password, xrayBaseUrl)
        create.then().log().ifValidationFails().statusCode(201)

        Response get = getPolicy(policyName, username, password, xrayBaseUrl)
        get.then().log().ifValidationFails().statusCode(200)
        def policyNameVerification = get.then().extract().path("name")
        Assert.assertTrue(policyName == policyNameVerification)

        Reporter.log("- Create policy. Policy with name ${policyName} created and verified successfully", true)
    }

    @Test(priority=4, groups=["xray"], testName = "Update policy", dependsOnMethods = "createPolicyTest")
    void updatePolicyTest(){
        def description = "Updated description"
        Response update = updatePolicy(policyName, description, username, password, xrayBaseUrl)
        update.then().log().ifValidationFails().statusCode(200)

        Response get = getPolicy(policyName, username, password, xrayBaseUrl)
        get.then().log().ifValidationFails().statusCode(200)
        def descriptionVerification = get.then().extract().path("description")
        Assert.assertTrue(description == descriptionVerification)

        Reporter.log("- Update policy. Policy with name ${policyName} updated and verified successfully", true)
    }

    @Test(priority=5, groups=["xray"], testName = "Get policies")
    void getPoliciesTest(){
        Response response = getPolicies(username, password, xrayBaseUrl)
        response.then().log().ifValidationFails().statusCode(200)
                .body("name", notNullValue())
                .body("type", notNullValue())
                .body("description", notNullValue())
                .body("author", notNullValue())
                .body("rules", notNullValue())
                .body("created", notNullValue())
                .body("modified", notNullValue())
        def policies = response.then().extract().path("name")
        Reporter.log("- Get policies. Policies list is returned successfully. " +
                "Policies returned: ${policies}", true)
    }

    @Test(priority=6, groups=["xray"], testName = "Create watch for the repositories", dependsOnMethods = "createPolicyTest")
    void createWatchTest(){
        Response create = createWatchEvent(watchName, policyName, username, password, xrayBaseUrl)
        create.then().log().ifValidationFails().statusCode(201)
                .body("info",
                equalTo("Watch has been successfully created"))

        Response get = getWatchEvent(watchName, username, password, xrayBaseUrl)
        get.then().log().ifValidationFails().statusCode(200)
                .body("general_data.name", equalTo((watchName).toString()))

        Reporter.log("- Create watch. Watch with name ${watchName} has been created and verified successfully", true)
    }

    @Test(priority=7, groups=["xray"], testName = "Update watch for the repositories", dependsOnMethods = "createWatchTest")
    void updateWatchTest(){
        def description = "Updated watch"
        Response create = updateWatchEvent(watchName, description, policyName, username, password, xrayBaseUrl)
        create.then().log().ifValidationFails().statusCode(200)
                .body("info",
                        equalTo("Watch was successfully updated"))

        Response get = getWatchEvent(watchName, username, password, xrayBaseUrl)
        get.then().log().ifValidationFails().statusCode(200)
                .body("general_data.description", equalTo(description))

        Reporter.log("- Update watch. Watch with name ${watchName} has been updated and verified successfully", true)
    }

    @Test(priority=8, groups=["xray"], testName = "Assign policy to watches")
    void assignPolicyToWatchTest(){
        Response response = assignPolicy(watchName, policyName, username, password, xrayBaseUrl)
        response.then().log().ifValidationFails().statusCode(200)
                .body("result.${watchName}",
                        equalTo("Policy assigned successfully to Watch"))

        Reporter.log("- Assign policy to watch. Policy assigned successfully to Watch", true)
    }

    @Test(priority=9, groups=["xray"], testName = "Delete watch")
    void deleteWatchTest(){
        Response response = deleteWatchEvent(watchName, username, password, xrayBaseUrl)
        response.then().log().ifValidationFails().statusCode(200)
                .body("info",
                        equalTo("Watch was deleted successfully"))

        Reporter.log("- Delete watch. Watch ${watchName} has been successfully deleted", true)
    }

    @Test(priority=10, groups=["xray"], testName = "Delete policy")
    void deletePolicyTest(){

        Response response = deletePolicy(policyName, username, password, xrayBaseUrl)
        response.then().statusCode(200)
                .body("info",
                        equalTo(("Policy ${policyName} was deleted successfully").toString()))

        Reporter.log("- Delete policy. Policy ${policyName} has been successfully deleted", true)
    }

    @Test(priority=11, groups=["xray"], testName = "Force reindex repo")
    void forceReindexTest(){
        // Make sure artifact exists
        def repoName = "generic-dev-local"
        def directoryName = "test-directory"
        def filename = "artifact_go_vuln.zip"
        def sha256 = Utils.generateSHA256(artifact)
        def sha1 = Utils.generateSHA1(artifact)
        def md5 = Utils.generateMD5(artifact)
        Response response = repoSteps.deployArtifact(artifactoryURL, username, password, repoName, directoryName, artifact, filename, sha256, sha1, md5)
        response.then().assertThat().log().ifValidationFails().statusCode(201)
        // Force index the artifact
        Response reindex = forceReindex(username, password, xrayBaseUrl, repoName, directoryName, filename)
        reindex.then().log().ifValidationFails().statusCode(200)

        Reporter.log("- Force reindex repo. Artifact was sent to reindex", true)
    }

    // Commented out due to inconsistency on the new instances, scan starts async
    /*
    @Test(priority=12, timeOut = 300000, groups=["xray"], testName = "Start scan")
    void startScanTest() throws Exception{
        // Make sure artifact exists
        def repoName = "generic-dev-local"
        def directoryName = "test-directory"
        def filename = "artifact_go_vuln22.zip"
        def sha256 = Utils.generateSHA256(artifact)
        def sha1 = Utils.generateSHA1(artifact)
        def md5 = Utils.generateMD5(artifact)
        Response response = repoSteps.deployArtifact(artifactoryURL, username, password, repoName, directoryName, artifact, filename, sha256, sha1, md5)
        response.then().assertThat().log().ifValidationFails().statusCode(201)
        def artifactPath = "default/${repoName}/${directoryName}/${filename}"
        def componentID = artifactSummary(username, password, artifactPath, xrayBaseUrl)
                .then().extract().path("artifacts[0].licenses[0].components[0]")
        Awaitility.await().atMost(600, TimeUnit.SECONDS).pollInSameThread()
                .pollDelay(60, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.SECONDS).untilAsserted(() ->
                startScan(username, password, componentID, xrayBaseUrl).then().log().everything()
                        .body("info",
                                equalTo(("Scan of artifact is in progress").toString())))

        Reporter.log("- Start scan. Scan of ${componentID} has been started successfully", true)
    }
    */

    @Test(priority=13, groups=["xray"], testName = "Create and get integration configuration")
    void integrationConfigurationTest(){
        def vendorName = "vendor_${randomIndex}"
        Response post = addtIntegrationConfiguration(username, password, vendorName, xrayBaseUrl)
        post.then().statusCode(200)

        Response get = getIntegrationConfiguration(username, password, xrayBaseUrl)
        int bodySize = get.body().jsonPath().getList(".").size()
        get.then().log().ifValidationFails().statusCode(200)
                .body("[" + (bodySize-1) + "].vendor", equalTo(vendorName.toString()))

        Reporter.log("- Integration configuration. " +
                "Configuration for vendor ${vendorName} has been successfully added and verified", true)
    }

    @Test(priority=14, groups=["xray"], testName = "Get system parameters")
    void getSystemPropertiesTest(){
        Response get = getSystemParameters(username, password, xrayBaseUrl)
        get.then().log().ifValidationFails().statusCode(200)

        Reporter.log("- Get system parameters. Verified", true)
    }

    @Test(priority=15, groups=["xray"], testName = "Get binary manager")
    void getBinaryManagerTest(){
        Response response = getBinaryManager(username, password, xrayBaseUrl)
        response.then().log().ifValidationFails().statusCode(200)
                .body("binMgrId", equalTo("default"))
                .body("license_valid", equalTo(true))
                .body("binMgrId", equalTo("default"))
        def version = response.then().extract().path("version")

        Reporter.log("- Get binary manager. Binary manager is verified, connected RT version: ${version}", true)
    }

    @Test(priority=16, groups=["xray"], testName = "Get repo indexing configuration")
    void getIndexingConfigurationTest(){
        Response response = getIndexingConfiguration(username, password, xrayBaseUrl)
        response.then().log().ifValidationFails().statusCode(200)
                .body("bin_mgr_id", equalTo("default"))
                .body("indexed_repos.name", hasItem("generic-dev-local"))

        Reporter.log("- Get repo indexing configuration.", true)
    }

    @Test(priority=17, groups=["xray"], testName = "Update repo indexing configuration")
    void updateIndexingConfigurationTest(){
        Response response = updateIndexingConfiguration(username, password, xrayBaseUrl)
        response.then().log().ifValidationFails().statusCode(200)
                .body("info", equalTo("Repositories list has been successfully sent to Artifactory"))

        Reporter.log("- Update repo indexing configuration. Successfully updated", true)
    }

    @Test(priority=18, groups=["xray"], testName = "Get artifact summary")
    void artifactSummaryTest(){
        // Make sure artifact exists
        def repoName = "generic-dev-local"
        def directoryName = "test-directory"
        def filename = "artifact_go_vuln11.zip"
        def sha256 = Utils.generateSHA256(artifact)
        def sha1 = Utils.generateSHA1(artifact)
        def md5 = Utils.generateMD5(artifact)
        Response response = repoSteps.deployArtifact(artifactoryURL, username, password, repoName, directoryName, artifact, filename, sha256, sha1, md5)
        response.then().assertThat().log().ifValidationFails().statusCode(201)
        // Artifact should be indexed by Xray before retrieving the summary
        def artifactPath = "default/${repoName}/${directoryName}/${filename}"
        Awaitility.await().atMost(240, TimeUnit.SECONDS)
                .pollDelay(20, TimeUnit.SECONDS)
                .pollInterval(20, TimeUnit.SECONDS).untilAsserted(() ->
                         artifactSummary(username, password, artifactPath, xrayBaseUrl).then()
                        .body("artifacts[0].general.path", equalTo(artifactPath.toString())))
        artifactSummary(username, password, artifactPath, xrayBaseUrl).then().log().ifValidationFails()
            .statusCode(200)
            .body("artifacts[0].general.path", equalTo(artifactPath.toString()))

        Reporter.log("- Get artifact summary. Artifact summary has been returned successfully", true)
    }

    @Test(priority=19, groups=["xray"], testName = "Create support bundle")
    void createSupportBundleTest(){
        def name = "Support Bundle"
        LocalDate startDate = LocalDate.now().minusDays(5)
        LocalDate endDate = LocalDate.now()
        Response response = createSupportBundle(username, password, name, startDate, endDate, xrayBaseUrl)
        response.then().log().ifValidationFails().statusCode(200)
        def bundle_url = response.then().extract().path("artifactory.bundle_url")
        if ((bundle_url.toString()).contains(xrayBaseUrl.toString())) {
            Reporter.log("- Create support bundle. Successfully created using Xray API", true)
        } else if (((bundle_url.toString()).contains("localhost"))){
            Reporter.log("- Create support bundle. Created with a bug, localhost instead of the hostname", true)
        } else {
            response.then().log().everything()
            Assert.fail()
        }
    }

    @Test(priority=20, groups=["xray"], testName = "Get system monitoring status")
    void getSystemMonitoringTest(){
        Response response = getSystemMonitoringStatus(username, password, xrayBaseUrl)
        response.then().log().ifValidationFails().statusCode(200)

        Reporter.log("- Get system monitoring status. Data returned successfully", true)
    }

    @Test(priority=21, groups=["xray"], testName = "Xray ping request")
    void xrayPingRequestTest(){
        Response response = xrayPingRequest(username, password, xrayBaseUrl)
        response.then().log().ifValidationFails().statusCode(200)
                .body("status", equalTo("pong"))

        Reporter.log("- Get system monitoring status. Data returned successfully", true)
    }

    @Test(priority=22, groups=["xray"], testName = "Xray version")
    void xrayGetVersionTest(){
        Response response = xrayGetVersion(username, password, xrayBaseUrl)
        response.then().log().ifValidationFails().statusCode(200)
                .body("xray_version", notNullValue())
                .body("xray_revision", notNullValue())
        def version = response.then().extract().path("xray_version")
        def revision = response.then().extract().path("xray_revision")

        Reporter.log("- Get Xray version. Version: ${version}, revision: ${revision}", true)
    }

    @Test(priority=23, groups=["xray"], testName = "Verify Xray metrics")
    void verifyXrayMetricsTest(){
        Response metrics = getXrayMetrics(username, password, xrayBaseUrl)
        metrics.then().log().ifValidationFails().statusCode(200)
        Assert.assertTrue(metrics.asString().contains("app_disk_used_bytes"))

        Reporter.log("- Verify Xray metrics. Metrics returned successfully", true)
    }



}




