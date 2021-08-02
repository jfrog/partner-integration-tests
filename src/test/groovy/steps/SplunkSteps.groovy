package steps

import io.restassured.response.Response
import org.apache.commons.validator.routines.InetAddressValidator
import org.awaitility.Awaitility
import org.yaml.snakeyaml.Yaml

import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static io.restassured.RestAssured.given

class SplunkSteps {

    Yaml yaml = new Yaml()
    def configFile = new File("./src/test/resources/testenv.yaml")
    def config = yaml.load(configFile.text)
    def distribution = config.artifactory.distribution
    def repoSteps = new RepositorySteps()

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

    def waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, secondsToWait){
        Awaitility.await().atMost(secondsToWait, TimeUnit.SECONDS).until(() ->
                (getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)).then().
                        extract().statusCode() == 200)
    }

    def dockerCleanupCache(artifactoryBaseURL, username, password, repoPath, images){
        for(imageName in images) {
            def path = repoPath + imageName
            Response deleteDockerRepos = repoSteps.deleteItem(artifactoryBaseURL, username, password,
                    path)
            deleteDockerRepos.then().log().ifValidationFails().statusCode(204)
        }
    }

    def splunkSearchResults(splunk_username, splunk_password, splunkBaseURL, search_string){
        def searchID = getSplunkSearchID(splunk_username, splunk_password, splunkBaseURL, search_string)
        waitForTheResponse(splunk_username, splunk_password, splunkBaseURL, searchID, 120)
        return getSearchResults(splunk_username, splunk_password, splunkBaseURL, searchID)
    }

    def validateIPAddress(IPAddress){
        InetAddressValidator validator = InetAddressValidator.getInstance()
        if (validator.isValid(IPAddress)) {
            return true
        }
        else {
            return false
        }
    }

    static Map<String, Integer> getSeverities(Response response) {
        return response.jsonPath().getList("results").stream().collect(
                Collectors.toMap(
                        it -> it["severity"].toString(),
                        it -> Integer.parseInt(it["count"].toString()),
                        Integer::sum
                )
        )
    }

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

    static Map<String, Integer> getMatchedPolicyWatchCounts(Response response, String selector) {
        return response.jsonPath().getList("results").stream().collect(
                Collectors.toMap(
                        it -> {
                            def policy = it[selector].toString()
                            policy.contains("security") ? "security" : policy.substring(policy.lastIndexOf("_") + 1)
                        },
                        it -> Integer.parseInt(it["count"].toString()),
                        Integer::sum
                )
        )
    }

    static Map<String, Integer> getMatchedRuleCounts(Response response) {
        return response.jsonPath().getList("results").stream().collect(
                Collectors.toMap(
                        it -> {
                            def policy = it["matched_policies{}.rule"].toString()
                            policy.contains("security") ? "security" : policy.substring("License".length(), policy.lastIndexOf("Rule"))
                        },
                        it -> Integer.parseInt(it["count"].toString()),
                        Integer::sum
                )
        )
    }

    static Map<String, Integer> squashSeveritiesOverTime(Response response) {
        List<Map<String, String>> results = response.jsonPath().getList("results")
        def severities = new HashMap<String, Integer>()

        results.forEach {
            it.findAll {
                it.getKey().charAt(0) != '_' as char
            }.forEach {key, value ->
                severities.merge(key, Integer.parseInt(value), Integer::sum)
            }
        }
        return severities
    }

    static Map<String, Integer> getInfectedComponentCounts(Response response, String selector) {
        return response.jsonPath().getList("results").stream().collect(
                Collectors.toMap(
                        it -> {
                            def artifact = it[selector].toString()
                            artifact.substring(artifact.lastIndexOf("/") + 1)
                        },
                        it -> Integer.parseInt(it["count"].toString()),
                        Integer::sum
                )
        )
    }

    static Map<String, Integer> getExpectedComponentCounts(license_issues, security_issues) {
        def componentCounts = new HashMap<String, Integer>()
        license_issues.forEach { it ->
            it[3].forEach { artifactId ->
                def artifactName = XraySteps.artifactFormat(artifactId)
                componentCounts.put(artifactName, componentCounts.getOrDefault(artifactName, 0) + 1)
            }
        }

        security_issues.forEach { it ->
            it[6].forEach { artifactId ->
                def artifactName = XraySteps.artifactFormat(artifactId)
                componentCounts.put(artifactName, componentCounts.getOrDefault(artifactName, 0) + 1)
            }
        }
        return componentCounts
    }

}
