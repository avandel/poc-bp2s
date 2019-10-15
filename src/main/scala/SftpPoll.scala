import java.net.InetAddress

import scala.concurrent.duration._

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.ActorMaterializer
import akka.stream.alpakka.ftp.scaladsl.Sftp
import akka.stream.alpakka.ftp.{ FtpCredentials, SftpIdentity, SftpSettings }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.zengularity.benji.s3.{ S3, WSS3 }
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import java.time.Instant


object SftpPoll extends App {

  val sftpUser = args(0)
  val sftpPassword = args(1)
  val sftpIp = args(2)
  val sftpPort = args(3).toInt
  val sftpPath = args(4)
  val bucketName = args(5)
  val s3User = args(6)
  val s3Password = args(7)
  val s3Host = args(8)
  val intervalPoll = args(9).toInt
  val kafkaBootstrapServers = args(10)
  val kafkaTopic = args(11)
  val sftpKey =  new String(java.util.Base64.getDecoder.decode(args(12)))

  implicit val system = ActorSystem("actor-system-sftp")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val credentials = FtpCredentials.create(sftpUser, sftpPassword)
  val address = InetAddress.getByName(sftpIp)

  val identity = SftpIdentity.createRawSftpIdentity(sftpKey.getBytes())

  val settings = SftpSettings
    .create(address)
    .withPort(sftpPort)
    .withCredentials(credentials)
    .withSftpIdentity(identity)
    .withStrictHostKeyChecking(false)

  implicit val ws: StandaloneAhcWSClient = StandaloneAhcWSClient()
  val s3: WSS3 = S3(s3User, s3Password, "http", s3Host)

  val kafkaProducerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(kafkaBootstrapServers)

  Source.tick(0.seconds, intervalPoll.seconds, Done).runForeach { _ =>
    println(s"${Instant.now()} ---> start polling")
    Sftp
      .ls(sftpPath, settings)
      .filter(ftpFile => ftpFile.isFile && !ftpFile.name.endsWith(".transferred"))
      .mapAsyncUnordered(parallelism = 2) { ftpFile =>
        val data: Source[ByteString, _] = Sftp.fromPath(ftpFile.path, settings)

        for {
          bucketRef <- { // get-or-create
            val ref = s3.bucket(bucketName)
            ref.create(failsIfExists = false).map(_ => ref)
          }
          storageObj = bucketRef.obj(ftpFile.name)
          _ <- data
            .alsoTo(storageObj.put[ByteString])
            .runWith(Sftp.move(file => file.path + ".transferred", settings).contramap(_ => ftpFile))
        } yield Message(storageObj.bucket, storageObj.name)
      }.map { message => 
        println(s"${Instant.now()} ---> sending message $message to kafka topic $kafkaTopic")
        new ProducerRecord[String, String](kafkaTopic, message.jsonify)
      }
      .runWith(Producer.plainSink(kafkaProducerSettings))
  }
}
