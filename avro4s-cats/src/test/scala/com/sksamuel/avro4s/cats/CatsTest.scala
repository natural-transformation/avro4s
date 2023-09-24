package com.sksamuel.avro4s.cats

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import com.sksamuel.avro4s.AvroSchema
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.sksamuel.avro4s.{SchemaFor, Encoder, Decoder}

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
  }
}

