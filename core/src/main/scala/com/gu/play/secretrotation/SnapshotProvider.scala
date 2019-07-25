package com.gu.play.secretrotation

import com.github.blemale.scaffeine.{LoadingCache, Scaffeine}
import com.typesafe.scalalogging.Logger

import scala.concurrent.duration._


trait SecretsSnapshot {
  def secrets: Phase[String]

  def description: String

  /**
    * @param decodingFunc a function that attempts to decode a value using the provided secret
    * @param conclusiveDecode If true, no further attempts to decode with other secrets will be made,
    *                      and the decoding result will be returned, wrapped in an Option.
    *                      Note that a decode can be conclusive without the decoded value being *valid* -
    *                      eg. the value may have been signed with a valid secret but expired due to it's
    *                      own expiration constraints, or even maliciously signed with an unacceptable
    *                      algorithm (eg a weak algorithm, even 'none' : https://tools.ietf.org/html/rfc7519#section-6.1 )
    */
  def decode[T](decodingFunc: String => T, conclusiveDecode: T => Boolean): Option[T]
}

trait SnapshotProvider {
  val logger = Logger[SnapshotProvider]

  def snapshot(): SecretsSnapshot
}

trait CachingSnapshotProvider extends SnapshotProvider {
  val transitionTiming: TransitionTiming

  private val anyKey = new Object // would love to use Unit or something, it just wouldn't compile

  // make sure we don't cache the secret state too long, we need to be ready to use a new secret when issued
  private val cache: LoadingCache[Object, SnapshotProvider] = Scaffeine()
      .expireAfterWrite(transitionTiming.usageDelay.dividedBy(2).toMillis.millis)
      .build(_ => loadState())

  override def snapshot(): SecretsSnapshot = cache.get(anyKey).snapshot()

  def loadState(): SnapshotProvider
}



