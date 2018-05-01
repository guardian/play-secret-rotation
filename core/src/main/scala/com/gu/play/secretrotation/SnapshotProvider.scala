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

  // make sure we don't cache the secret state too long, we need to be ready to use a new secret when issued
  private val cache = CacheBuilder.newBuilder
    .expireAfterWrite(transitionTiming.usageDelay.dividedBy(2).getSeconds, SECONDS)
    .build(new CacheLoader[Null, SnapshotProvider] { def load(key: Null): SnapshotProvider = loadState() })

  override def snapshot(): SecretsSnapshot = cache.get(null).snapshot()

  def loadState(): SnapshotProvider
}



