package com.sksamuel.avro4s.cats

import com.sksamuel.avro4s.{SchemaFor, Encoder, Decoder}
import com.sksamuel.avro4s.{AvroSchema, AvroInputStream, AvroOutputStream, Decoder, Encoder, SchemaFor}

import org.apache.avro.Schema
import org.apache.avro.util.ByteBufferInputStream
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import scala.jdk.CollectionConverters.SeqHasAsJava
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


class CatsTest extends AnyWordSpec with Matchers {

  "avro4s-cats" should {
    "encode and decode cats.data.Validated" in {
      case class Test(maybeValid: Validated[String, Int])
      val shcema = summon[SchemaFor[Test]].schema
      println(s"schema:\n${shcema.toString(true)}")

      val encoder = summon[Encoder[Test]]
      val decoder = summon[Decoder[Test]]

      val validExample = Test(Validated.Valid(1))
      val encodedValidExample = encoder.encode(shcema)(validExample)
      
      println(s"encodedValidExample: $encodedValidExample")
      
      val decodedValidExample = decoder.decode(shcema)(encodedValidExample)

      println(s"decodedValidExample: $decodedValidExample")

      decodedValidExample should equal(validExample)

      val invalidExample = Test(Validated.Invalid("error"))
      val encodedInvalidExample = encoder.encode(shcema)(invalidExample)

      println(s"encodedInvalidExample: $encodedInvalidExample")

      val decodedInvalidExample = decoder.decode(shcema)(encodedInvalidExample)

      println(s"decodedInvalidExample: $decodedInvalidExample")

      decodedInvalidExample should equal(invalidExample)

    }

    "encode and decode cats.data.ValidatedNel" in {
      // case class Test(maybeValid: ValidatedNel[String, Int])
      case class CatsValidatedNelTest(errorsOrNum: ValidatedNel[String, Int])

      val shcema = summon[SchemaFor[CatsValidatedNelTest]].schema
      println(s"schema:\n${shcema.toString(true)}")

      val encoder = summon[Encoder[CatsValidatedNelTest]]
      val decoder = summon[Decoder[CatsValidatedNelTest]]

      val validExample = CatsValidatedNelTest(Validated.Valid(1))
      val encodedValidExample = encoder.encode(shcema)(validExample)
      
      println(s"encodedValidExample: $encodedValidExample")
      
      val decodedValidExample = decoder.decode(shcema)(encodedValidExample)

      println(s"decodedValidExample: $decodedValidExample")

      decodedValidExample should equal(validExample)

      val invalidExample = CatsValidatedNelTest(Validated.Invalid(NonEmptyList.one("error1")))
      val encodedInvalidExample = encoder.encode(shcema)(invalidExample)

      println(s"encodedInvalidExample: $encodedInvalidExample")

      val decodedInvalidExample = decoder.decode(shcema)(encodedInvalidExample)

      println(s"decodedInvalidExample: $decodedInvalidExample")

      decodedInvalidExample should equal(invalidExample)

    }

     "encode and decode user case class using either" in {
      case class EitherTest(either: Either[String, Int])

      val schemaFor: SchemaFor[EitherTest] = summon[SchemaFor[EitherTest]]
      println(s"schema for EitherTest is ${schemaFor.schema.toString(true)}")

      val serialized    = AvroUtil.toBin(EitherTest(Right(1)))
      val derserialized = AvroUtil.fromBin[EitherTest](serialized)

      derserialized shouldEqual EitherTest(Right(1))

     }

    "encode and decode user case class using cats data" in {
      case class CatsValidatedTest(errorsOrNum: Validated[String, Int])

      val schemaFor: SchemaFor[CatsValidatedTest] = summon[SchemaFor[CatsValidatedTest]]
      println(s"schema for CatsValidatedTest is ${schemaFor.schema.toString(true)}")

      val serialized    = AvroUtil.toBin(CatsValidatedTest(Validated.Invalid("error1")))
      val derserialized = AvroUtil.fromBin[CatsValidatedTest](serialized)

      derserialized shouldEqual CatsValidatedTest(Validated.Invalid("error1"))
    }
    
  }
}

object AvroUtil {

  def toBin[T: Encoder: SchemaFor](o: T): Array[Byte] = {

    val output = new ByteArrayOutputStream
    val avro   = AvroOutputStream.binary[T].to(output).build()
    avro.write(o)
    avro.close()

    output.toByteArray
  }

  def fromBin[T: Decoder](bytes: Array[Byte], writerSchema: Schema): T = {

    val is     = AvroInputStream.binary[T].from(bytes).build(writerSchema)
    val record = is.iterator.next()
    is.close()

    record
  }

  def fromBin[T <: AnyRef](bytes: Array[Byte])(using schema: SchemaFor[T], decoder: Decoder[T]): T = {

    val is     = AvroInputStream.binary[T].from(bytes).build(schema.schema)
    val record = is.iterator.next()
    is.close()

    record
  }

  def writeByteBuffer[T: Encoder: SchemaFor](o: T, buf: ByteBuffer): Unit = {
    val stream = ByteBufferBackedOutputStream(buf)
    val avro   = AvroOutputStream.binary[T].to(stream).build()
    avro.write(o)
    avro.close()
  }

  def readByteBuffer[T: Decoder](buf: ByteBuffer, writerSchema: Schema): T = {
    val is     = AvroInputStream.binary[T].from(new ByteBufferInputStream(List(buf).asJava)).build(writerSchema)
    val record = is.iterator.next()
    is.close()

    record
  }

  case class ByteBufferBackedOutputStream(buf: ByteBuffer) extends OutputStream {
    @throws[IOException]
    def write(b: Int): Unit =
      buf.put(b.toByte)

    @throws[IOException]
    override def write(bytes: Array[Byte], off: Int, len: Int): Unit =
      buf.put(bytes, off, len)
  }

}

