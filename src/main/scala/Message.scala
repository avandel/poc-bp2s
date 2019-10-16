case class Message(
                    bucket: String,
                    fileName: String
                  ) {
  def jsonify: String = s""" {"bucket": $bucket, "fileName": $fileName} """
}