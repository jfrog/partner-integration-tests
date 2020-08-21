package utils

import java.text.SimpleDateFormat


class Utils {
    def getUTCdate(){
        TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
        def today = new Date()
        def sdf = new SimpleDateFormat("yyyy-MM-dd")
        return sdf.format(today)

    }

}
