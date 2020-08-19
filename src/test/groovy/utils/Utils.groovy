package utils

import java.text.SimpleDateFormat


class Utils {
    def getDateAsString(){
        def today = new Date()
        def sdf = new SimpleDateFormat("yyyy-MM-dd")
        return sdf.format(today)
    }

}
