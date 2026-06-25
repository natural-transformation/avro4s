package com.sksamuel.avro4s.record.decoder

import com.sksamuel.avro4s.{Avro4sDecodingException, AvroSchema, AvroTransient, Decoder}
import org.apache.avro.generic.GenericData
import org.apache.avro.util.Utf8
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class TransientDecoderTest extends AnyFunSuite with Matchers {

  case class TransientFoo(a: String, @AvroTransient b: Option[String])
  case class TransientBar(name: String)
  case class TransientFooWithDefault(
    a: String,
    @AvroTransient(useDefault = true) b: TransientBar = TransientBar("default"))
  case class TransientFooWithoutDefault(a: String, @AvroTransient(useDefault = true) b: TransientBar)

  test("decoder should populate transient fields with None") {
    val schema = AvroSchema[TransientFoo]
    val record = new GenericData.Record(schema)
    record.put("a", new Utf8("hello"))
    Decoder[TransientFoo].decode(schema).apply(record) shouldBe TransientFoo("hello", None)
  }

  test("decoder should populate transient fields with default case class value") {
    val schema = AvroSchema[TransientFooWithDefault]
    val record = new GenericData.Record(schema)
    record.put("a", new Utf8("hello"))
    Decoder[TransientFooWithDefault].decode(schema).apply(record) shouldBe TransientFooWithDefault("hello")
  }

  test("decoder should reject transient fields configured to use a missing default value") {
    val schema = AvroSchema[TransientFooWithoutDefault]
    val record = new GenericData.Record(schema)
    record.put("a", new Utf8("hello"))
    val thrown = intercept[Avro4sDecodingException] {
      Decoder[TransientFooWithoutDefault].decode(schema).apply(record)
    }
    thrown.getMessage should include(
      """@AvroTransient(useDefault = true) requires field "b" to define a default value""")
  }
}
