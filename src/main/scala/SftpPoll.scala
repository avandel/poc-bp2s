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

  implicit val system = ActorSystem("actor-system-sftp")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher


  val identity = SftpIdentity.createFileSftpIdentity("/Users/aurelienvandel/.ssh/keys/ssh_host_rsa_key")
  val credentials = FtpCredentials.create("aurelienvandel", "password")
  val address = InetAddress.getByName("192.168.64.2")

  val settings = SftpSettings
    .create(address)
    .withPort(31436)
    .withCredentials(credentials)
    //.withSftpIdentity(identity)
    .withStrictHostKeyChecking(false)

  implicit val ws: StandaloneAhcWSClient = StandaloneAhcWSClient()
  val s3: WSS3 = S3("minio", "minio123", "http", "192.168.64.2:31984")

  Source.tick(0.seconds, 10.seconds, Done).runForeach { _ =>
    Sftp
      .ls("/incoming", settings)
      .filter(ftpFile => ftpFile.isFile && !ftpFile.name.endsWith(".transferred"))
      .mapAsyncUnordered(parallelism = 2) { ftpFile =>

        val data: Source[ByteString, _] = Sftp.fromPath(ftpFile.path, settings)

        for {
          bucketRef <- { // get-or-create
            val ref = s3.bucket("poc-bp2s")
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
