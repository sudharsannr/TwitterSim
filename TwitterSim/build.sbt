name := "TwitterSim"

version := "1.0"

scalaVersion := "2.11.2"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-actors" % "2.11.2",
  "org.scala-lang" % "scala-swing" % "2.10.2", 
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.github.scala-incubator.io" % "scala-io-core_2.11" % "0.4.3",
  "io.spray" % "spray-routing" % "1.2.0",
  "io.spray" % "spray-can" % "1.2.0",
  "com.typesafe.akka" %% "akka-remote" % "2.3.6"
  )

libraryDependencies += "org.apache.commons" % "commons-collections4" % "4.0"