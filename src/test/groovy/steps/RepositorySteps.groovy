package steps


import static io.restassured.RestAssured.given

class RepositorySteps {

    def getHealthCheckResponse(artifactoryURL) {
        return given()
                .when()
                .get("${artifactoryURL}/router/api/v1/system/health")
                .then()
                .extract().response()
    }

    def ping(artifactoryURL) {
        return given()
                .when()
                .get("${artifactoryURL}/api/system/ping")
                .then()
                .extract().response()
    }

    def createRepositories(artifactoryURL, File body, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .body(body)
                .when()
                .patch("${artifactoryURL}/api/system/configuration")
                .then()
                .extract().response()
    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-GetRepositories
    def getRepos(artifactoryURL, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .when()
                .get("${artifactoryURL}/api/repositories")
                .then()
                .extract().response()

    }
    def getReposWithUser(artifactoryURL, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .when()
                .get("${artifactoryURL}/api/repositories")
                .then()
                .extract().response()

    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-RepositoryConfiguration
    def getRepoConfig(artifactoryURL, repoName) {
        return given()
                .header("Cache-Control", "no-cache")
                .when()
                .get("${artifactoryURL}/api/repositories/${repoName}")
                .then()
                .extract().response()

    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-DeleteRepository
    def deleteRepository(artifactoryURL, repoName, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .when()
                .delete("${artifactoryURL}/api/repositories/" + repoName)
                .then()
                .extract().response()

    }

    def createDirectory(artifactoryURL, repoName, directoryName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .when()
                .put("${artifactoryURL}/${repoName}/${directoryName}")
                .then()
                .extract().response()

    }

    def deployArtifact(artifactoryURL, username, password, repoName, directoryName, artifact, filename, sha256, sha1, md5) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
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

    def deployArtifactAs(artifactoryURL, username, password, repoName, directoryName, artifact, filename, sha256, sha1, md5) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
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

    def downloadArtifact(artifactoryURL, username, password, repoName, directoryName, filename) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/octet-stream")
                .when()
                .get("${artifactoryURL}/api/download/${repoName}/${directoryName}/${filename}")
                .then()
                .extract().response()
    }



    def addChecksumToArtifact(artifactoryURL, repoName, directoryName, filename) {
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

    def deleteItem(artifactoryURL, username, password, path) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .delete("${artifactoryURL}/" + path)
                .then()
                .extract().response()

    }

    def getInfo(artifactoryURL, path) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/storage/" + path)
                .then()
                .extract().response()

    }

    def listDockerTags(artifactoryURL, username, password, repoKey, imageName, listSize, endTag) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/docker/${repoKey}/v2/${imageName}/tags/list?n=${listSize}&last=${endTag}")
                .then()
                .extract().response()

    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-GetRepositoryReplicationConfiguration
    def getReplicationConfig(artifactoryURL, repoName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/replications/" + repoName)
                .then()
                .extract().response()

    }

    def createSupportBundle(artifactoryURL, name, startDate, endDate) {
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


}