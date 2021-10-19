package steps

import tests.TestSetup

import static io.restassured.RestAssured.given

class RepositorySteps extends TestSetup {

    static def acceptEula(artifactoryURL, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .when()
                .post("${artifactoryURL}/ui/jcr/eula/accept")
                .then()
                .extract().response()
    }

    static def getHealthCheckResponse(artifactoryURL) {
        return given()
                .when()
                .get("${artifactoryURL}/router/api/v1/system/health")
                .then()
                .extract().response()
    }

    static def ping(artifactoryURL) {
        return given()
                .when()
                .get("${artifactoryURL}/api/system/ping")
                .then()
                .extract().response()
    }

    static def setBaseUrl(artifactoryURL, username, password, String baseUrl) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("content-Type", "text/plain")
                .body(baseUrl)
                .when()
                .put("${artifactoryURL}/api/system/configuration/baseUrl")
                .then()
                .extract().response()
    }

    static def createRepositories(artifactoryURL, File body, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .body(body)
                .when()
                .patch("${artifactoryURL}/api/system/configuration")
                .then()
                .extract().response()
    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-GetRepositories
    static def getRepos(artifactoryURL, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .when()
                .get("${artifactoryURL}/api/repositories")
                .then()
                .extract().response()


    }

    static def getReposWithUser(artifactoryURL, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .when()
                .get("${artifactoryURL}/api/repositories")
                .then()
                .extract().response()

    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-RepositoryConfiguration
    static def getRepoConfig(artifactoryURL, repoName) {
        return given()
                .header("Cache-Control", "no-cache")
                .when()
                .get("${artifactoryURL}/api/repositories/${repoName}")
                .then()
                .extract().response()

    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-DeleteRepository
    static def deleteRepository(artifactoryURL, String repoName, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .when()
                .delete("${artifactoryURL}/api/repositories/" + repoName)
                .then()
                .extract().response()

    }

    static def createDirectory(artifactoryURL, repoName, directoryName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .when()
                .put("${artifactoryURL}/${repoName}/${directoryName}")
                .then()
                .extract().response()

    }

    static def deployArtifact(artifactoryURL, username, password, repoName, directoryName,
                              File artifact, filename, sha256, sha1, md5) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("mime-Type", "application/zip")
                .header("X-Checksum-Sha256", sha256)
                .header("X-Checksum-Deploy", "false")
                .header("X-Checksum-Sha1", sha1)
                .header("X-Checksum", md5)
                .body(artifact)
                .when()
                .put("${artifactoryURL}/${repoName}/${directoryName}/${filename}")
                .then()
                .extract().response()

    }

    static def deployArtifactAs(artifactoryURL, username, password, repoName, directoryName,
                                File artifact, filename, sha256, sha1, md5) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("mime-Type", "application/zip")
                .header("X-Checksum-Sha256", sha256)
                .header("X-Checksum-Deploy", "false")
                .header("X-Checksum-Sha1", sha1)
                .header("X-Checksum", md5)
                .body(artifact)
                .when()
                .put("${artifactoryURL}/${repoName}/${directoryName}/${filename}")
                .then()
                .extract().response()

    }

    static def downloadArtifact(artifactoryURL, username, password, repoName, directoryName, filename) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/octet-stream")
                .when()
                .get("${artifactoryURL}/api/download/${repoName}/${directoryName}/${filename}")
                .then()
                .extract().response()
    }


    static def addChecksumToArtifact(artifactoryURL, repoName, directoryName, filename) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .body("{\n" +
                        "   \"repoKey\":\"${repoName}\",\n" +
                        "   \"path\":\"${directoryName}/${filename}\"\n" +
                        "}")
                .when()
                .post("${artifactoryURL}/api/checksum/sha256")
                .then()
                .extract().response()

    }

    static def deleteItem(artifactoryURL, username, password, String path) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .delete("${artifactoryURL}/" + path)
                .then()
                .extract().response()

    }

    static def getInfo(artifactoryURL, String path) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/storage/" + path)
                .then()
                .extract().response()

    }

    static def listDockerTags(artifactoryURL, username, password, repoKey, imageName, listSize, endTag) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/docker/${repoKey}/v2/${imageName}/tags/list?n=${listSize}&last=${endTag}")
                .then()
                .extract().response()

    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-GetRepositoryReplicationConfiguration
    static def getReplicationConfig(artifactoryURL, String repoName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/replications/" + repoName)
                .then()
                .extract().response()

    }

    static def createSupportBundle(artifactoryURL, name, startDate, endDate) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .body("{ \n" +
                        "   \"name\":\"${name}\",\n" +
                        "   \"description\":\"desc\",\n" +
                        "   \"parameters\":{ \n" +
                        "      \"configuration\": \"true\",\n" +
                        "      \"system\": \"true\",             \n" +
                        "      \"logs\":{ \n" +
                        "         \"include\": \"true\",          \n" +
                        "         \"start_date\":\"${startDate}\",\n" +
                        "         \"end_date\":\"${endDate}\"\n" +
                        "      },\n" +
                        "      \"thread_dump\":{ \n" +
                        "         \"count\": 1,\n" +
                        "         \"interval\": 0\n" +
                        "      }\n" +
                        "   }\n" +
                        "}")
                .when()
                .post("${artifactoryURL}/api/system/support/bundle")
                .then()
                .extract().response()

    }

    static def buildUpload(artifactoryURL, username, password, File body) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .auth()
                .preemptive()
                .basic(username, password)
                .body(body)
                .when()
                .put("${artifactoryURL}/api/build")
                .then()
                .extract().response()

    }

    static def getBuildInfo(artifactoryURL, username, password, buildName, buildNumber) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .auth()
                .preemptive()
                .basic(username, password)
                .when()
                .get("${artifactoryURL}/api/build/${buildName}/${buildNumber}")
                .then()
                .extract().response()

    }
}