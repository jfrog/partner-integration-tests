package steps

import io.restassured.response.Response
import org.testng.annotations.DataProvider
import org.yaml.snakeyaml.Yaml
import utils.Utils

import static io.restassured.RestAssured.given
import static io.restassured.RestAssured.given

class DataAnalyticsSteps {

    def repoSteps = new RepositorySteps()
    def securitySteps = new SecuritytSteps()
    def xraySteps = new XraySteps()
    def utils = new Utils()
    def artifact = new File("./src/test/resources/repositories/artifact.zip")
    def repoListHA = new File("./src/test/resources/repositories/CreateDefault.yaml")
    Yaml yaml = new Yaml()
    def configFile = new File("./src/test/resources/testenv.yaml")
    def config = yaml.load(configFile.text)
    def url = "http://${config.artifactory.external_ip}/xray/api"
    def distribution = config.artifactory.distribution
    def username = config.artifactory.rt_username
    def password = config.artifactory.rt_password


    // Generate HTTP responses to test Log Analytics

    def http200(count, calls){
        while (count <= calls) {
            Response http200 = repoSteps.getRepos(username, password)
            http200.then().statusCode(200)
            count++
        }

    }
    def http201(count, calls){
        while (count <= calls) {
            def usernameRt = "user${count}"
            def emailRt = "email+${count}@server.com"
            def passwordRt = "password"
            Response http201 = securitySteps.createUser(username, password, usernameRt, emailRt, passwordRt)
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
            def sha256 = utils.generateSHA256(artifact)
            def sha1 = utils.generateSHA1(artifact)
            def md5 = utils.generateMD5(artifact)
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
            Response http500 = securitySteps.generateError500(username, password)
            http500.then().statusCode(500)
            count++
        }
    }

    def downloadArtifact(count, calls){
        def repoName = "generic-dev-local"
        def directoryName = "test-directory"
        def filename = "1_artifact.zip"
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
        def sha256 = utils.generateSHA256(artifact)
        def sha1 = utils.generateSHA1(artifact)
        def md5 = utils.generateMD5(artifact)

        for (int i = 1; i <= calls; i++) {
            def filename = "artifact.zip"
            filename = "${i}_${filename}"
            Response deploy = repoSteps.deployArtifact(repoName, directoryName, artifact, filename, sha256, sha1, md5)
            deploy.then().statusCode(201)
        }
        long fileSizeInBytes = artifact.length()
        return fileSizeInBytes
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


    def createUsers(usernameRt, emailRt, passwordRt){
        Response response = securitySteps.createUser(username, password, usernameRt, emailRt, passwordRt)
        response.then().statusCode(201)
    }

    def createRepos(){
        def body = repoListHA
        Response create = repoSteps.createRepositories(body, username, password)
        create.then().statusCode(200)
    }

    def getRepos(username, password){
        Response response = repoSteps.getReposWithUser(username, password)
        response.then().statusCode(200)
    }

    def deployArtifactAs(usernameRt, passwordRt){
            def path = "generic-dev-local/test-directory/artifact.zip"
            def repoName = "generic-dev-local"
            def directoryName = "test-directory"
            def filename = "artifact.zip"
            def sha256 = utils.generateSHA256(artifact)
            def sha1 = utils.generateSHA1(artifact)
            def md5 = utils.generateMD5(artifact)
            def body = repoListHA
            repoSteps.createRepositories(body, username, password)
            repoSteps.deployArtifactAs(usernameRt, passwordRt, repoName, directoryName, artifact, filename, sha256, sha1, md5)
    }

    def addPermissions(usernameRt){
        def permissionName = "testPermission"
        def repository = "ANY"
        def user1 = usernameRt
        def action1 = "read"
        def action2 = "write"
        def action3 = "manage"
        securitySteps.createSinglePermission(permissionName, repository, user1,
                action1, action2, action3)

    }


    @DataProvider(name="users")
    public Object[][] users() {
        return new Object[][]{
                ["splunktest0", "email0@jfrog.com", "password123", "incorrectPassword"],
                ["splunktest1", "email1@jfrog.com", "password123", "incorrectPassword"],
                ["splunktest2", "email2@jfrog.com", "password123", "incorrectPassword"],
                ["splunktest3", "email3@jfrog.com", "password123", "incorrectPassword"],
                ["splunktest4", "email4@jfrog.com", "password123", "incorrectPassword"]

        }
    }


}
