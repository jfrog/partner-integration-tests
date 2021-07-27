package utils

import org.testng.Assert

import java.security.DigestInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import static io.restassured.RestAssured.given


class Utils {
    static def getUTCdate(){
        TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
        def today = new Date()
        def sdf = new SimpleDateFormat("yyyy-MM-dd")
        return sdf.format(today)

    }

    static def getLocaldate(){
        def today = new Date()
        def sdf = new SimpleDateFormat("yyyy-MM-dd")
        return sdf.format(today)

    }

    static def verifySplunkDate(date){
        if(date.endsWith("00:00")) {
            Assert.assertTrue((date.substring(0, 10)) == getUTCdate())
        } else {
            Assert.assertTrue((date.substring(0, 10)) == getLocaldate())
        }
    }

    static def dockerPullImage(image){
        def pull = "docker pull ${image}".execute()
        pull.waitForProcessOutput(System.out, System.err)
        Assert.assertTrue(pull.exitValue().equals(0))
    }

    static def dockerLogin(username, password, dockerURL){
        def proc = "docker login -u=${username} -p=${password} ${dockerURL}".execute()
        proc.waitForProcessOutput(System.out, System.err)
        Assert.assertTrue(proc.exitValue().equals(0))
    }

    static def dockerGenerateImages(repos, numberOfImages, image, dockerURL){
        for(repo in repos) {

            for (int i = 1; i <= numberOfImages; i++) {
                def tag = "docker tag ${image} ${dockerURL}/${repo}/${image}${i}:1.${i}".execute()
                tag.waitForProcessOutput(System.out, System.err)
                Assert.assertTrue(tag.exitValue().equals(0))
            }
            for (int i = 1; i <= numberOfImages; i++) {
                def push = "docker push ${dockerURL}/${repo}/${image}${i}:1.${i}".execute()
                push.waitForProcessOutput(System.out, System.err)
                Assert.assertTrue(push.exitValue().equals(0))
            }
//            for (int i = 1; i <= numberOfImages; i++) {
//                def push = "docker pull ${dockerURL}/${repo}/${image}${i}:1.${i}".execute()
//                push.waitForProcessOutput(System.out, System.err)
//                Assert.assertTrue(push.exitValue().equals(0))
//            }
        }
    }

    static def generateMD5(File file){
        file.withInputStream {
            new DigestInputStream(it, MessageDigest.getInstance('MD5')).withStream {
                it.eachByte {}
                it.messageDigest.digest().encodeHex() as String
            }
        }
    }

    static def generateSHA1(File file){
        file.withInputStream {
            new DigestInputStream(it, MessageDigest.getInstance('Sha1')).withStream {
                it.eachByte {}
                it.messageDigest.digest().encodeHex() as String
            }
        }
    }

    static def generateSHA256(File file){
        file.withInputStream {
            new DigestInputStream(it, MessageDigest.getInstance('Sha-256')).withStream {
                it.eachByte {}
                it.messageDigest.digest().encodeHex() as String
            }
        }
    }

    static def getHostIPv4(){
        final URL whatismyip2 = new URL("https://wtfismyip.com/text");
        final BufferedReader in2 = new BufferedReader(new InputStreamReader(
                whatismyip2.openStream()))
        final String ip2 = in2.readLine()
        return ip2
    }

    /**
     * Create an artifact with a random SHA256 in {@code ./src/test/resources/repositories/} <br>
     * Example usage: {@code Utils.createArtifact("artifact_1.zip")}
     *
     * @param name of the artifact. Do not include the directory.
     */
    static def createArtifact(String filename){
        ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream("./src/test/resources/repositories/${filename}"))
        zipFile.putNextEntry(new ZipEntry("content.txt"))
        Random random = new Random()
        byte[] buffer = random.nextLong().toString().getBytes() // random string
        zipFile.write(buffer, 0, buffer.length)
        zipFile.closeEntry()
        zipFile.close()
    }

}
