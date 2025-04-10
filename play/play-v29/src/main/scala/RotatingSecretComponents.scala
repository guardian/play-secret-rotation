package com.gu.play.secretrotation

import com.typesafe.scalalogging.Logger

trait RotatingSecretComponents extends SecretComponents {
  {
    val logger = Logger[RotatingSecretComponents]
    secretStateSupplier.snapshot()
    logger.info("Successfully fetched application secret")
  }
}
