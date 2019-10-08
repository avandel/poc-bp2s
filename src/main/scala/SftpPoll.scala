import java.net.InetAddress
import scala.concurrent.duration._

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.ftp.scaladsl.Sftp
import akka.stream.alpakka.ftp.{ FtpCredentials, SftpIdentity, SftpSettings }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.zengularity.benji.s3.{ S3, WSS3 }
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

  args.foreach(println)

  implicit val system = ActorSystem("actor-system-sftp")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher


  //val identity = SftpIdentity.createFileSftpIdentity("/Users/aurelienvandel/.ssh/keys/ssh_host_rsa_key")
  val credentials = FtpCredentials.create(sftpUser, sftpPassword)
  val address = InetAddress.getByName(sftpIp)

  val settings = SftpSettings
    .create(address)
    .withPort(sftpPort)
    .withCredentials(credentials)
    //.withSftpIdentity(identity)
    .withStrictHostKeyChecking(false)

  implicit val ws: StandaloneAhcWSClient = StandaloneAhcWSClient()
  val s3: WSS3 = S3(s3User, s3Password, "http", s3Host)


  Source.tick(0.seconds, intervalPoll.seconds, Done).runForeach { _ =>
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
        } yield ()
      }
      .runWith(Sink.ignore)
  }
}
