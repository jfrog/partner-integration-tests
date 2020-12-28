if [ -z "$1" ]
then
  echo "No CURL parameters submitted!"
  exit 1
fi
RT_USERNAME=$1
RT_PASSWORD=$2
IP_ADDR=$3

echo "### Accept EULA for JCR distrubution ###"
OUT=$(curl -XPOST -vu ${RT_USERNAME}:${RT_PASSWORD} http://${IP_ADDR}/artifactory/ui/jcr/eula/accept 2>&1)
    echo "$OUT"
    if [[ "$OUT" =~ (ERROR) ]]
      then
       echo "Accept EULA call failed!"
       exit 1
    fi