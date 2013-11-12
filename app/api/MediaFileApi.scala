package fr.lium
package api

import fr.lium.util.FileUtils
import fr.lium.model.{MediaFile, Uploaded, Status}
import fr.lium.tables.MediaFiles
import fr.lium.model.Conversions._
import org.apache.commons.io.{FileUtils => ApacheFileUtils}

import java.io.File

import scala.util.{ Try, Success, Failure }

import scala.slick.session.Database
import scala.slick.driver.SQLiteDriver.simple._
import Database.threadLocalSession

case class MediaFileApi(
    baseDirectory: File,
    audioFileBasename: String,
    database: Database) {

  /** Take care of registering a new file into the system
    *
    * @param sourceFile The source file to register into the system
    * @param newFileName Rename the source file using this extension
    * @param move If the sourceFile should be moved or only copied
    */
  def createFile(sourceFile: File, fileExtension: Option[String], move: Boolean = true): Try[MediaFile] = {

    val fileName = audioFileBasename + fileExtension.getOrElse("")

    def registerFile(sourceFile: File, toFile: File, move: Boolean): Try[File] = {
      if(move) {
        FileUtils.moveFileToFile(sourceFile, toFile)
      } else {
        Try{
          ApacheFileUtils.copyFile(sourceFile, toFile)
          toFile
        }
      }
    }

    database.withSession {
      for {
        //TODO: give a better audio file name than just appending .wav
        audioFile <- Try(MediaFiles.autoInc.insert((fileName, Uploaded, fileName + ".wav")))
        id <- audioFile.id asTry badArg("Fail to get autoinc id from DB")
        dir = new File(baseDirectory + File.separator + id).mkdir();
        moved <- registerFile(sourceFile, new File(baseDirectory + File.separator + id + File.separator + fileName), move)
      } yield MediaFile(audioFile.id, fileName)

    }

  }
  def getFileById(id: Int): Try[MediaFile] = {

    database.withSession {

      val dir = new File(baseDirectory + File.separator + id + File.separator)

      if (dir.exists && dir.isDirectory) {
        MediaFiles.findById(id)
      } else {
        Failure(new Exception("MediaFile directory doesn't exist"))
      }
    }

  }
}