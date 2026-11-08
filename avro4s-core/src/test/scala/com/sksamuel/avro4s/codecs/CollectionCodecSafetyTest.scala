package com.sksamuel.avro4s.codecs

import com.sksamuel.avro4s.*
import org.apache.avro.Schema
import org.apache.avro.util.Utf8
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters.*

class CollectionCodecSafetyTest extends AnyFunSuite with Matchers {
  private val intSchema = Schema.create(Schema.Type.INT)
  private val arraySchema = Schema.createArray(intSchema)
  private val mapSchema = Schema.createMap(intSchema)

  private def elements(value: AnyRef): List[Int] =
    value.asInstanceOf[java.util.Collection[Integer]].asScala.iterator.map(_.intValue).toList

  test("array, list, vector and sequence encoders retain order and duplicates at every size") {
    for (size <- List(0, 1, 32, 256)) {
      val input = List.tabulate(size)(i => 1000 + i % 7)
      val outputs = List(
        Encoder[Array[Int]].encode(arraySchema)(input.toArray),
        Encoder[List[Int]].encode(arraySchema)(input),
        Encoder[Vector[Int]].encode(arraySchema)(input.toVector),
        Encoder[Seq[Int]].encode(arraySchema)(input.toVector))
      outputs.foreach(output => elements(output) shouldBe input)
    }
  }

  test("encoding an array takes a snapshot and never reuses earlier result storage") {
    val encode = Encoder[Array[Int]].encode(arraySchema)
    val input = Array(1001, 2002, 3003)
    val first = encode(input)
    input(0) = 9009
    val second = encode(input)
    elements(first) shouldBe List(1001, 2002, 3003)
    elements(second) shouldBe List(9009, 2002, 3003)
  }

  test("encoded collections remain read-only Java lists") {
    val outputs = List(
      Encoder[Array[Int]].encode(arraySchema)(Array(1001, 2002)),
      Encoder[List[Int]].encode(arraySchema)(List(1001, 2002)),
      Encoder[Seq[Int]].encode(arraySchema)(Vector(1001, 2002)),
      Encoder[Vector[Int]].encode(arraySchema)(Vector(1001, 2002)),
      Encoder[Set[Int]].encode(arraySchema)(Set(1001, 2002)))
    outputs.foreach { output =>
      val list = output.asInstanceOf[java.util.List[Integer]]
      intercept[UnsupportedOperationException](list.set(0, 3003))
      intercept[UnsupportedOperationException](list.add(3003))
      intercept[UnsupportedOperationException](list.remove(0))
      list.size() shouldBe 2
    }
  }

  test("custom encoders preserve sequence multiplicity and existing Set collision semantics") {
    given Encoder[Int] = Encoder(value => java.lang.Integer.valueOf(value % 2))
    val input = List(1, 3, 2, 4)
    elements(Encoder[List[Int]].encode(arraySchema)(input)) shouldBe List(1, 1, 0, 0)
    elements(Encoder[Array[Int]].encode(arraySchema)(input.toArray)) shouldBe List(1, 1, 0, 0)
    val set = input.toSet
    elements(Encoder[Set[Int]].encode(arraySchema)(set)) shouldBe set.map(_ % 2).toList
  }

  test("collection encoders preserve null elements returned by custom codecs") {
    given Encoder[Int] = Encoder(_ => null)
    val outputs = List(
      Encoder[Array[Int]].encode(arraySchema)(Array(1, 2)),
      Encoder[List[Int]].encode(arraySchema)(List(1, 2)),
      Encoder[Vector[Int]].encode(arraySchema)(Vector(1, 2)))
    outputs.foreach { output =>
      output.asInstanceOf[java.util.List[AnyRef]].asScala.toList shouldBe List(null, null)
    }
  }

  test("all collection decoders accept Java collections, Scala iterables and arrays") {
    val decodeList = Decoder[List[Int]].decode(arraySchema)
    val decodeSeq = Decoder[Seq[Int]].decode(arraySchema)
    val decodeVector = Decoder[Vector[Int]].decode(arraySchema)
    val decodeSet = Decoder[Set[Int]].decode(arraySchema)
    val decodeArray = Decoder[Array[Int]].decode(arraySchema)
    for (size <- List(0, 1, 32, 256)) {
      val expected = List.tabulate(size)(i => 1000 + i % 7)
      val inputs: List[Any] = List(expected.asJava, expected, expected.toVector, expected.toArray)
      for (input <- inputs) {
        decodeList(input) shouldBe expected
        decodeSeq(input) shouldBe expected
        decodeVector(input) shouldBe expected.toVector
        decodeSet(input) shouldBe expected.toSet
        decodeArray(input).toList shouldBe expected
      }
    }
  }

