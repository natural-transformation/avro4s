package com.sksamuel.avro4s.cats

import com.sksamuel.avro4s.AvroSchema
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.sksamuel.avro4s.{SchemaFor, Encoder, Decoder}

class CatsTest extends AnyWordSpec with Matchers {

  "avro4s-cats" should {
    "generate schema for cats.data.Validated" in {
      case class Test(maybeValid: cats.data.Validated[String, Int])
      val shcema = summon[SchemaFor[Test]].schema
      println(s"schema:\n${shcema.toString(true)}")

      val encoder = summon[Encoder[Test]]
      val decoder = summon[Decoder[Test]]

      val validExample = Test(cats.data.Validated.Valid(1))
      val encodedValidExample = encoder.encode(shcema)(validExample)
      
      println(s"encodedValidExample: $encodedValidExample")
      
      val decodedValidExample = decoder.decode(shcema)(encodedValidExample)

      println(s"decodedValidExample: $decodedValidExample")

      val invalidExample = Test(cats.data.Validated.Invalid("error"))
      val encodedInvalidExample = encoder.encode(shcema)(invalidExample)

      println(s"encodedInvalidExample: $encodedInvalidExample")

      val decodedInvalidExample = decoder.decode(shcema)(encodedInvalidExample)

      println(s"decodedInvalidExample: $decodedInvalidExample")

    }
  }
}

