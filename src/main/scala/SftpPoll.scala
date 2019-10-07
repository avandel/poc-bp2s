import java.net.InetAddress
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

import akka.actor.ActorSystem
import akka.stream.alpakka.ftp.scaladsl.Sftp
import akka.stream.alpakka.ftp.{ FtpCredentials, SftpIdentity, SftpSettings }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.ByteString
import com.zengularity.benji.s3.{ S3, WSS3 }
import play.api.libs.ws.BodyWritable
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.ahc.StandaloneAhcWSClient


object SftpPoll extends App {

  def putToS3[A: BodyWritable](storage: WSS3, bucketName: String, objName: String, data: => Source[A, _])(implicit m: Materializer): Future[Unit] = {
    implicit def ec: ExecutionContext = m.executionContext

    for {
      bucketRef <- { // get-or-create
        val ref = storage.bucket(bucketName)
        ref.create(failsIfExists = false).map(_ => ref)
      }
      storageObj = bucketRef.obj(objName)
      _ <- data runWith storageObj.put[A]
    } yield ()
  }

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

  val res = Sftp
    .ls("/incoming", settings)
    .filter(ftpFile => ftpFile.isFile)
    .mapAsyncUnordered(parallelism = 2) { ftpFile =>

      val data: Source[ByteString, _] = Sftp.fromPath(ftpFile.path, settings)
      putToS3[ByteString](s3, "my-bucket", ftpFile.name, data)
        .map { _ =>
          ftpFile.path
        }
    }
    .runWith(Sink.seq)

  val seq = Await.result(res, Duration.Inf)
  seq.foreach(println)

}
