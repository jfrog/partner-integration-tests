package steps

import io.restassured.response.Response
import io.restassured.http.ContentType
import org.awaitility.Awaitility
import org.testng.Reporter
import org.testng.annotations.DataProvider
import tests.TestSetup

import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.equalTo

class XraySteps extends TestSetup{
    static String artifactFormat(int i) {
        return "artifact_${i}.zip".toString()
    }

    static void deleteExistingWatches(namePrefix, artifactoryBaseURL, username, password) {
        def watches = given()
                .auth().preemptive().basic(username, password)
                .when().get("${artifactoryBaseURL}/xray/api/v2/watches")
                .then().extract().body().jsonPath().getList("\$").stream()
                .map({it.getAt("general_data").getAt("name").toString()})
                .filter({it.startsWith(namePrefix)})
                .collect()
        watches.forEach { name ->
            given().auth().preemptive().basic(username, password)
                    .when().delete("${artifactoryBaseURL}/xray/api/v2/watches/${name}")
                    .then().statusCode(200)
        }

        println("Successfully deleted ${watches.size()} watch${watches.size() == 1 ? "" : "es"}.")

        def policies = given()
                .auth().preemptive().basic(username, password)
                .when().get("${artifactoryBaseURL}/xray/api/v2/policies")
                .then().extract().body().jsonPath().getList("\$").stream()
                .map({it.getAt("name").toString()})
                .filter({it.startsWith(namePrefix)})
                .collect()
        policies.forEach { name ->
            given().auth().preemptive().basic(username, password)
                    .when().delete("${artifactoryBaseURL}/xray/api/v2/policies/${name}")
                    .then().log().ifValidationFails().statusCode(200)
        }

        println("Successfully deleted ${policies.size()} polic${policies.size() == 1 ? "y" : "ies"}.")
    }

    static def getUILoginHeaders(url, username, password) {
        def login = given()
                .auth()
                .basic("${username}", "${password}")
                .headers("X-Requested-With", "XMLHttpRequest") // Needed to use UI api
                .contentType(ContentType.JSON)
                .body("{\n" +
                        "   \"user\":\"${username}\",\n" +
                        "   \"password\":\"${password}\",\n" +
                        "   \"type\":\"login\"\n" +
                        "}")
                .when()
                .post(url + "/ui/api/v1/ui/auth/login")
                .then()
                .assertThat()
                .statusCode(200).and()
                .cookie("ACCESSTOKEN").and()
                .cookie("REFRESHTOKEN")
                .extract().response()

        return given()
                .cookies(login.getDetailedCookies())
                .headers("X-Requested-With", "XMLHttpRequest") // Needed to use UI api
    }

    static Response assignLicenseToArtifact(loginHeaders, url, artifactName, sha256, license_name, license_full_name, license_references) {
        return loginHeaders
                .contentType(ContentType.JSON)
                .body("{\n" +
                        "   \"component\": {\n" +
                        "       \"component_name\":\"${artifactName}\",\n" +
                        "       \"package_id\":\"generic://sha256:${sha256}/${artifactName}\",\n" +
                        "       \"package_type\":\"generic\",\n" +
                        "       \"version\":\"\"\n" +
                        "   },\n" +
                        "   \"license\":{\n" +
                        "       \"name\":\"${license_name}\",\n" +
                        "       \"full_name\":\"${license_full_name}\",\n" +
                        "       \"references\":[\n" +
                        "           \"${license_references}\"\n" +
                        "       ]\n" +
                        "   }\n" +
                        "}")
                .when()
                .post(url+"/ui/api/v1/xray/ui/licenses")
                .then()
                .extract().response()
    }

    static def ensureAssignLicense(UILoginHeaders, artifactoryBaseURL, username, password, artifactName, sha256,
                            license_name, license_full_name, license_references) {
        int tries = 0
        Awaitility.await().atMost(120, TimeUnit.SECONDS).with()
                .pollDelay(1, TimeUnit.SECONDS).and().pollInterval(500, TimeUnit.MILLISECONDS).until { ->
            def success = assignLicenseToArtifact(UILoginHeaders, artifactoryBaseURL, artifactName, sha256,
                    license_name, license_full_name, license_references)
                    .then().extract().statusCode() == 200
            if(!success) {
                // reauthenticate
                Reporter.log("Failed to add ${license_name} to ${artifactName} ${++tries} time(s).", true)
                UILoginHeaders = getUILoginHeaders("${artifactoryBaseURL}", username, password)
            }
            return success
        }
    }

