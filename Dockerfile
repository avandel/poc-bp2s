FROM openjdk:8-jre-alpine

RUN mkdir -p /opt/app
WORKDIR /opt/app

COPY ./run_jar.sh target/scala-2.12/sftp-poll-assembly-0.1.jar ./

ENTRYPOINT ["./run_jar.sh"]