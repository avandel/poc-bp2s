#! /bin/sh

interval="$SYNC_INTERVAL"
port="$SFTP_PORT"
ip="$SFTP_ADDR"
user="$SFTP_USER"
password="$SFTP_PASSWORD"
path="$SFTP_PATH"
baseurl="$S3_BASE_URL"
accesskeyid="$S3_ACCESS_KEY_ID"
secretkey="$S3_SECRET_KEY"
targetbucket="$S3_BUCKET"
kafkaTopic="$KAFKA_TOPIC"


echo $ip
echo java -jar sftp-poll-assembly-0.1.jar $user $password $ip $port $path $targetbucket $accesskeyid $secretkey $baseurl $interval $kafkaTopic

java -jar sftp-poll-assembly-0.1.jar $user $password $ip $port $path $targetbucket $accesskeyid $secretkey $baseurl $interval $kafkaTopic