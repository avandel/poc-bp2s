#! /bin/sh

interval="$SYNC_INTERVAL"
port="$SFTP_PORT"
ip="$SFTP_IP"
user="$SFTP_USER"
password="$SFTP_PASSWORD"
path="$SFTP_PATH"
baseurl="$S3_BASE_URL"
accesskeyid="$S3_ACCESS_KEY_ID"
secretkey="$S3_SECRET_KEY"
targetbucket="$S3_BUCKET"

echo $SYNC_INTERVAL
echo "$SYNC_INTERVAL"
echo ${interval}
echo java -jar sftp-poll-assembly-0.1.jar ${user} $password $ip $port $path $targetbucket $accesskeyid $secretkey $baseurl $interval

java -jar sftp-poll-assembly-0.1.jar $user $password $ip $port $path $targetbucket $accesskeyid $secretkey $baseurl $interval