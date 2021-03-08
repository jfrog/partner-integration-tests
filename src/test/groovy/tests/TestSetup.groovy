package tests

import org.yaml.snakeyaml.Yaml

class TestSetup {

    protected static final Map config = new Yaml().load(new FileReader("./src/test/resources/testenv.yaml"))
    protected final protocol = System.env.RT_PROTOCOL ?: config.artifactory.protocol
    protected final ip = System.env.RT_URL ?: config.artifactory.external_ip
    protected final distribution = System.env.RT_DISTRIBUTION ?: config.artifactory.distribution
    protected final username = System.env.RT_USERNAME ?: config.artifactory.rt_username
    protected final password = System.env.RT_PASSWORD ?: config.artifactory.rt_password
    protected final dockerURL = System.env.RT_DOCKER_URL ?: config.artifactory.url
    protected final artifactoryBaseURL = "${protocol}${ip}"

    protected final splunkProtocol = System.env.SPLUNK_PROTOCOL ?: config.splunk.protocol
    protected final splunkUri = System.env.SPLUNK_URI ?: config.splunk.url
    protected final splunkPort = System.env.SPLUNK_PORT ?: config.splunk.port
    protected final splunk_username = System.env.SPLUNK_USERNAME ?: config.splunk.username
    protected final splunk_password = System.env.SPLUNK_PASSWORD ?: config.splunk.password
    // Port must be added for Splunk
    protected final splunkBaseURL = "${splunkProtocol}${splunkUri}" + ":" + "${splunkPort}"

    protected final prometheusProtocol = System.env.PROMETHEUS_PROTOCOL ?: config.prometheus.protocol
    protected final prometheusUri = System.env.PROMETHEUS_URI ?: config.prometheus.url
    protected final prometheusPort = System.env.PROMETHEUS_PORT ?: config.prometheus.port
    protected final prometheus_username = System.env.PROMETHEUS_USERNAME ?: config.prometheus.username
    protected final prometheus_password = System.env.PROMETHEUS_PASSWORD ?: config.prometheus.password
    protected final prometheusBaseURL = "${prometheusProtocol}${prometheusUri}" + ":" + "${prometheusPort}"

    protected final datadogProtocol = System.env.DATADOG_PROTOCOL ?: config.datadog.protocol
    protected final datadogUri = System.env.DATADOG_URI ?: config.datadog.url
    protected final datadogPort = System.env.DATADOG_PORT ?: config.datadog.port
    protected final datadog_username = System.env.DATADOG_USERNAME ?: config.datadog.username
    protected final datadog_password = System.env.DATADOG_PASSWORD ?: config.datadog.password
    protected final datadogApiKey = System.env.DATADOG_API_KEY ?: config.datadog.api_key
    protected final datadogApplicationKey = System.env.DATADOG_APPLICATION_KEY ?: config.datadog.application_key
    protected final datadogBaseURL = "${datadogProtocol}${datadogUri}"


}
