package com.gu.play.secretrotation

import com.google.common.cache.{CacheBuilder, CacheLoader}
import play.api.http.SecretConfiguration

import scala.concurrent.duration._


trait SecretsSnapshot {
  def secrets: Phase[SecretConfiguration]

  def description: String

  def decode[T](decodingFunc: SecretConfiguration => T, successfulDecode: T => Boolean): Option[T]
}

trait SnapshotProvider {
  def snapshot(): SecretsSnapshot
}

trait CachingSnapshotProvider extends SnapshotProvider {
  val transitionTiming: TransitionTiming

  private val anyKey = new Object // would love to use Unit or something, it just wouldn't compile

  // make sure we don't cache the secret state too long, we need to be ready to use a new secret when issued
  private val cache = CacheBuilder.newBuilder
    .expireAfterWrite(transitionTiming.usageDelay.dividedBy(2).getSeconds, SECONDS)
    .build(new CacheLoader[Object, SnapshotProvider] { def load(key: Object): SnapshotProvider = loadState() })

  override def snapshot(): SecretsSnapshot = cache.get(anyKey).snapshot()

  def loadState(): SnapshotProvider
}



