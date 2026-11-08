package com.sksamuel.avro4s.schema

import com.sksamuel.avro4s.*
import org.apache.avro.{Schema, SchemaParseException}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayOutputStream

object SchemaBoundaryFixtures {
  @AvroErasedName case class Erased[T](value: T)
  sealed trait Shape
  case class NumberShape(value: Erased[Int]) extends Shape
  case class TextShape(value: Erased[String]) extends Shape
  case class Tuples(first: (Int, String), second: (String, Int))
  case class ShortHash(@AvroFixed(2) hash: String)
  case class LongHash(@AvroFixed(4) hash: String)
  case class Hashes(first: ShortHash, second: LongHash)
  case class EqualNames(first: Erased[Int], second: Erased[Int])
}

class SchemaBoundaryTest extends AnyFunSuite with Matchers {
  import SchemaBoundaryFixtures.*

  test("schema export rejects conflicting names across sealed trait branches") {
    intercept[SchemaParseException](AvroSchema[Shape])
  }

  test("schema export rejects conflicting names under tuple and collection roots") {
    intercept[SchemaParseException](AvroSchema[(Erased[Int], Erased[String])])
    intercept[SchemaParseException](AvroSchema[List[(Erased[Int], Erased[String])]])
    intercept[SchemaParseException](AvroSchema[Option[Shape]])
  }

  test("equal named definitions round trip through schema JSON") {
    val schema = AvroSchema[(EqualNames, List[EqualNames])]
    new Schema.Parser().parse(schema.toString) shouldBe schema
  }

  private def binaryRoundTrip[T: SchemaFor: Encoder: Decoder](value: T): Unit = {
    val schema = SchemaFor[T].schema
    val bytes = new ByteArrayOutputStream()
    val output = AvroOutputStream.binary[T].to(bytes).build()
    output.write(value)
    output.close()
    val input = AvroInputStream.binary[T].from(bytes.toByteArray).build(schema, schema)
    try input.iterator.toList shouldBe List(value)
    finally input.close()
  }

  test("binary streams retain support for heterogeneous tuples without renaming") {
    binaryRoundTrip(Tuples((1, "one"), ("two", 2)))
    val schema = SchemaFor[Tuples].schema
    schema.getField("first").schema().getFullName shouldBe "scala.Tuple2"
    intercept[SchemaParseException](AvroSchema[Tuples])
  }

  test("binary streams retain support for same-named fixed fields of different sizes") {
    binaryRoundTrip(Hashes(ShortHash("ab"), LongHash("abcd")))
    intercept[SchemaParseException](AvroSchema[Hashes])
  }

  test("data containers reject conflicting explicit schemas before writing a header") {
    // Build this graph independently of derivation to cover the explicit-schema API.
    def named(t: Schema.Type): Schema = {
      val record = Schema.createRecord("Same", null, "boundary", false)
      record.setFields(java.util.List.of(new Schema.Field("value", Schema.create(t))))
      record
    }
    val schema = Schema.createRecord("Root", null, "boundary", false)
    schema.setFields(java.util.List.of(
      new Schema.Field("first", named(Schema.Type.INT)),
      new Schema.Field("second", named(Schema.Type.STRING))))
    val bytes = new ByteArrayOutputStream()
    intercept[SchemaParseException] {
      AvroOutputStream.data(schema, Encoder[Tuples]).to(bytes).build()
    }
    bytes.size() shouldBe 0
  }
}
