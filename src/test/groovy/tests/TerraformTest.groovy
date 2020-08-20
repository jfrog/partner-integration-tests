package tests

import io.restassured.RestAssured
import io.restassured.response.Response
import org.hamcrest.Matchers
import org.junit.Assert
import org.testng.Reporter
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test
import org.yaml.snakeyaml.Yaml
import steps.RepositorySteps
import steps.SecuritytSteps

/**
 PTRENG-983, Create automated tests for Terraform provider
 Test won't run Terraform provider itself. It will verify outcome of the provider run after the execution on the
 Artifactory instance.
 Provider's Repo https://github.com/atlassian/terraform-provider-artifactory/blob/master/website/docs/index.html.markdown
 Terraform must be installed first to run the providers.
  */

class TerraformTest {
    Yaml yaml = new Yaml()
    def securitySteps = new SecuritytSteps()
    def repositorySteps = new RepositorySteps()
    def configFile = new File("./src/test/resources/testenv.yaml")
    def config = yaml.load(configFile.text)
    def tfConfigFile = new File("./src/test/resources/terraform/terraform_config.yaml")
    def tfConfig = yaml.load(tfConfigFile.text)
    def artifactoryURL
    def distribution
    def username
    def password

    @BeforeSuite(groups=["terraform"])
    def setUp() {
        artifactoryURL = config.artifactory.external_ip
        distribution = config.artifactory.distribution
        username = config.artifactory.rt_username
        password = config.artifactory.rt_password
        RestAssured.baseURI = "http://${artifactoryURL}/artifactory"
        RestAssured.authentication = RestAssured.basic(username, password);
        RestAssured.useRelaxedHTTPSValidation();
    }

    //Groups
    @Test(priority=1, groups=["terraform"], testName = "Verify group is successfully created by Terraform Provider")
    void createGroupTest(){
        def groupName = tfConfig.groups.groupName
        def groupDescription = tfConfig.groups.groupDescription
        Response get = securitySteps.getGroup(groupName)
        get.then().statusCode(200)
        def name = get.then().extract().path("name")
        def description = get.then().extract().path("description")
        def adminPrivileges = get.then().extract().path("adminPrivileges")
        Assert.assertTrue(name == groupName)
        Assert.assertTrue(description == groupDescription)
        Assert.assertTrue(adminPrivileges == false)

        Reporter.log("- Terraform. Verify group is created. Group '${groupName}' was " +
                "successfully created by Terraform Provider", true)
    }

    //Users
    @Test(priority=2, groups=["terraform"], testName = "Verify users were created successfully by Terraform Provider")
    void verifyUsersTest(){
        def username = tfConfig.users.userName
        def email = tfConfig.users.email
        def groupName = tfConfig.groups.groupName
        Response response = securitySteps.getUserDetails(username)
        response.then().statusCode(200).
                body("name", Matchers.equalTo(username)).
                body("email", Matchers.equalTo(email)).
                body("admin", Matchers.equalTo(false)).
                body("groups", Matchers.hasItems(groupName))

        Reporter.log("- Terraform. Verify user is created. User '${username}' was " +
                "successfully created and added to the '${groupName}' group by Terraform Provider", true)
    }

    //Local Repositories
    @Test(priority=3, groups=["terraform"], testName = "Verify local repository was created successfully by Terraform Provider")
    void verifyLocalTest(){
        def repoName = tfConfig.local_repo.repoName
        def packageType = tfConfig.local_repo.package_type
        Response response = repositorySteps.getRepoConfig(repoName)
        response.then().statusCode(200).
                body("key",  Matchers.equalTo(repoName)).
                body("packageType",  Matchers.equalTo(packageType))

        Reporter.log("- Terraform. Verify local repository was created. Local repository '${repoName}' was " +
                "created successfully by Terraform Provider", true)
    }

    // Permission Targets
    @Test(priority=4, groups=["terraform"], testName = "Verify permission target created successfully by Terraform Provider")
    void getPermissionTargetTest(){
        def repoName = tfConfig.local_repo.repoName
        def parmTergetName = tfConfig.permission_target.name
        def includesPattern = tfConfig.permission_target.includesPattern
        def excludesPattern = tfConfig.permission_target.excludesPattern
        Response response = securitySteps.getPermissionTargetDetails(parmTergetName)
        response.then().statusCode(200).
                body("name", Matchers.equalTo(parmTergetName)).
                body("includesPattern", Matchers.equalTo(includesPattern)).
                body("excludesPattern", Matchers.equalTo(excludesPattern)).
                body("repositories", Matchers.hasItems(repoName))

        Reporter.log("- Terraform. Verify permission target. Permission target '${parmTergetName}' was " +
                "created successfully by Terraform Provider", true)
    }

