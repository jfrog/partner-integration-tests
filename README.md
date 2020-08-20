## Partnership Engineering Integration Testing Framework

The purpose of this framework is to test different Artifactory configurations deployed by various Partnership Engineering solutions.
It can be used in the CI-CD process and in a single manual test run.

Test suites include Artifactory Pro, JCR and X-ray tests.   

### How to run it locally
Clone the repo. Open the file ```/src/test/resources/testenv.yaml``` and fill it with your environment values. Mandatory fields: 

`external_ip` - load balancer or Artifactory node IP address

`rt_username` - Artifactory username (`admin` by default)

`rt_password` - Artifactory password (`password` by default)

`url` fields is used in Docker tests only and must be valid DNS name

Run Gradle wrapper to invoke Gradle task: 
```
./gradlew <task_name>
```

### Tasks for Artifactory testing
```
artifactory_jcr_test
artifactory_ha_test
unified_test
artifactory_ha_docker_test
artifactory_jcr_docker_test
terraform_provider_test
splunk_test
```
### Tasks for Terraform provider testing
```
terraform_provider_test
```

### Tasks for Data analytics integration testing
```
splunk_test
```

Docker tests require SSL setup which is not always needed or available.
For this reason they are moved to the separate tasks. 

### Adding new tests
We use Groovy and REST-Assured in our tests. TestNG is being use as a test management tool. 
New tests could be added in `/src/test/groovy/tests`
All tests should use steps, which is a collection of Artifactory API calls. 
Each new test class should be added to `testing.xml` file and should be assigned to at least one group - ```groups=["common","jcr","pro","docker"]```

### Gradle cleanup. Delete the folder:
```
 ~/.gradle/caches/
 ./gradlew clean
```
