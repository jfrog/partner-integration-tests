package tests

import org.yaml.snakeyaml.Yaml

class TestSetup {
    Yaml yaml = new Yaml()
    protected final configFile = new File("./src/test/resources/testenv.yaml")
    protected final config = yaml.load(configFile.text)
    protected final protocol = System.env.RT_PROTOCOL ?: config.artifactory.protocol
    protected final ip = System.env.RT_URL ?: config.artifactory.external_ip
    protected final artifactoryBaseURL = "${protocol}${ip}"

}