    def createIssueEvent(issueID, cve, summary, description, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "    \"id\": \"${issueID}\",\n" +
                        "    \"type\": \"Security\",\n" +
                        "    \"provider\": \"JFrog\",\n" +
                        "    \"package_type\": \"maven\",\n" +
                        "    \"severity\": \"High\",\n" +
                        "    \"components\": [\n" +
                        "        {\n" +
                        "            \"id\": \"aero:aero\",\n" +
                        "            \"vulnerable_versions\": [\n" +
                        "                \"[0.2.3]\"\n" +
                        "            ]\n" +
                        "        }\n" +
                        "    ],\n" +
                        "    \"cves\": [\n" +
                        "        {\n" +
                        "            \"cve\": \"${cve}\",\n" +
                        "            \"cvss_v2\": \"2.4\"\n" +
                        "        }\n" +
                        "    ],\n" +
                        "    \"summary\": \"${summary}\",\n" +
                        "    \"description\": \"${description}\",\n" +
                        "    \"sources\": [\n" +
                        "        {\n" +
                        "            \"source_id\": \"${cve}\"\n" +
                        "        }\n" +
                        "    ]\n" +
                        "}")
                .when()
                .post(url + "/v1/events")
                .then()
                .extract().response()
    }

    def createSecurityIssueEvents(issueID, cve, summary, description, issueType, severity, sha256, artifactName, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "    \"id\": \"${issueID}\",\n" +
                        "    \"type\": \"${issueType}\",\n" +
                        "    \"provider\": \"JFrog\",\n" +
                        "    \"package_type\": \"generic\",\n" +
                        "    \"severity\": \"${severity}\",\n" +
                        "    \"components\": [\n" +
                        "        {\n" +
                        "            \"id\": \"sha256:${sha256}/${artifactName}\",\n" +
                        "            \"vulnerable_versions\": [\n" +
                        "                \"[1.0.0]\"\n" +
                        "            ]\n" +
                        "        }\n" +
                        "    ],\n" +
                        "    \"cves\": [\n" +
                        "        {\n" +
                        "            \"cve\": \"${cve}\",\n" +
                        "            \"cvss_v2\": \"5.0/CVSS:2.0/AV:N/AC:L/Au:N/C:N/I:P/A:N\"\n" +
                        "        }\n" +
                        "    ],\n" +
                        "    \"summary\": \"${summary}\",\n" +
                        "    \"description\": \"${description}\",\n" +
                        "    \"sources\": [\n" +
                        "        {\n" +
                        "            \"source_id\": \"${cve}\"\n" +
                        "        }\n" +
                        "    ]\n" +
                        "}")
                .when()
                .post(url + "/v1/events")
                .then()
                .extract().response()
    }


    def updateIssueEvent(issueID, cve, summary, description, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "    \"id\": \"${issueID}\",\n" +
                        "    \"type\": \"Security\",\n" +
                        "    \"provider\": \"JFrog\",\n" +
                        "    \"package_type\": \"maven\",\n" +
                        "    \"severity\": \"High\",\n" +
                        "    \"components\": [\n" +
                        "        {\n" +
                        "            \"id\": \"aero:aero\",\n" +
                        "            \"vulnerable_versions\": [\n" +
                        "                \"[0.2.3]\"\n" +
                        "            ]\n" +
                        "        }\n" +
                        "    ],\n" +
                        "    \"cves\": [\n" +
                        "        {\n" +
                        "            \"cve\": \"${cve}\",\n" +
                        "            \"cvss_v2\": \"2.4\"\n" +
                        "        }\n" +
                        "    ],\n" +
                        "    \"summary\": \"${summary}\",\n" +
                        "    \"description\": \"${description}\",\n" +
                        "    \"sources\": [\n" +
                        "        {\n" +
                        "            \"source_id\": \"${cve}\"\n" +
                        "        }\n" +
                        "    ]\n" +
                        "}")
                .when()
                .put(url + "/v1/events/${issueID}")
                .then()
                .extract().response()
    }

    def createPolicy(policyName, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "  \"name\": \"${policyName}\",\n" +
                        "  \"type\": \"security\",\n" +
                        "  \"description\": \"some description\",\n" +
                        "  \"rules\": [\n" +
                        "    {\n" +
                        "      \"name\": \"securityRule\",\n" +
                        "      \"priority\": 1,\n" +
                        "      \"criteria\": {\n" +
                        "        \"min_severity\": \"Low\"\n" +
                        "      },\n" +
                        "      \"actions\": {\n" +
                        "        \"mails\": [\n" +
                        "          \"mail1@example.com\",\n" +
                        "          \"mail2@example.com\"\n" +
                        "        ],\n" +
                        "        \"fail_build\": true,\n" +
                        "        \"block_download\": {\n" +
                        "          \"unscanned\": false,\n" +
                        "          \"active\": false\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}")
                .when()
                .post(url + "/v1/policies")
                .then()
                .extract().response()
    }

    def createLicensePolicy(policyName, username, password, url, licenseName) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "  \"name\": \"${policyName}\",\n" +
                        "  \"type\": \"license\",\n" +
                        "  \"description\": \"License issue\",\n" +
                        "  \"rules\": [\n" +
                        "    {\n" +
                        "      \"name\": \"License${licenseName}Rule\",\n" +
                        "      \"priority\": 1,\n" +
                        "      \"criteria\": {\n" +
                        "        \"banned_licenses\": [\n" +
                        "          \"${licenseName}\"\n" +
                        "        ],\n" +
                        "        \"allow_unknown\": true\n" +
                        "      }\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}")
                .when()
                .post(url + "/v1/policies")
                .then()
                .extract().response()
    }


    def updatePolicy(policyName, description, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "  \"name\": \"${policyName}\",\n" +
                        "  \"type\": \"security\",\n" +
                        "  \"description\": \"${description}\",\n" +
                        "  \"rules\": [\n" +
                        "    {\n" +
                        "      \"name\": \"securityRule\",\n" +
                        "      \"priority\": 1,\n" +
                        "      \"criteria\": {\n" +
                        "        \"min_severity\": \"High\"\n" +
                        "      },\n" +
                        "      \"actions\": {\n" +
                        "        \"mails\": [\n" +
                        "          \"mail1@example.com\",\n" +
                        "          \"mail2@example.com\"\n" +
                        "        ],\n" +
                        "        \"fail_build\": true,\n" +
                        "        \"block_download\": {\n" +
                        "          \"unscanned\": true,\n" +
                        "          \"active\": true\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}")
                .when()
                .put(url +"/v1/policies/${policyName}")
                .then()
                .extract().response()
    }

    def getPolicy(policyName, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get(url + "/v1/policies/${policyName}")
                .then()
                .extract().response()
    }

    def getPolicies(username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get(url + "/v1/policies")
                .then()
                .extract().response()
    }

    def deletePolicy(policyName, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .delete(url + "/v1/policies/${policyName}")
                .then()
                .extract().response()
    }

    def getIssueEvent(issueID, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get(url + "/v2/events/${issueID}")
                .then()
                .extract().response()
    }

    def createWatchEvent(watchName, policyName, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "    \"general_data\": {\n" +
                        "        \"name\": \"${watchName}\",\n" +
                        "        \"description\": \"This is a new watch created using API V2\",\n" +
                        "        \"active\": true\n" +
                        "    },\n" +
                        "    \"project_resources\": {\n" +
                        "        \"resources\": [\n" +
                        "            {\n" +
                        "                \"type\": \"all-repos\",\n" +
                        "                \"filters\": [\n" +
                        "                    {\n" +
                        "                        \"type\": \"package-type\",\n" +
                        "                        \"value\": \"Docker\"\n" +
                        "                    },\n" +
                        "                    {\n" +
                        "                        \"type\": \"package-type\",\n" +
                        "                        \"value\": \"Generic\"\n" +
                        "                    },\n" +
                        "                    {\n" +
                        "                        \"type\": \"package-type\",\n" +
                        "                        \"value\": \"Debian\"\n" +
                        "                    }\n" +
                        "                ]\n" +
                        "            }\n" +
                        "        ]\n" +
                        "    },\n" +
                        "    \"assigned_policies\": [\n" +
                        "        {\n" +
                        "            \"name\": \"${policyName}\",\n" +
                        "            \"type\": \"security\"\n" +
                        "        }\n" +
                        "    ]\n" +
                        "}")
                .when()
                .post(url+ "/v2/watches")
                .then()
                .extract().response()
    }


    def createWatchEventTEST(watchName, policyName, policyType, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "  \"general_data\": {\n" +
                        "    \"name\": \"${watchName}\",\n" +
                        "    \"description\": \"This is a new watch created using API V2\",\n" +
                        "    \"active\": true\n" +
                        "  },\n" +
                        "  \"project_resources\": {\n" +
                        "    \"resources\": [\n" +
                        "      {\n" +
                        "        \"category\": \"all\",\n" +
                        "        \"name\": \"\",\n" +
                        "        \"repo_type\": \"\",\n" +
                        "        \"filters\": [],\n" +
                        "        \"type\": \"all-repos\",\n" +
                        "        \"include_patterns\": [],\n" +
                        "        \"exclude_patterns\": []\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  },\n" +
                        "  \"assigned_policies\": [\n" +
                        "    {\n" +
                        "      \"name\": \"${policyName}\",\n" +
                        "      \"type\": \"${policyType}\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"watch_recipients\": [\n" +
                        "    \"name@myemail.com\",\n" +
                        "    \"umac@youremail.com\"\n" +
                        "  ]\n" +
                        "}")
                .when()
                .post(url+ "/v2/watches")
                .then()
                .extract().response()
    }

    def createWatch(watchName, policy, policyType, username, password, xrayBaseUrl) {
        Response createWatch = createWatchEventTEST(watchName, policy, policyType, username, password, xrayBaseUrl)
        createWatch.then().log().ifValidationFails().statusCode(201)
                .body("info", equalTo("Watch has been successfully created"))
        Response getWatch = getWatchEvent(watchName, username, password, xrayBaseUrl)
        getWatch.then().statusCode(200)
                .body("general_data.name", equalTo((watchName).toString()))
    }


    def updateWatchEvent(watchName, description, policyName, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "    \"general_data\": {\n" +
                        "        \"name\": \"${watchName}\",\n" +
                        "        \"description\": \"${description}\",\n" +
                        "        \"active\": true\n" +
                        "    },\n" +
                        "    \"project_resources\": {\n" +
                        "        \"resources\": [\n" +
                        "            {\n" +
                        "                \"type\": \"all-repos\",\n" +
                        "                \"filters\": [\n" +
                        "                    {\n" +
                        "                        \"type\": \"package-type\",\n" +
                        "                        \"value\": \"Docker\"\n" +
                        "                    },\n" +
                        "                    {\n" +
                        "                        \"type\": \"package-type\",\n" +
                        "                        \"value\": \"Debian\"\n" +
                        "                    }\n" +
                        "                ]\n" +
                        "            }\n" +
                        "        ]\n" +
                        "    },\n" +
                        "    \"assigned_policies\": [\n" +
                        "        {\n" +
                        "            \"name\": \"${policyName}\",\n" +
                        "            \"type\": \"security\"\n" +
                        "        }\n" +
                        "    ]\n" +
                        "}")
                .when()
                .put(url + "/v2/watches/${watchName}")
                .then()
                .extract().response()
    }

    def getWatchEvent(watchName, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get(url + "/v2/watches/${watchName}")
                .then()
                .extract().response()
    }

    def deleteWatchEvent(watchName, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .delete(url + "/v2/watches/${watchName}")
                .then()
                .extract().response()
    }

    def assignPolicy(watchName, policyName, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "    \"watches\": [\n" +
                        "        \"${watchName}\"\n" +
                        "    ]\n" +
                        "}")
                .when()
                .post(url+ "/v1/policies/${policyName}/assign")
                .then()
                .extract().response()
    }

    def getIntegrationConfiguration(username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get(url + "/v1/integration")
                .then()
                .extract().response()
    }

    def addtIntegrationConfiguration(username, password, vendorName, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "  \"vendor\": \"${vendorName}\",\n" +
                        "  \"api_key\": \"12345\",\n" +
                        "  \"enabled\": true,\n" +
                        "  \"context\": \"project_id\",\n" +
                        "  \"url\": \"https://saas.whitesourcesoftware.com/xray\",\n" +
                        "  \"description\": \"WhiteSource provides a simple yet powerful open source security and licenses management solution. More details at http://www.whitesourcesoftware.com.\",\n" +
                        "  \"test_url\": \"https://saas.whitesourcesoftware.com/xray/api/checkauth\"\n" +
                        "}")
                .when()
                .post(url + "/v1/integration")
                .then()
                .extract().response()
    }
    // test was removed since Xray 3.29.2. It was breaking RabbitMQ cluster and Xray became non-functional
    def postSystemParameters(username, password, body, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body(body)
                .when()
                .put(url + "/v1/configuration/systemParameters")
                .then()
                .extract().response()
    }

    def getSystemParameters(username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get(url+ "/v1/configuration/systemParameters")
                .then()
                .extract().response()
    }

    def getBinaryManager(username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get(url + "/v1/binMgr/default")
                .then()
                .extract().response()
    }


    def getIndexingConfiguration(username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get(url + "/v1/binMgr/default/repos")
                .then()
                .extract().response()
    }

    def updateIndexingConfiguration(username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "    \"indexed_repos\": [\n" +
                        "        {\n" +
                        "            \"name\": \"docker-local\",\n" +
                        "            \"type\": \"local\",\n" +
                        "            \"pkg_type\": \"Docker\"\n" +
                        "        },\n" +
                        "        {\n" +
                        "            \"name\": \"generic-dev-local\",\n" +
                        "            \"type\": \"local\",\n" +
                        "            \"pkg_type\": \"Generic\"\n" +
                        "        }\n" +
                        "    ],\n" +
                        "    \"non_indexed_repos\": []\n" +
                        "}")
                .when()
                .put(url + "/v1/binMgr/default/repos")
                .then()
                .extract().response()
    }


    def forceReindex(username, password, url, repository, directoryName, filename) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .body("{\n" +
                        "\"artifactory_id\": \"default\",\n" +
                        "    \"artifacts\": [\n" +
                        "        {\n" +
                        "            \"repository\": \"${repository}\",\n" +
                        "            \"path\": \"${directoryName}/${filename}\" \n" +
                        "        }\n" +
                        "    ]\n" +
                        "}")
                .when()
                .post(url + "/v1/forceReindex")
                .then()
                .extract().response()
    }



    def startScan(username, password, componentID, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .body("{\n" +
                        " \"componentID\": \"${componentID}\"\n" +
                        "}")
                .when()
                .post(url + "/v1/scanArtifact")
                .then()
                .extract().response()
    }


    def artifactSummary(username, password, artifactPath, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "  \"checksums\": [\n" +
                        "    \"\"\n" +
                        "  ],\n" +
                        "  \"paths\": [\n" +
                        "    \"${artifactPath}\"\n" +
                        "  ]\n" +
                        "}")
                .when()
                .post(url + "/v1/summary/artifact")
                .then()
                .extract().response()
    }

    def createSupportBundle(username, password, name, startDate, endDate, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .body("{ \n" +
                        "   \"name\":\"${name}\",\n" +
                        "   \"description\":\"desc\",\n" +
                        "   \"parameters\":{ \n" +
                        "      \"configuration\": true,\n" +
                        "      \"system\": true,             \n" +
                        "      \"logs\":{ \n" +
                        "         \"include\": true,          \n" +
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
                .post(url+ "/v1/system/support/bundle")
                .then()
                .extract().response()

    }

    def getSystemMonitoringStatus(username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get(url + "/v1/monitor")
                .then()
                .extract().response()
    }

    def xrayPingRequest(username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get(url + "/v1/system/ping")
                .then()
                .extract().response()
    }

    def xrayGetVersion(username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .when()
                .get(url + "/v1/system/version")
                .then()
                .extract().response()
    }

    def xrayGetViolations(violationType, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json")
                .body("{\n" +
                        "    \"filters\": {\n" +
                        "        \"name_contains\": \"*\",\n" +
                        "        \"violation_type\": \"${violationType}\",\n" +
                        "        \"min_severity\": \"Low\",\n" +
                        "        \"created_from\": \"1970-01-01T00:00:00Z\"\n" +
                        "    },\n" +
                        "    \"pagination\": {\n" +
                        "        \"order_by\": \"updated\",\n" +
                        "        \"limit\": 25,\n" +
                        "        \"offset\": 1\n" +
                        "    }\n" +
                        "}")
                .when()
                .post(url + "/v1/violations")
                .then()
                .extract().response()
    }

    def getXrayMetrics(username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get(url + "/v1/metrics")
                .then()
                .extract().response()
    }

    // Expected Results

    static Map<String, Integer> getExpectedSeverities(license_issues, security_issues) {
        Map<String, Integer> expected = security_issues.stream().collect(
                Collectors.toMap(
                        {Object[] it -> it[5].toString()},
                        {Object[] it -> it[6].size()},
                        Integer::sum
                )
        )
        int licenseCount = license_issues.stream().reduce((int) 0, (int count, list) -> count + list[3].size())

        expected.put("High", expected.getOrDefault("High", 0) + licenseCount)
        return expected
    }

    static Map<String, Integer> getExpectedComponentCounts(license_issues, security_issues) {
        def componentCounts = new HashMap<String, Integer>()
        license_issues.forEach { it ->
            it[3].forEach { artifactId ->
                def artifactName = artifactFormat(artifactId)
                componentCounts.put(artifactName, componentCounts.getOrDefault(artifactName, 0) + 1)
            }
        }

        security_issues.forEach { it ->
            it[6].forEach { artifactId ->
                def artifactName = artifactFormat(artifactId)
                componentCounts.put(artifactName, componentCounts.getOrDefault(artifactName, 0) + 1)
            }
        }
        return componentCounts
    }


    static Map<String, Integer> getExpectedViolationCounts(license_issues, security_issues) {
        def expected = license_issues.stream().collect(
                Collectors.toMap(
                        {Object[] it -> it[0].toString()},
                        {Object[] it -> it[3].size()},
                        Integer::sum
                )
        )

        expected.put("security", security_issues.stream().reduce((int) 0, (int count, list) -> count + list[6].size()))
        return expected
    }


    static Map<String, Integer> getExpectedCVECounts(security_issues) {
        return security_issues.stream().collect(
                Collectors.toMap(
                        it -> it[1].toString(),
                        it -> it[6].size(),
                        Integer::sum
                )
        )
    }


    // Data providers

    @DataProvider(name = "artifacts")
    public Object[][] artifacts() {
        return new Object[][]{
                ["artifact_0.zip"],
                ["artifact_1.zip"],
                ["artifact_2.zip"],
                ["artifact_3.zip"],
                ["artifact_4.zip"],
                ["artifact_5.zip"],
                ["artifact_6.zip"],
                ["artifact_7.zip"],
                ["artifact_8.zip"],
                ["artifact_9.zip"]
        }
    }


    @DataProvider(name = "issueEvents")
    public Object[][] issueEvents() {
        return new Object[][]{
                ["XRAY-", "CVE-2017-2000386", "A very important custom issue", "A very important custom issue"]

        }
    }

    @DataProvider(name = "multipleIssueEvents")
    public Object[][] multipleIssueEvents() {
        return new Object[][]{
                ["XRAYS0-", "CVE-2017-2000386", "Custom issue 0", "The Hackers can get access to your source code", "Security", "Medium", [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]],
                ["XRAYS1-", "CVE-2018-2000568", "Custom issue 1", "Root access could be granted to a stranger", "Security", "High",[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]],
                ["XRAYS2-", "CVE-2020-2000554", "Custom issue 2", "Everything will fall apart if you use this binary", "Security", "High",[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]],
                ["XRAYS3-", "CVE-2021-2001325", "Custom issue 3", "Never use the binary with this issue", "Security", "Medium",[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]],
                ["XRAYS4-", "CVE-2019-2005843", "Custom issue 4", "Beware of this zip file", "Security", "Low",[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]]

        }
    }

    @DataProvider(name = "multipleLicenseIssueEvents")
    public Object[][] multipleLicenseIssueEvents() {
        return new Object[][]{
                ["0BSD", "BSD Zero Clause License", "https://spdx.org/licenses/0BSD.html", [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]],
                ["AAL", "Attribution Assurance License", "https://spdx.org/licenses/AAL.html",[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]],
                ["Abstyles", "Abstyles License", "https://spdx.org/licenses/Abstyles.html",[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]],
                ["Adobe-2006", "Adobe Systems Incorporated Source Code License Agreement", "https://spdx.org/licenses/Adobe-2006.html",[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]],
                ["Adobe-Glyph", "Adobe Glyph List License", "https://spdx.org/licenses/Adobe-Glyph.html",[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]]
        }
    }

}