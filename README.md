## Partnership Engineering Integration Testing Framework

The purpose of this framework is to test different Artifactory configurations deployed by various Partnership Engineering solutions.
It can be used in the CI-CD process and in a single manual test run.
NOTE: the test purpose is to test if deployment was correct and Artifactory functioning as expected. 
test will delete all existing repositories and create a list of new repos. 

Test suites include Artifactory Pro, JCR and X-ray tests as well as Terraform Provider and Data analytics tests.    

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


### Run as a docker container
Build the image or pull the image ``partnership-partner-integration-tests.jfrog.io/jfrog-tester``
Run the container with a set of environment variables:
```
docker run -it -e RT_URL=<your_artifactory_uri> -e RT_PROTOCOL=<http:// or https://> -e RT_USERNAME=<username> -e RT_PASSWORD=<password> partnership-partner-integration-tests.jfrog.io/jfrog-tester:0.0.1 <task_name>
```
NOTE: if default password needs to be changed, set NEW_RT_PASSWORD variable.
New password should meet security requirements.  
If the variable set, the password will be changed during a `common` test group run. 


### Tasks for Artifactory testing
```
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
- SSL setup for Docker repository tests

### Tasks for Terraform provider testing
```
terraform_provider_test
```
This test verifies the desired state of the Artifactory instance after user runs Terraform Provider.

Requirements: 
- Deployed Artifactory Pro instance
- Installed Terraform
- [Terraform Providers](https://github.com/jfrog/terraform-provider-artifactory)   

### Tasks for Data analytics integration testing
```
splunk_test
prometheus_test
datadog_test
```
Data analytics tests generate load on the Artifactory instance, then use data analytics platforms APIs to get the charts 
data and compare with expected result.

Requirements: 
- Deployed Artifactory Pro instance
- Deployed Log Analytics platform (Install JFrog app for Splunk)
- Fluentd running on each Artifactory and Xray node, connected to Log Analytics platform

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