    //Remote Repositories
    @Test(priority=5, groups=["terraform"], testName = "Verify remote repository was created successfully by Terraform Provider")
    void verifyRemoteRepoTest(){
        def repoName = tfConfig.remote_repo.repoName
        def packageType = tfConfig.remote_repo.package_type
        def url = tfConfig.remote_repo.url
        def repo_layout = tfConfig.remote_repo.repo_layout
        Response response = repositorySteps.getRepoConfig(repoName)
        response.then().statusCode(200).
                body("key",  Matchers.equalTo(repoName)).
                body("packageType",  Matchers.equalTo(packageType)).
                body("url",  Matchers.equalTo(url)).
                body("repoLayoutRef",  Matchers.equalTo(repo_layout))

        Reporter.log("- Terraform. Verify remote repository was created. Remote repository '${repoName}' was " +
                "created successfully by Terraform Provider", true)
    }

    // Replication Configurations - 6
    @Test(priority=6, groups=["terraform"], testName = "Verify replication configuration between two local repos. Terraform Provider")
    void verifyReplicationTest(){
        def sourceRepoName = tfConfig.replication.sourceRepoName
        def destRepoName = tfConfig.replication.destRepoName
        def cronExp = tfConfig.replication.cronExp
        Response sourceResponse = repositorySteps.getReplicationConfig(sourceRepoName)
        sourceResponse.then().statusCode(200).
                body("[0].cronExp",  Matchers.equalTo(cronExp)).
                body("[0].repoKey",  Matchers.equalTo(sourceRepoName)).
                body("[0].username",  Matchers.equalTo(username))
        Response destResponse = repositorySteps.getReplicationConfig(destRepoName)
        destResponse.then().statusCode(404).
                body("errors[0].status",  Matchers.equalTo(404))

        Reporter.log("- Terraform. Verify replication between two local repos. " +
                "Replication configuration was created", true)
    }

    //Single Replication Configurations
    @Test(priority=7, groups=["terraform"], testName = "Verify single replication configuration between two local repos. Terraform Provider")
    void verifySingleReplicationTest(){
        def sourceRepoName = tfConfig.single_replication.sourceRepoName
        def destRepoName = tfConfig.single_replication.destRepoName
        def cronExp = tfConfig.single_replication.cronExp
        Response sourceResponse = repositorySteps.getReplicationConfig(sourceRepoName)
        sourceResponse.then().statusCode(200).
                body("[0].cronExp",  Matchers.equalTo(cronExp)).
                body("[0].repoKey",  Matchers.equalTo(sourceRepoName)).
                body("[0].username",  Matchers.equalTo(username))
        Response destResponse = repositorySteps.getReplicationConfig(destRepoName)
        destResponse.then().statusCode(404).
                body("errors[0].status",  Matchers.equalTo(404))

        Reporter.log("- Terraform. Verify single replication between two local repos. " +
                "Replication configuration was created", true)
    }

    //Virtual Repositories. Provider creates 2 local repos, then adds them to one virtual repo
    @Test(priority=8, groups=["terraform"], testName = "Verify virtual repository was created successfully by Terraform Provider")
    void verifyVirtualTest(){
        def repoName = tfConfig.virtual_repo.repoName
        def packageType = tfConfig.virtual_repo.package_type
        def rclass = tfConfig.virtual_repo.rclass
        String[] repositories = tfConfig.virtual_repo.repos
        Response response = repositorySteps.getRepoConfig(repoName)
        response.then().statusCode(200).
                body("key",  Matchers.equalTo(repoName)).
                body("packageType",  Matchers.equalTo(packageType)).
                body("rclass",  Matchers.equalTo(rclass)).
                body("repositories",  Matchers.hasItems(repositories))

        Reporter.log("- Terraform. Verify virtual repository was created. Virtual repository '${repoName}' was " +
                "created successfully by Terraform Provider", true)
    }

    //Certificates
    @Test(priority=9, groups=["terraform"], testName = "Verify certificate can be added by Terraform Provider")
    void verifyCertificateTest(){
        def repoName = tfConfig.certificate.repoName
        def certAlias = tfConfig.certificate.alias
        Response response = securitySteps.getInstalledCerts()
        response.then().statusCode(200).
                body("[0].certificateAlias", Matchers.equalTo(certAlias))
        Response repoResponse = repositorySteps.getRepoConfig(repoName)
        repoResponse.then().statusCode(200).
                body("clientTlsCertificate", Matchers.equalTo(certAlias))

        Reporter.log("- Terraform. Verify certificate. Certificate '${certAlias}' was successfully added " +
                "and assigned to the remote repo '${repoName}' by Terraform Provider", true)
    }

    // File
    // FileInfo


}
