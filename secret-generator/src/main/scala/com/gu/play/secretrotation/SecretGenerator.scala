package com.gu.play.secretrotation

import java.security.SecureRandom

object SecretGenerator {

  /**
    * Copied from play.sbt.ApplicationSecretGenerator#generateSecret ...we could
    * have just introduced that as a dependency, but might as well reduce the
    * size of the AWS Lambda jar...
    * @return 64-character play secret
    */
  def generateSecret = {
    val random = new SecureRandom()

    (1 to 64).map { _ =>
      (random.nextInt(75) + 48).toChar
    }.mkString.replaceAll("\\\\+", "/")
  }
}
