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

  implicit val system = ActorSystem("actor-system-bp2s")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val identity = SftpIdentity.createRawSftpIdentity(sftpKey.getBytes())
  val sftpCredentials = FtpCredentials.create(sftpUser, "")
  val sftpAddress = InetAddress.getByName(sftpIp)

  val sftpSettings = SftpSettings
    .create(sftpAddress)
    .withPort(sftpPort)
    .withCredentials(sftpCredentials)
    .withSftpIdentity(identity)
    .withStrictHostKeyChecking(false)

  implicit val ws: StandaloneAhcWSClient = StandaloneAhcWSClient()
  val s3: WSS3 = S3(s3User, s3Password, "http", s3Host)

  val producerSettings: ProducerSettings[String, String] = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(kafkaBootstrapServers)

  Source.tick(0.seconds, intervalPoll.seconds, Done).runForeach { _ =>
    Sftp
      .ls(sftpPath, sftpSettings)
      .filter(ftpFile => ftpFile.isFile && !ftpFile.name.endsWith(".lock"))
      .alsoTo(Sftp.move(ftpFile => s"${ftpFile.path}.lock", sftpSettings))
      .mapAsync(parallelism = 1) { ftpFile =>
        val lockedFtpFile = ftpFile.copy(path = s"${ftpFile.path}.lock")
        val data: Source[ByteString, _] = Sftp.fromPath(lockedFtpFile.path, sftpSettings)
        for {
          bucketRef <- { // get-or-create
            val ref = s3.bucket(bucketName)
            ref.create(failsIfExists = false).map(_ => ref)
          }
          storageObj = bucketRef.obj(ftpFile.name)
          _ <- data
            .alsoTo(storageObj.put[ByteString])
            .runWith(Sink.ignore)
        } yield (lockedFtpFile, Message(storageObj.bucket, storageObj.name))
      }
      .alsoTo(Sftp.remove(sftpSettings).contramap(_._1))
      .map { case (_, message) => new ProducerRecord[String, String](kafkaTopic, message.jsonify) }
      .runWith(Producer.plainSink(producerSettings))
  }

}