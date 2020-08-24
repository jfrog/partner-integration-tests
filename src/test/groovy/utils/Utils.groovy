package utils

import org.testng.Assert

import java.text.SimpleDateFormat


class Utils {
    def getUTCdate(){
        TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
        def today = new Date()
        def sdf = new SimpleDateFormat("yyyy-MM-dd")
        return sdf.format(today)

    }

    def getLocaldate(){
        def today = new Date()
        def sdf = new SimpleDateFormat("yyyy-MM-dd")
        return sdf.format(today)

    }

    def verifySplunkDate(date){
        if(date.endsWith("00:00")) {
            Assert.assertTrue((date.substring(0, 10)) == getUTCdate())
        } else {
            Assert.assertTrue((date.substring(0, 10)) == getLocaldate())
        }
    }

    def dockerPullImage(image){
        def pull = "docker pull ${image}".execute()
        pull.waitForProcessOutput(System.out, System.err)
        Assert.assertTrue(pull.exitValue().equals(0))
    }

    def dockerLogin(username, password, dockerURL){
        def proc = "docker login -u=${username} -p=${password} ${dockerURL}".execute()
        proc.waitForProcessOutput(System.out, System.err)
        Assert.assertTrue(proc.exitValue().equals(0))
    }

    def dockerGenerateImages(repos, numberOfImages, image, dockerURL){
        for(repo in repos) {

            for (int i = 1; i <= numberOfImages; i++) {
                def tag = "docker tag ${image} ${dockerURL}/${repo}/busybox${i}:1.${i}".execute()
                tag.waitForProcessOutput(System.out, System.err)
                Assert.assertTrue(tag.exitValue().equals(0))
            }
            for (int i = 1; i <= numberOfImages; i++) {
                def push = "docker push ${dockerURL}/${repo}/busybox${i}:1.${i}".execute()
                push.waitForProcessOutput(System.out, System.err)
                Assert.assertTrue(push.exitValue().equals(0))
            }
        }
    }

}
