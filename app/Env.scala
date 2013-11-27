package fr.lium

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.Config
import play.api.{ Application, Play }
import java.io.File

import fr.lium.api.{AudioFileApi, MediaFileApi, TranscriptionApi, WordApi}
import fr.lium.actor.{DiarizationActor, SoundConvertorActor}

import scala.slick.session.Database

final class Env(
    config: Config,
    actorSystem: ActorSystem) {

  val ffmpegBin = new File(config.getString("lium.ffmpegBin"))
  val spkDiarizationJar = config.getString("lium.spk.diarizationJar")
  val glpsolBin = config.getString("lium.glpsolBin")

  lazy val mediaFileApi = new MediaFileApi(
    baseDirectory = new File(config.getString("lium.baseDir")),
    audioFileBasename = config.getString("lium.audioFileBasename"),
    database,
    Some(soundConvertorActor)
  )

  lazy val audioFileApi = new AudioFileApi(
    baseDirectory = new File(config.getString("lium.baseDir")),
    audioFileBasename = config.getString("lium.audioFileBasename"),
    database
  )

  lazy val wordApi = new WordApi(config.getString("lium.fileEncoding"))

  lazy val transcriptionApi = new TranscriptionApi(wordApi, database)

  lazy val databaseName = config.getString("lium.databaseName")
  lazy val database: Database = Database.forURL(
    config.getString("lium.databaseUrl") format databaseName,
    driver = config.getString("lium.databaseDriver"))

  val diarizationActor: ActorRef = actorSystem.actorOf(Props(new DiarizationActor(
    spkDiarizationJar,
    glpsolBin,
    config.getString("lium.spk.pmsGmm"),
    config.getString("lium.spk.silenceGmm"),
    config.getString("lium.spk.genderGmm"),
    config.getString("lium.spk.ubmIv"),
    config.getString("lium.spk.efrIv"),
    config.getString("lium.spk.covIv"),
    config.getString("lium.spk.tvIv")
  )), name = "diarizationActor")
  val soundConvertorActor: ActorRef = actorSystem.actorOf(Props(new SoundConvertorActor(ffmpegBin, database, diarizationActor)), name = "soundConvertorActor")
}

object Env {

  lazy val current = new Env(
    config = appConfig,
    actorSystem = appSystem)

  private def appConfig: Config = withApp(_.configuration.underlying)

  private def appSystem = withApp { implicit app ⇒
    play.api.libs.concurrent.Akka.system
  }
  private def withApp[A](op: Application ⇒ A): A =
    Play.maybeApplication.map(op).get
}
