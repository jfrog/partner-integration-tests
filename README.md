## Minimal Artifactory Functional Testing Framework

This test framework has been repurposed from the partner-integration-tests used internally in JFrog by the Partnership Engineering team.

**NOTE:** the test purpose is to test if deployment was correct and JFrog Deployment is functioning as expected and not for running  on a Production live JFrog Deployment. 
You can use this test framework **"as is"** and  it is not supported by JFrog.

It has the following customizations to the original partner-integration-tests :
a) It is upgraded to run with :

```
❯ gradle --version

Gradle 7.4.2
Kotlin:       1.5.31
Groovy:       3.0.9
Ant:          Apache Ant(TM) version 1.10.11 compiled on July 10 2021
JVM:          18 (Oracle Corporation 18+36-2087)
```

b) It does not delete any existing repositories that are already in artifactory. Instead it creates new local, remote and virtual repos ( name prefixed with “appbdd-” ) and after the test is done it deletes only these appbdd-* repos.

c) I disabled the setBaseURLTest() in src/test/groovy/tests/HealthCheckTest.groovy by changing the group name from "common" to "common-notneeded"

Though the Test suites include Artifactory Pro, JCR and Xray tests as well as some Data analytics tests , I tested only the Artifactory tests.    

### How to run it locally
Clone the repo. Open the file ```/src/test/resources/testenv.yaml``` and fill it with your environment values. Mandatory fields: 

`external_ip` - load balancer or Artifactory node IP address

`rt_username` - Artifactory username (`admin` by default)

`rt_password` - Artifactory password (`password` by default)

`url` fields is used in Docker tests only and must be a valid DNS name

Run Gradle wrapper to invoke Gradle task: 
```
./gradlew <task_name>
```
Test project can use environment variables to substitute values in testenv.yaml file. Check ```src/test/groovy/tests/TestSetup.groovy``` to see which variables are available. 
For examples of running the artifactory tests see "To rerun a test" section below.


### Tasks for Artifactory platform testing
```
artifactory_common
artifactory_ha_test_with_rmRepos
artifactory_rm_HARepos
artifactory_jcr_test
artifactory_ha_test
unified_test
artifactory_ha_docker_test
artifactory_jcr_docker_test
```
Docker tests require SSL setup which is not always needed or available.
For this reason they are moved to the separate tasks. 
unified_test task runs Artifactory Pro and Xray tests.  

Requirements: 
- Deployed Artifactory Pro or JCR instance
- Xray installed for ``unified_test``
- SSL setup for ``artifactory_ha_docker_test`` and ``artifactory_jcr_docker_test``

### Tasks for Data analytics integration testing
```
splunk_test
prometheus_test
datadog_test
```
Data analytics tests generate load on the Artifactory instance, then use data analytics platforms APIs to get the charts 
data and compare with expected result.

Requirements: 
- Deployed Artifactory Pro + Xray instance
- Deployed and connected Log Analytics platforms - Splunk with JFrog app, Datadog or Prometheus

### Adding new tests
We use Groovy and REST-Assured in our tests. TestNG is being used as a test management tool. 
New tests could be added in `/src/test/groovy/tests`
All tests should use steps, which is a collection of Artifactory or Xray API calls. 
Each new test class should be added to `testing.xml` file and should be assigned to at least one group - ```groups=["common","jcr","pro","docker"]```

### Gradle cleanup. Delete the folder:
```
 ~/.gradle/caches/
 ./gradlew clean
```

### To rerun a test :
1. First make sure gradlew can download the "**distributionUrl**" specified in gradle/wrapper/gradle-wrapper.properties. I used steps in https://stackoverflow.com/questions/68552674/gradle-distribution-remote-repo so  gradlew can resolve the **gradle-7.4.2-all.zip** from my Artifactory server  ( mytest.artifactory.com's gradle-dist generic remote repository).

The artifactory username and password to connect to  my Artifactory server is set in 
{user.home}/.gradle/gradle.properties as mentioned in https://stackoverflow.com/questions/45310011/how-do-i-provide-credentials-for-gradle-wrapper-without-embedding-them-in-my-pro

2. Next for gradle to connect to artifactory and resolve  the maven dependencies specified in the **build.gradle** you can set this in the **gradle.properties** as mentioned in https://stackoverflow.com/questions/22352475/upload-artifact-to-artifactory-using-gradle

3. Then run:
```
 ./gradlew clean
 ./gradlew artifactory_ha_test_with_rmRepos
 or
 ./gradlew artifactory_ha_test
```
**Note:** **artifactory_ha_test_with_rmRepos** will create and delete the appbdd-* repos used in the test 
vs
**artifactory_ha_test** test will create but not delete the appbdd-* repos used in the test.

The test  report is saved in partner-integration-tests/build/reports/tests/**artifactory_ha_test**/index.html or partner-integration-tests/build/reports/tests/**artifactory_ha_test_with_rmRepos**/classes/tests.RepositoryTest.html depending on the test you run
![image](https://user-images.githubusercontent.com/7613305/163461564-d4225f88-0449-4b64-8ff1-24f8454425e5.png)

![image](https://user-images.githubusercontent.com/7613305/163461626-78b5bbf2-5a33-4420-ab92-c2fbfce04fee.png)
