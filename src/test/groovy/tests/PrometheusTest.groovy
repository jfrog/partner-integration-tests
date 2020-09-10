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
import steps.PrometheusSteps
import steps.RepositorySteps
import steps.SecuritytSteps
import steps.SplunkSteps
import utils.Utils

class PrometheusTest extends DataAnalyticsSteps{

    Yaml yaml = new Yaml()
    def configFile = new File("./src/test/resources/testenv.yaml")
    def config = yaml.load(configFile.text)
    def artifact = new File("./src/test/resources/repositories/artifact.zip")
    def repoSteps = new RepositorySteps()
    def securitySteps = new SecuritytSteps()
    def prometheus = new PrometheusSteps()
    def utils = new Utils()
    def artifactoryURL
    def dockerURL
    def distribution
    def username
    def password
    def splunk_username
    def splunk_password
    def prom_url


    @BeforeSuite(groups=["prometheus", "prometheus_xray"])
    def setUp() {
        artifactoryURL = config.artifactory.external_ip
        dockerURL = config.artifactory.url
        distribution = config.artifactory.distribution
        username = config.artifactory.rt_username
        password = config.artifactory.rt_password
        splunk_username = config.prometheus.username
        splunk_password = config.prometheus.password
        prom_url = "${config.prometheus.protocol}" + "${config.prometheus.url}" + ":" + "${config.prometheus.port}"
        RestAssured.baseURI = "http://${artifactoryURL}/artifactory"
        RestAssured.authentication = RestAssured.basic(username, password)
        RestAssured.useRelaxedHTTPSValidation()
    }


    @Test(priority=1, groups=["prometheus"], testName = "Artifactory. Upload Data Transfers")
    void uploadDataTest() throws Exception {

        int count = 1
        int calls = 15
        def query = "sum(jfrog_rt_data_upload)"
        long fileSizeInBytes = uploadIntoRepo(count, calls)

//        Response response = prometheus.postQuery(prom_url, query)
//        response.then().log().everything()

        println fileSizeInBytes

        Reporter.log("- Prometheus. Upload", true)

    }

    @Test(priority=2, groups=["prometheus"], testName = "Artifactory. Download Data Transfers")
    void downloadDataTest() throws Exception {

        int count = 1
        int calls = 5
        def query = "sum(jfrog_rt_data_download)"
        downloadArtifact(count, calls)

        Response response = prometheus.postQuery(prom_url, query)
        response.then().log().everything()

        JsonPath jsonPathEvaluator = response.jsonPath()
        def result = jsonPathEvaluator.get("data.result[0].value[1]")
        println result



        Reporter.log("- Prometheus. Download", true)

    }
}
