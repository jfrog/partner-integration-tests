#!/bin/bash

if [ -z "$1" ]
then
  echo "No CURL parameters submitted!"
  exit 1
fi
RT_USERNAME=$1
RT_PASSWORD=$2
IP_ADDR=$3
LICENSE1=$4
LICENSE2=$5
LICENSE3=$6

echo "### Add licenses ###"
curl -X POST -u ${RT_USERNAME}:${RT_PASSWORD} \
  http://${IP_ADDR}/artifactory/api/system/licenses \
  -H 'Cache-Control: no-cache' \
  -H 'Content-Type: application/json' \
  -d '[
        {
          "licenseKey": "'${LICENSE1}'"
        },
        {
          "licenseKey": "'${LICENSE2}'"
        },
        {
          "licenseKey": "'${LICENSE3}'"
        }
      ]'