package tests

import org.yaml.snakeyaml.Yaml

class TestSetup {
    Yaml yaml = new Yaml()
    protected final configFile = new File("./src/test/resources/testenv.yaml")
    protected final config = yaml.load(configFile.text)
    protected final artifactoryBaseURL = "${protocol}${config.artifactory.external_ip}"
    protected final artifactoryPingURL = artifactoryBaseURL
    protected final protocol = config.artifactory.protocol
    protected final ip = System.env.URL ?: config.artifactory.external_ip


}
