package com.sksamuel.avro4s.github

import com.sksamuel.avro4s.AvroSchema
import org.apache.avro.JsonProperties
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class NestedRecordDefaultBugTest extends AnyFunSuite with Matchers {

  test("support nested record defaults containing option fields") {
    val schema = AvroSchema[NestedRecordDefaultBugResponse]
    val summaryDefault = schema.getField("summary").defaultVal().asInstanceOf[java.util.Map[String, AnyRef]]
    val scoresDefault = summaryDefault.get("scores").asInstanceOf[java.util.Map[String, AnyRef]]

    summaryDefault.get("reviewCount") shouldBe java.lang.Integer.valueOf(0)
    scoresDefault.get("averageScore") shouldBe JsonProperties.NULL_VALUE
  }
}

final case class NestedRecordDefaultBugResponse(
  summary: NestedRecordDefaultBugSummary = NestedRecordDefaultBugSummary.empty
)

final case class NestedRecordDefaultBugSummary(
  reviewCount: Int,
  scores: NestedRecordDefaultBugScores
)

object NestedRecordDefaultBugSummary {
  val empty: NestedRecordDefaultBugSummary =
    NestedRecordDefaultBugSummary(0, NestedRecordDefaultBugScores.empty)
}

final case class NestedRecordDefaultBugScores(averageScore: Option[Double])

object NestedRecordDefaultBugScores {
  val empty: NestedRecordDefaultBugScores = NestedRecordDefaultBugScores(None)
}
