package com.gu.play.secretrotation

/**
 * To avoid having to specify any application secret value
 * and to enforce that you aren't using the application secret anywhere.
 */
trait NoApplicationSecretComponents extends SecretComponents {

  final val secretStateSupplier: SnapshotProvider = new SnapshotProvider {
    override def snapshot(): SecretsSnapshot =
      throw new SecurityException("You are using the Play application secret when you aren't expecting it to be used!")
  }
}
