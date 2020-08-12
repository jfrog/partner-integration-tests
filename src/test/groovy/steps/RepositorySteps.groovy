package steps


import static io.restassured.RestAssured.given

class RepositorySteps {

    def getHealthCheckResponse(artifactoryURL) {
        return given()
                .when()
                .get("http://" + artifactoryURL + "/router/api/v1/system/health")
                .then()
                .extract().response()
    }

    def ping() {
        return given()
                .when()
                .get("/api/system/ping")
                .then()
                .extract().response()
    }

    def createRepositories(File body, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .body(body)
                .when()
                .patch("/api/system/configuration")
                .then()
                .extract().response()
    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-GetRepositories
    def getRepos() {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .when()
                .get("/api/repositories")
                .then()
                .extract().response()

    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-RepositoryConfiguration
    def getRepoConfig(repoName) {
        return given()
                .header("Cache-Control", "no-cache")
                .when()
                .get("/api/repositories/${repoName}")
                .then()
                .extract().response()

    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-DeleteRepository
    def deleteRepository(repoName, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .when()
                .delete("/api/repositories/" + repoName)
                .then()
                .extract().response()

    }

    def createDirectory(repoName, directoryName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/yaml")
                .when()
                .put("/" + repoName + "/" + directoryName)
                .then()
                .extract().response()

    }

    def deployArtifact(repoName, directoryName, artifact, filename, sha256, sha1, md5) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .header("X-Checksum-Sha256", sha256)
                .header("X-Checksum-Deploy", "false")
                .header("X-Checksum-Sha1", sha1)
                .header("X-Checksum", md5)
                .body(artifact)
                .when()
                .put("/" + repoName + "/" + directoryName + "/" + filename)
                .then()
                .extract().response()

    }

    def addChecksumToArtifact(repoName, directoryName, filename) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .body("{\n" +
                        "   \"repoKey\":\"${repoName}\",\n" +
                        "   \"path\":\"${directoryName}/${filename}\"\n" +
                        "}")
                .when()
                .post("/api/checksum/sha256")
                .then()
                .extract().response()

    }

    def deleteItem(path) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .delete("/" + path)
                .then()
                .extract().response()

    }

    def getInfo(path) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get("/api/storage/" + path)
                .then()
                .extract().response()

    }

    def listDockerTags(username, password, repoKey, imageName, listSize, endTag) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get("/api/docker/${repoKey}/v2/${imageName}/tags/list?n=${listSize}&last=${endTag}")
                .then()
                .extract().response()

    }
    // https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-GetRepositoryReplicationConfiguration
    def getReplicationConfig(repoName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get("/api/replications/" + repoName)
                .then()
                .extract().response()

    }

    def createSupportBundle(name, startDate, endDate) {
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
                .post("/api/system/support/bundle")
                .then()
                .extract().response()

    }


}