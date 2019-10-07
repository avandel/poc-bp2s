name := "sftp-poll"

version := "0.1"

scalaVersion := "2.12.10"

val benjiVersion = "2.0.5"
val alpakkaVersion = "1.1.1"

libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-stream-alpakka-file" % alpakkaVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-ftp" % alpakkaVersion,
  "com.zengularity" %% "benji-s3" % benjiVersion
)