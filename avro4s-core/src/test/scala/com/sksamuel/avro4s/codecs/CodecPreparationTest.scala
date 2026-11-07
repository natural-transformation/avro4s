package com.sksamuel.avro4s.codecs

import com.sksamuel.avro4s.*
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.avro.generic.IndexedRecord
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Using

object CodecPreparationTest {
  case class Pair(first: Int, second: Int)

  // A serializable codec may allocate process-local state when it is prepared.
  final class PreparedState(val offset: Int)

  final class StatefulEncoder extends Encoder[Int] {
    var preparations = 0
    def encode(schema: Schema): Int => AnyRef = {
      preparations += 1
      val state = new PreparedState(10)
      value => ImmutableRecord(schema, Vector(value + state.offset))
    }
  }

  final class StatefulDecoder extends Decoder[Int] {
    var preparations = 0
    def decode(schema: Schema): Any => Int = {
      preparations += 1
      val state = new PreparedState(10)
      value => value.asInstanceOf[IndexedRecord].get(0).asInstanceOf[Int] - state.offset
    }
  }
}

class CodecPreparationTest extends AnyFunSuite with Matchers {
  import CodecPreparationTest.*

  private def schema: Schema = SchemaBuilder.record("PreparedInt")
    .fields().requiredInt("value").endRecord()

