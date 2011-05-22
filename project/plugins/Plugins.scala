import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  //Add Eclipsify plugin
  lazy val eclipse = "de.element34" % "sbt-eclipsify" % "0.7.0"
  val akkaRepo = "Akka Repo" at "http:akka.io/repository"
  //Add Akka plugin
  val akkaPlugin = "se.scalablesolutions.akka" % "akka-sbt-plugin" % "1.1"
}