  test("custom decoders preserve sequence multiplicity and mapped Set input semantics") {
    given Decoder[Int] = new Decoder[Int] {
      def decode(schema: Schema): Any => Int = value => value.asInstanceOf[Int] % 2
    }
    val values = List(1, 3, 2, 4)
    val decode = Decoder[Vector[Int]].decode(arraySchema)
    val decodeArray = Decoder[Array[Int]].decode(arraySchema)
    decode(values.asJava) shouldBe Vector(1, 1, 0, 0)
    decode(values.toArray) shouldBe Vector(1, 1, 0, 0)
    decode(values.toSet).sorted shouldBe Vector(0, 1)
    decode(scala.collection.mutable.LinkedHashSet.from(values)).sorted shouldBe Vector(0, 1)
    decodeArray(values.toSet).sorted.toList shouldBe List(0, 1)
    // Java Collection's Scala adapter maps as an Iterable, even for a Java Set.
    val javaSet = new java.util.LinkedHashSet[Integer]()
    values.foreach(value => javaSet.add(Integer.valueOf(value)))
    decode(javaSet) shouldBe Vector(1, 1, 0, 0)
    decodeArray(javaSet).toList shouldBe List(1, 1, 0, 0)
  }

  test("decoding Scala maps as iterables keeps the original mapping semantics") {
    val input = Map(1 -> 1001, 2 -> 2002, 3 -> 3003)
    given Decoder[Int] = new Decoder[Int] {
      def decode(schema: Schema): Any => Int = value => value.asInstanceOf[(Int, Int)]._2
    }
    Decoder[Vector[Int]].decode(arraySchema)(input) shouldBe input.map(_._2).toVector
    Decoder[Array[Int]].decode(arraySchema)(input).toVector shouldBe input.map(_._2).toVector
  }

  test("Scala Sets wrapped as Java collections retain mapping collision semantics") {
    given Decoder[Int] = new Decoder[Int] {
      def decode(schema: Schema): Any => Int = value => value.asInstanceOf[Int] % 2
    }
    // asScala unwraps asJavaCollection back to the original Scala Set.
    val input: java.util.Collection[Int] = Set(1, 3, 2, 4).asJavaCollection
    Decoder[List[Int]].decode(arraySchema)(input).sorted shouldBe List(0, 1)
    Decoder[Vector[Int]].decode(arraySchema)(input).sorted shouldBe Vector(0, 1)
    Decoder[Array[Int]].decode(arraySchema)(input).sorted.toList shouldBe List(0, 1)
  }

  test("decoding snapshots Java collection contents independently of subsequent reads") {
    val input = new java.util.ArrayList[Integer]()
    input.add(1001)
    input.add(2002)
    val decode = Decoder[Vector[Int]].decode(arraySchema)
    val first = decode(input)
    input.set(0, 3003)
    val second = decode(input)
    first shouldBe Vector(1001, 2002)
    second shouldBe Vector(3003, 2002)
  }

  test("map codecs retain every entry for empty, small and resizing-sized maps") {
    val encode = Encoder[Map[String, Int]].encode(mapSchema)
    val decode = Decoder[Map[String, Int]].decode(mapSchema)
    for (size <- List(0, 1, 12, 13, 32, 256)) {
      val expected = (0 until size).iterator.map(i => s"key-$i" -> (1000 + i)).toMap
      val encoded = encode(expected).asInstanceOf[java.util.Map[String, Integer]]
      encoded.size() shouldBe size
      decode(encoded) shouldBe expected
    }
  }

  test("map decoding supports Utf8 keys and custom value codecs without retaining input storage") {
    given Decoder[Int] = new Decoder[Int] {
      def decode(schema: Schema): Any => Int = value => value.asInstanceOf[Int] + 10
    }
    val input = new java.util.LinkedHashMap[Utf8, Integer]()
    input.put(new Utf8("阿夫罗"), 1001)
    input.put(new Utf8("ç"), 2002)
    val decode = Decoder[Map[String, Int]].decode(mapSchema)
    val first = decode(input)
    input.put(new Utf8("阿夫罗"), 3003)
    first shouldBe Map("阿夫罗" -> 1011, "ç" -> 2012)
    decode(input) shouldBe Map("阿夫罗" -> 3013, "ç" -> 2012)
  }

  test("map encoding uses custom value codecs and keeps outputs independent") {
    given Encoder[Int] = Encoder(value => java.lang.Integer.valueOf(value + 10))
    val encode = Encoder[Map[String, Int]].encode(mapSchema)
    val first = encode(Map("key" -> 1001)).asInstanceOf[java.util.Map[String, Integer]]
    val second = encode(Map("key" -> 2002)).asInstanceOf[java.util.Map[String, Integer]]
    first.get("key").intValue shouldBe 1011
    second.get("key").intValue shouldBe 2012
  }
}
