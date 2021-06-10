package steps

import io.restassured.response.Response
import org.testng.annotations.DataProvider
import tests.TestSetup

import java.util.regex.Matcher

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.equalTo

class XraySteps extends TestSetup{

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

    def createIssueEvents(issueID, cve, summary, description, issueType, sha256, artifactName, username, password, url) {
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
                        "    \"severity\": \"High\",\n" +
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


    def updateIssueEvent(issueID, cve, summary, description, username, password, url) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
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

    def createLicensePolicy(policyName, username, password, url) {
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
                        "      \"name\": \"LicenseRule\",\n" +
                        "      \"priority\": 1,\n" +
                        "      \"criteria\": {\n" +
                        "        \"banned_licenses\": [\n" +
                        "          \"0BSD\",\n" +
                        "          \"AAL\"\n" +
                        "        ],\n" +
                        "        \"allow_unknown\": false\n" +
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
                .get(url + "/v1/events/${issueID}")
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


    def forceReindex(username, password, url) {
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
                        "            \"repository\": \"generic-dev-local\",\n" +
                        "            \"path\": \"test-directory/artifact.zip\" \n" +
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

    def xrayGetViolations(violationType, watchName, username, password, url) {
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
                        "        \"watch_name\": \"${watchName}\",\n" +
                        "        \"min_severity\": \"Low\",\n" +
                        "        \"created_from\": \"2018-06-06T12:22:16+03:00\"\n" +
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
                ["XRAY0-", "CVE-2017-2000386", "Custom issue 0", "The Hackers can get access to your source code", "Security"],
                ["XRAY1-", "CVE-2018-2000568", "Custom issue 1", "Root access could be granted to a stranger", "Security"],
                ["XRAY2-", "CVE-2020-2000554", "Custom issue 2", "Everything will fall apart if you use this binary", "Security"],
                ["XRAY3-", "CVE-2021-2001325", "Custom issue 3", "Never use the binary with this issue", "Security"],
                ["XRAY4-", "CVE-2019-2005843", "Custom issue 4", "Beware of this zip file", "Security"],
                ["XRAY5-", "CVE-2018-2003578", "Bad license 0", "A very important license issue", "License"],
                ["XRAY6-", "CVE-2021-2001234", "Bad license 1", "A very important license issue", "License"],
                ["XRAY7-", "CVE-2020-2002548", "Bad license 2", "A very important license issue", "License"],
                ["XRAY8-", "CVE-2015-2003256", "Bad license 3", "A very important license issue", "License"],
                ["XRAY9-", "CVE-2018-2008753", "Bad license 4", "A very important license issue", "License"]

        }
    }

}