package steps

import io.restassured.response.Response
import org.yaml.snakeyaml.Yaml

import static io.restassured.RestAssured.given

class SplunkSteps {

    def repoSteps = new RepositorySteps()
    def securitySteps = new SecuritytSteps()
    def xraySteps = new XraySteps()
    def artifact = new File("./src/test/resources/repositories/artifact.zip")
    def repoListHA = new File("./src/test/resources/repositories/CreateDefault.yaml")
    Yaml yaml = new Yaml()
    def configFile = new File("./src/test/resources/testenv.yaml")
    def config = yaml.load(configFile.text)
    def url = "http://${config.artifactory.external_ip}/xray/api"
    def distribution = config.artifactory.distribution
    def username = config.artifactory.rt_username
    def password = config.artifactory.rt_password

    // Splunk API documentation
    // https://docs.splunk.com/Documentation/Splunk/8.0.5/RESTREF/RESTsearch#search.2Fjobs
    def createSearch(splunk_username, splunk_password, splunk_url, search_string) {
        return given()
                .auth()
                .preemptive()
                .basic("${splunk_username}", "${splunk_password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/x-www-form-urlencoded")
                .body(search_string)
                .when()
                .post(splunk_url + "/services/search/jobs")
                .then()
                .extract().response()
    }

    def getSearchResults(splunk_username, splunk_password, splunk_url, search_id) {
        return given()
                .auth()
                .preemptive()
                .basic("${splunk_username}", "${splunk_password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/x-www-form-urlencoded")
                .body("output_mode=json")
                .when()
                .get(splunk_url + "/services/search/jobs/" + search_id + "/results")
                .then()
                .extract().response()
    }


    def getSplunkSearchID(splunk_username, splunk_password, splunk_url, search_string){
        Response createSearch = createSearch(splunk_username, splunk_password, splunk_url, search_string)
        createSearch.then().statusCode(201)
        def searchID = createSearch.then().extract().path("sid")
        println "Search ID is " + searchID
        return searchID
    }


    // Generate HTTP responses to test Log Analytics

    def http200(count, calls){
        while (count <= calls) {
            Response http200 = repoSteps.getRepos()
            http200.then().statusCode(200)
            count++
        }

    }
    def http201(count, calls){
        while (count <= calls) {
            def usernameRt = "user${count}"
            def emailRt = "email+${count}@server.com"
            def passwordRt = "password"
            Response http201 = securitySteps.createUser(usernameRt, emailRt, passwordRt)
            http201.then().statusCode(201)
            count++
        }
    }

    def http204(count, calls){
        while (count <= calls) {
            def path = "generic-dev-local/test-directory/artifact.zip"
            def repoName = "generic-dev-local"
            def directoryName = "test-directory"
            def filename = "artifact.zip"
            def sha256 = "34444087e37a6d1b8e0d30690f7ded539ba8f52da95ec17bc9ef4091bd1668f1"
            def sha1 = "cbca9c7ae0f02b67b26a307f312fed1a23b3e407"
            def md5 = "be7560fcfb06dd942d28bf0c9c764728"
            def body = repoListHA
            repoSteps.createRepositories(body, username, password)
            repoSteps.deployArtifact(repoName, directoryName, artifact, filename, sha256, sha1, md5)

            Response http204 = repoSteps.deleteItem(path)
            http204.then().statusCode(204)
            count++
        }
    }

    def http403(count, calls){
        while (count <= calls) {
            def repoName = "generic-dev-local"
            Response http403 = repoSteps.deleteRepository(repoName, "user1", "password")
            http403.then().statusCode(403)
            count++
        }
    }

    def http404(count, calls){
        while (count <= calls) {
            def path = "generic-dev-local/test-directory/non-existing-artifact.zip"
            Response http404 = repoSteps.deleteItem(path)
            http404.then().statusCode(404)
            count++
        }
    }

    def http500(count, calls){
        while (count <= calls) {
            Response http500 = securitySteps.generateError500()
            http500.then().statusCode(500)
            count++
        }
    }

    def downloadArtifact(count, calls){
        def repoName = "generic-dev-local"
        def directoryName = "test-directory"
        def filename = "artifact.zip"
        while (count <= calls) {
            Response download = repoSteps.downloadArtifact(repoName, directoryName, filename)
            download.then().statusCode(200)
            count++
        }
    }

    def uploadIntoRepo(count, calls){
        def body = repoListHA
        def configFile = new File("./src/test/resources/testenv.yaml")
        Yaml yaml = new Yaml()
        def config = yaml.load(configFile.text)
        def username = config.artifactory.rt_username
        def password = config.artifactory.rt_password
        Response create = repoSteps.createRepositories(body, username, password)
        create.then().statusCode(200)
        def repoName = "generic-dev-local"
        def directoryName = "test-directory"
        def filename = "artifact.zip"
        def sha256 = "34444087e37a6d1b8e0d30690f7ded539ba8f52da95ec17bc9ef4091bd1668f1"
        def sha1 = "cbca9c7ae0f02b67b26a307f312fed1a23b3e407"
        def md5 = "be7560fcfb06dd942d28bf0c9c764728"
        while (count <= calls) {
            Response deploy = repoSteps.deployArtifact(repoName, directoryName, artifact, filename, sha256, sha1, md5)
            deploy.then().statusCode(201)
            count++
        }
    }

    def xray200(count, calls){
        while (count <= calls) {
            Response policies = xraySteps.getPolicies(username, password, url)
            policies.then().statusCode(200)

            count++
        }
    }

    def xray201(count, calls){
        Random random = new Random()
        while (count <= calls) {
            def policyName = "new-policy-(${random.nextInt(10000000)})"
            Response policy = xraySteps.createPolicy(policyName, username, password, url)
            policy.then().statusCode(201)
            count++
        }
    }

    def xray409(count, calls){
        while (count <= calls) {
            def policyName = "new-policy"
            xraySteps.createPolicy(policyName, username, password, url)
            count++
        }
    }

    def xray500(count, calls){
        while (count <= calls) {
            def policyName = "non-existing-policy"
            Response policy = xraySteps.getPolicy(policyName, username, password, url)
            policy.then().statusCode(500)
            count++
        }
    }


}
