name := "TwitterSim"

version := "1.0"

scalaVersion := "2.11.2"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"


libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-actors" % "2.11.2",
  "org.scala-lang" % "scala-swing" % "2.10.2", 
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.github.scala-incubator.io" % "scala-io-core_2.11" % "0.4.3",
  "com.typesafe.akka" %% "akka-remote" % "2.3.6",
  "io.spray" %% "spray-routing" % "1.3.1",
  "io.spray" %% "spray-client" % "1.3.1",
  "io.spray" %% "spray-testkit" % "1.3.1" % "test",
  "org.json4s" %% "json4s-native" % "3.2.10"
  )

