package com.sksamuel.avro4s.codecs

import com.sksamuel.avro4s.*
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.avro.generic.{GenericData, IndexedRecord}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

object RecordCodecSafetyTest {
  case class Pair(first: Int, second: Int)
  case class Nested(left: Pair, right: Pair)
  case class Empty()
  case class WithDefault(first: Int, second: Int = 99)
  case class Renamed(@AvroName("wire_value") value: Int,
                     @AvroTransient(useDefault = true) local: Int = 99)
}

class RecordCodecSafetyTest extends AnyFunSuite with Matchers {
  import RecordCodecSafetyTest.*

  private def record(schema: Schema, first: Int, second: Int): GenericData.Record = {
    val result = new GenericData.Record(schema)
    result.put("first", first)
    result.put("second", second)
    result
  }

  private def reversed(schema: Schema): Schema = SchemaBuilder
    .record(schema.getName).namespace(schema.getNamespace)
    .fields().requiredInt("second").requiredInt("first").endRecord()

  test("one prepared decoder accepts alternating field orders in either initial order") {
    val schema = AvroSchema[Pair]
    val reordered = reversed(schema)
    val originalRecord = record(schema, 1001, 2002)
    val reorderedRecord = record(reordered, 3003, 4004)
    for (startWithReordered <- List(false, true)) {
      val decode = Decoder[Pair].decode(schema)
      val inputs = if (startWithReordered)
        List(reorderedRecord -> Pair(3003, 4004), originalRecord -> Pair(1001, 2002))
      else List(originalRecord -> Pair(1001, 2002), reorderedRecord -> Pair(3003, 4004))
      for (_ <- 1 to 3; (input, expected) <- inputs) decode(input) shouldBe expected
    }
  }

  test("FromRecord remains safe when reused across different record schemas") {
    val schema = AvroSchema[Pair]
    val converter = FromRecord[Pair](schema)
    converter.from(record(schema, 1001, 2002)) shouldBe Pair(1001, 2002)
    converter.from(record(reversed(schema), 3003, 4004)) shouldBe Pair(3003, 4004)
    converter.from(record(schema, 5005, 6006)) shouldBe Pair(5005, 6006)
  }

  test("a cached decoder still applies defaults when a later record omits a field") {
    val schema = AvroSchema[WithDefault]
    val olderSchema = SchemaBuilder.record(schema.getName).namespace(schema.getNamespace)
      .fields().requiredInt("first").endRecord()
    val olderRecord = new GenericData.Record(olderSchema)
    olderRecord.put("first", 3003)
    val converter = FromRecord[WithDefault](schema)
    converter.from(record(schema, 1001, 2002)) shouldBe WithDefault(1001, 2002)
    converter.from(olderRecord) shouldBe WithDefault(3003, 99)
    converter.from(record(schema, 5005, 6006)) shouldBe WithDefault(5005, 6006)
  }

  test("record encoding follows the supplied schema's order rather than constructor order") {
    val schema = reversed(AvroSchema[Pair])
    val encode = Encoder[Pair].encode(schema)
    val encoded = encode(Pair(1001, 2002)).asInstanceOf[IndexedRecord]
    encoded.get(0).asInstanceOf[Int] shouldBe 2002
    encoded.get(1).asInstanceOf[Int] shouldBe 1001
    Decoder[Pair].decode(schema)(encoded) shouldBe Pair(1001, 2002)
  }

  test("previously encoded records retain independent field arrays, including nested records") {
    val schema = AvroSchema[Nested]
    val encode = Encoder[Nested].encode(schema)
    val decode = Decoder[Nested].decode(schema)
    val first = Nested(Pair(1001, 2002), Pair(3003, 4004))
    val second = Nested(Pair(5005, 6006), Pair(7007, 8008))
    val firstRecord = encode(first).asInstanceOf[ImmutableRecord]
    val secondRecord = encode(second).asInstanceOf[ImmutableRecord]
    decode(firstRecord) shouldBe first
    decode(secondRecord) shouldBe second
    firstRecord.get("left").asInstanceOf[IndexedRecord].get(0).asInstanceOf[Int] shouldBe 1001
    intercept[UnsupportedOperationException](firstRecord.put(0, null))
  }

  test("decoding reads primitive fields eagerly on each call") {
    val schema = AvroSchema[Pair]
    val decode = Decoder[Pair].decode(schema)
    val input = record(schema, 1001, 2002)
    val first = decode(input)
    input.put("first", 3003)
    val second = decode(input)
    first shouldBe Pair(1001, 2002)
    second shouldBe Pair(3003, 2002)
  }

  test("empty records and renamed fields with transient defaults round trip") {
    val emptySchema = AvroSchema[Empty]
    Decoder[Empty].decode(emptySchema)(Encoder[Empty].encode(emptySchema)(Empty())) shouldBe Empty()
    val schema = AvroSchema[Renamed]
    val encoded = Encoder[Renamed].encode(schema)(Renamed(1001, 123)).asInstanceOf[IndexedRecord]
    encoded.getSchema.getField("wire_value").pos() shouldBe 0
    encoded.get(0).asInstanceOf[Int] shouldBe 1001
    Decoder[Renamed].decode(schema)(encoded) shouldBe Renamed(1001, 99)
  }

  test("record codecs preserve custom field codecs") {
    given Encoder[Int] = Encoder(value => java.lang.Integer.valueOf(value + 10))
    given Decoder[Int] = new Decoder[Int] {
      def decode(schema: Schema): Any => Int = value => value.asInstanceOf[Int] - 10
    }
    val schema = AvroSchema[Pair]
    val encoded = Encoder[Pair].encode(schema)(Pair(1001, 2002)).asInstanceOf[IndexedRecord]
    encoded.get(0).asInstanceOf[Int] shouldBe 1011
    encoded.get(1).asInstanceOf[Int] shouldBe 2012
    Decoder[Pair].decode(schema)(encoded) shouldBe Pair(1001, 2002)
  }
}