  private def roundTrip[A](value: A): A = {
    val bytes = new ByteArrayOutputStream()
    Using.resource(new ObjectOutputStream(bytes))(_.writeObject(value))
    Using.resource(new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray))) {
      _.readObject().asInstanceOf[A]
    }
  }

  test("ToRecord prepares its custom encoder once and still encodes every input") {
    var preparations = 0
    var valuesEncoded = 0
    val encoder = new Encoder[Int] {
      def encode(schema: Schema): Int => AnyRef = {
        preparations += 1
        value => {
          valuesEncoded += 1
          ImmutableRecord(schema, Vector(value))
        }
      }
    }
    val converter = ToRecord[Int](schema)(using encoder)
    // Keep preparation lazy, including the timing of custom codec failures.
    preparations shouldBe 0
    converter.to(1001).get(0).asInstanceOf[Int] shouldBe 1001
    converter.to(2002).get(0).asInstanceOf[Int] shouldBe 2002
    preparations shouldBe 1
    valuesEncoded shouldBe 2
  }

  test("FromRecord prepares its custom decoder once and still decodes every input") {
    var preparations = 0
    var valuesDecoded = 0
    val decoder = new Decoder[Int] {
      def decode(schema: Schema): Any => Int = {
        preparations += 1
        value => {
          valuesDecoded += 1
          value.asInstanceOf[IndexedRecord].get(0).asInstanceOf[Int]
        }
      }
    }
    val recordSchema = schema
    val converter = FromRecord[Int](recordSchema)(using decoder)
    preparations shouldBe 0
    converter.from(ImmutableRecord(recordSchema, Vector(1001))) shouldBe 1001
    converter.from(ImmutableRecord(recordSchema, Vector(2002))) shouldBe 2002
    preparations shouldBe 1
    valuesDecoded shouldBe 2
  }

  test("codec preparation belongs to each wrapper and its supplied schema") {
    val firstSchema = schema
    val secondSchema = SchemaBuilder.record("OtherPreparedInt")
      .fields().requiredInt("value").endRecord()
    var encoderSchemas = Vector.empty[Schema]
    var decoderSchemas = Vector.empty[Schema]
    val encoder = new Encoder[Int] {
      def encode(schema: Schema): Int => AnyRef = {
        encoderSchemas :+= schema
        value => ImmutableRecord(schema, Vector(value))
      }
    }
    val decoder = new Decoder[Int] {
      def decode(schema: Schema): Any => Int = {
        decoderSchemas :+= schema
        value => {
          val record = value.asInstanceOf[IndexedRecord]
          require(record.getSchema eq schema)
          record.get(0).asInstanceOf[Int]
        }
      }
    }
    val firstTo = ToRecord[Int](firstSchema)(using encoder)
    val secondTo = ToRecord[Int](secondSchema)(using encoder)
    val firstFrom = FromRecord[Int](firstSchema)(using decoder)
    val secondFrom = FromRecord[Int](secondSchema)(using decoder)
    for (value <- List(1001, 2002)) {
      firstFrom.from(firstTo.to(value)) shouldBe value
      secondFrom.from(secondTo.to(value)) shouldBe value
    }
    encoderSchemas shouldBe Vector(firstSchema, secondSchema)
    decoderSchemas shouldBe Vector(firstSchema, secondSchema)
  }

  test("shared wrappers prepare once when first used concurrently") {
    val encoderPreparations = new AtomicInteger()
    val decoderPreparations = new AtomicInteger()
    val encoder = new Encoder[Int] {
      def encode(schema: Schema): Int => AnyRef = {
        encoderPreparations.incrementAndGet()
        value => ImmutableRecord(schema, Vector(value))
      }
    }
    val decoder = new Decoder[Int] {
      def decode(schema: Schema): Any => Int = {
        decoderPreparations.incrementAndGet()
        value => value.asInstanceOf[IndexedRecord].get(0).asInstanceOf[Int]
      }
    }
    val recordSchema = schema
    val to = ToRecord[Int](recordSchema)(using encoder)
    val from = FromRecord[Int](recordSchema)(using decoder)
    val start = new CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(4)
    try {
      val results = (1001 to 1016).map { value =>
        executor.submit(new Callable[Int] {
          override def call(): Int = {
            require(start.await(10, TimeUnit.SECONDS), "Timed out waiting for test start")
            from.from(to.to(value))
          }
        })
      }
      start.countDown()
      results.map(_.get(20, TimeUnit.SECONDS)) shouldBe (1001 to 1016)
      encoderPreparations.get() shouldBe 1
      decoderPreparations.get() shouldBe 1
    } finally {
      start.countDown()
      executor.shutdownNow()
    }
  }

  test("derived wrappers round trip through Java serialization before first use") {
    val recordSchema = AvroSchema[Pair]
    val to = roundTrip(ToRecord[Pair](recordSchema))
    val from = roundTrip(FromRecord[Pair](recordSchema))
    from.from(to.to(Pair(1001, 2002))) shouldBe Pair(1001, 2002)
  }

  test("derived wrappers round trip through Java serialization after first use") {
    val recordSchema = AvroSchema[Pair]
    val to = ToRecord[Pair](recordSchema)
    val from = FromRecord[Pair](recordSchema)
    from.from(to.to(Pair(1001, 2002))) shouldBe Pair(1001, 2002)
    val restoredTo = roundTrip(to)
    val restoredFrom = roundTrip(from)
    restoredFrom.from(restoredTo.to(Pair(3003, 4004))) shouldBe Pair(3003, 4004)
  }

  test("ToRecord excludes prepared state from serialization and prepares lazily after restore") {
    val encoder = new StatefulEncoder
    val to = ToRecord[Int](schema)(using encoder)
    to.to(1001).get(0).asInstanceOf[Int] shouldBe 1011
    encoder.preparations shouldBe 1
    // Serialize both objects together to retain their shared reference for the assertion.
    val (restored, restoredEncoder) = roundTrip((to, encoder))
    restoredEncoder.preparations shouldBe 1
    restored.to(2002).get(0).asInstanceOf[Int] shouldBe 2012
    restored.to(3003).get(0).asInstanceOf[Int] shouldBe 3013
    restoredEncoder.preparations shouldBe 2
    encoder.preparations shouldBe 1
  }

  test("FromRecord excludes prepared state from serialization and prepares lazily after restore") {
    val decoder = new StatefulDecoder
    val recordSchema = schema
    val from = FromRecord[Int](recordSchema)(using decoder)
    from.from(ImmutableRecord(recordSchema, Vector(1011))) shouldBe 1001
    decoder.preparations shouldBe 1
    val (restored, restoredDecoder) = roundTrip((from, decoder))
    restoredDecoder.preparations shouldBe 1
    restored.from(ImmutableRecord(recordSchema, Vector(2012))) shouldBe 2002
    restored.from(ImmutableRecord(recordSchema, Vector(3013))) shouldBe 3003
    restoredDecoder.preparations shouldBe 2
    decoder.preparations shouldBe 1
  }

  test("ToRecord retries failed preparation and caches the first successful function") {
    var attempts = 0
    val encoder = new Encoder[Int] {
      def encode(schema: Schema): Int => AnyRef = {
        attempts += 1
        if (attempts == 1) throw new IllegalStateException("preparation failed")
        value => ImmutableRecord(schema, Vector(value))
      }
    }
    val to = ToRecord[Int](schema)(using encoder)
    attempts shouldBe 0
    intercept[IllegalStateException](to.to(1001)).getMessage shouldBe "preparation failed"
    to.to(2002).get(0).asInstanceOf[Int] shouldBe 2002
    to.to(3003).get(0).asInstanceOf[Int] shouldBe 3003
    attempts shouldBe 2
  }

  test("FromRecord retries failed preparation and caches the first successful function") {
    var attempts = 0
    val decoder = new Decoder[Int] {
      def decode(schema: Schema): Any => Int = {
        attempts += 1
        if (attempts == 1) throw new IllegalStateException("preparation failed")
        value => value.asInstanceOf[IndexedRecord].get(0).asInstanceOf[Int]
      }
    }
    val recordSchema = schema
    val from = FromRecord[Int](recordSchema)(using decoder)
    attempts shouldBe 0
    intercept[IllegalStateException](from.from(ImmutableRecord(recordSchema, Vector(1001))))
      .getMessage shouldBe "preparation failed"
    from.from(ImmutableRecord(recordSchema, Vector(2002))) shouldBe 2002
    from.from(ImmutableRecord(recordSchema, Vector(3003))) shouldBe 3003
    attempts shouldBe 2
  }
}
