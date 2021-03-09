package steps

import org.testng.annotations.DataProvider

import static io.restassured.RestAssured.given


class SecuritytSteps {

    def createUser(artifactoryURL, username, password, usernameRt, emailRt, passwordRt) {
        return given().log().uri()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "  \"email\" : \"${emailRt}\",\n" +
                        "  \"password\": \"${passwordRt}\",\n" +
                        "  \"name\": \"${usernameRt}\"\n" +
                        "}")
                .when().log().all()
                .put("${artifactoryURL}/api/security/users/${usernameRt}")
                .then().log().everything()
                .extract().response()
    }

    def getUserDetails(artifactoryURL, usernameRt) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/security/users/${usernameRt}")
                .then()
                .extract().response()
    }

    def deleteUser(artifactoryURL, usernameRt) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .delete("${artifactoryURL}/api/security/users/${usernameRt}")
                .then()
                .extract().response()
    }

    def generateAPIKey(artifactoryURL, usernameRt, passwordRt) {
        return given()
                .auth()
                .preemptive()
                .basic("${usernameRt}", "${passwordRt}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .post("${artifactoryURL}/api/security/apiKey")
                .then()
                .extract().response()
    }

    def getAPIKey(artifactoryURL, usernameRt, passwordRt) {
        return given()
                .auth()
                .preemptive()
                .basic("${usernameRt}", "${passwordRt}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/security/apiKey")
                .then()
                .extract().response()
    }

    def regenerateAPIKey(artifactoryURL, usernameRt, passwordRt) {
        return given()
                .auth()
                .preemptive()
                .basic("${usernameRt}", "${passwordRt}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .put("${artifactoryURL}/api/security/apiKey")
                .then()
                .extract().response()
    }

    def createGroup(artifactoryURL, groupName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\"name\": \"${groupName}\"}")
                .when()
                .put("${artifactoryURL}/api/security/groups/${groupName}")
                .then()
                .extract().response()
    }

    def getGroup(artifactoryURL, groupName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\"name\": \"${groupName}\"}")
                .when()
                .get("${artifactoryURL}/api/security/groups/${groupName}")
                .then()
                .extract().response()
    }

    def deleteGroup(artifactoryURL, groupName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .delete("${artifactoryURL}/api/security/groups/${groupName}")
                .then()
                .extract().response()
    }

    def createPermissions(artifactoryURL, permissionName, repository, user1, user2,
                          group1, group2, action1, action2, action3) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        " \"name\": \"${permissionName}\",\n" +
                        "   \"repo\": {\n" +
                        "      \"repositories\": [ \"${repository}\" ],\n" +
                        "      \"actions\": {\n" +
                        "          \"users\" : {\n" +
                        "            \"${user1}\": [ \"${action1}\",\"${action2}\",\"${action3}\" ], \n" +
                        "            \"${user2}\" : [ \"${action1}\",\"${action2}\",\"${action3}\" ]\n" +
                        "          },\n" +
                        "          \"groups\" : {\n" +
                        "            \"${group1}\" : [ \"${action1}\",\"${action2}\",\"${action3}\" ],\n" +
                        "            \"${group2}\" : [ \"${action1}\",\"${action2}\" ]\n" +
                        "          }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}")
                .when()
                .put("${artifactoryURL}/api/v2/security/permissions/${permissionName}")
                .then()
                .extract().response()
    }

    def createSinglePermission(artifactoryURL, username, password, permissionName, repository, user1,
                          action1, action2, action3) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "    \"name\": \"${permissionName}\",\n" +
                        "    \"repo\": {\n" +
                        "        \"repositories\": [\n" +
                        "            \"${repository}\"\n" +
                        "        ],\n" +
                        "        \"actions\": {\n" +
                        "            \"users\": {\n" +
                        "                \"${user1}\": [\n" +
                        "                    \"${action1}\",\n" +
                        "                    \"${action2}\",\n" +
                        "                    \"${action3}\"\n" +
                        "                ]\n" +
                        "            }\n" +
                        "        }\n" +
                        "    }\n" +
                        "}")
                .when()
                .put("${artifactoryURL}/api/v2/security/permissions/${permissionName}")
                .then()
                .extract().response()
    }

    def getPermissions(artifactoryURL, permissionName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/v2/security/permissions/${permissionName}")
                .then()
                .extract().response()
    }

    def deletePermissions(artifactoryURL, permissionName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .delete("${artifactoryURL}/api/v2/security/permissions/${permissionName}")
                .then()
                .extract().response()
    }

    def getInstalledCerts(artifactoryURL) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/system/security/certificates")
                .then()
                .extract().response()
    }

    def getPermissionTargetDetails(artifactoryURL, parmTergetName) {
        return given()
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/security/permissions/${parmTergetName}")
                .then()
                .extract().response()
    }

    def generateError500(artifactoryURL, username, password){
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .post("${artifactoryURL}/api/system/usage")
                .then()
                .extract().response()
    }

    def login(url, usernameRt, passwordRt) {
        return given()
                .relaxedHTTPSValidation()
                .header("Connection", "keep-alive")
                .header("Accept", "application/json, text/plain, */*")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.135 Safari/537.36")
                .header("Origin", "http://35.188.4.233")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Content-Type", "application/json;charset=UTF-8")
                .body("{\"user\":\"${usernameRt}\",\"password\":\"${passwordRt}\",\"type\":\"login\"}")
                .when()
                .post("http://${url}/ui/api/v1/ui/auth/login?_spring_security_remember_me=false")
                .then()
                .extract().response()
    }


    // Data providers

    @DataProvider(name="users")
    public Object[][] users() {
        return new Object[][]{
                ["testuser0", "email0@jfrog.com", "Password123"],
                ["testuser1", "email1@jfrog.com", "Password123"],
                ["testuser2", "email2@jfrog.com", "Password123"],
                ["testuser3", "email3@jfrog.com", "Password123"],
                ["testuser4", "email4@jfrog.com", "Password123"],
                ["testuser5", "email5@jfrog.com", "Password123"],
                ["testuser6", "email6@jfrog.com", "Password123"],
                ["testuser7", "email7@jfrog.com", "Password123"],
                ["testuser8", "email8@jfrog.com", "Password123"],
                ["testuser9", "email9@jfrog.com", "Password123"]
        }
    }

    @DataProvider(name="groups")
    public Object[][] groups() {
        return new Object[][]{
                ["test-group-0"],
                ["test-group-1"],
                ["test-group-2"],
                ["test-group-3"],
                ["test-group-4"],
                ["test-group-5"],
                ["test-group-6"],
                ["test-group-7"],
                ["test-group-8"],
                ["test-group-9"]
        }
    }
}
