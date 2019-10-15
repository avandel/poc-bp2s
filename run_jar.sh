#! /bin/sh

interval="$SYNC_INTERVAL"
port="$BP2S_SFTP_SVC_SERVICE_PORT"
ip="$BP2S_SFTP_SVC_SERVICE_HOST"
user="$SFTP_USER"
password="$SFTP_PASSWORD"
sftpKey="$SFTP_PK"
path="$SFTP_PATH"
baseurl="$BP2S_STORAGE_SVC_SERVICE_HOST:$BP2S_STORAGE_SVC_SERVICE_PORT"
accesskeyid="$S3_ACCESS_KEY_ID"
secretkey="$S3_SECRET_KEY"
targetbucket="$S3_BUCKET"
kafkaBootstrapServers="$BP2S_KAFKA_SVC_SERVICE_HOST:$BP2S_KAFKA_SVC_SERVICE_PORT"
kafkaTopic="$KAFKA_TOPIC"
sftpPk="$SFTP_PK"

echo java -jar sftp-poll-assembly-0.1.jar $user $password $ip $port $path $targetbucket $accesskeyid $secretkey $baseurl $interval $kafkaBootstrapServers $kafkaTopic $sftpPk

java -jar sftp-poll-assembly-0.1.jar $user $password $ip $port $path $targetbucket $accesskeyid $secretkey $baseurl $interval $kafkaBootstrapServers $kafkaTopic $sftpPk