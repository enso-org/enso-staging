package org.enso.editions.repository

import io.circe._
import io.circe.syntax.EncoderOps
import org.enso.editions.EditionName

/** The Edition Repository manifest, which lists all editions that the
  * repository provides.
  */
case class Manifest(editions: Seq[EditionName])

object Manifest {
  object Fields {
    val editions = "editions"
  }

  /** A [[Decoder]] instance for parsing [[Manifest]]. */
  implicit val decoder: Decoder[Manifest] = { json =>
    for {
      editions <- json.get[Seq[EditionName]](Fields.editions)
    } yield Manifest(editions)
  }

  /** An [[Encoder]] instance for serializing [[Manifest]]. */
  implicit val encoder: Encoder[Manifest] = { manifest =>
    Json.obj(Fields.editions -> manifest.editions.asJson)
  }

  /** The name of the manifest file that should be present at the root of
    * editions repository.
    */
  val filename = "manifest.yaml"
}
