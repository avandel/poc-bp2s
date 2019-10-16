#! /bin/sh

interval="$SYNC_INTERVAL"
port="$SFTP_SVC_SERVICE_PORT"
ip="$SFTP_SVC_SERVICE_HOST"
user="$SFTP_USER"
password="$SFTP_PASSWORD"
sftpKey="$SFTP_PK"
path="$SFTP_PATH"
baseurl="$STORAGE_SVC_SERVICE_HOST:$STORAGE_SVC_SERVICE_PORT"
accesskeyid="$S3_ACCESS_KEY_ID"
secretkey="$S3_SECRET_KEY"
targetbucket="$S3_BUCKET"
kafkaBootstrapServers="$KAFKA_SVC_SERVICE_HOST:$KAFKA_SVC_SERVICE_PORT"
kafkaTopic="$KAFKA_TOPIC"

java $JVM_OPTS -jar sftp-poll-assembly-0.1.jar $user $password $ip $port $path $targetbucket $accesskeyid $secretkey $baseurl $interval $kafkaBootstrapServers $kafkaTopic $sftpKey
