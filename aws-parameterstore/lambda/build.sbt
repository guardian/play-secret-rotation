// replace the conventional main artifact with an uber-jar
addArtifact(Compile / packageBin / artifact, assembly)

// see https://github.com/scalacenter/sbt-version-policy/issues/190
versionPolicyAssessCompatibility / skip := true

assembly / assemblyMergeStrategy := {
  {
    case PathList("META-INF", _ @ _*) => MergeStrategy.discard
    case _ => MergeStrategy.first
  }
}