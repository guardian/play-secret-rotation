// replace the conventional main artifact with an uber-jar
addArtifact(Compile / packageBin / artifact, assembly)

assembly / assemblyMergeStrategy := {
  {
    case PathList("META-INF", _ @ _*) => MergeStrategy.discard
    case _ => MergeStrategy.first
  }
}