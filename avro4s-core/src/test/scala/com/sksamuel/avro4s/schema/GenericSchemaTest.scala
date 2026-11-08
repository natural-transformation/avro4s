package com.sksamuel.avro4s.schema

import java.util.UUID

import com.sksamuel.avro4s.{AvroErasedName, AvroName, AvroSchema}
import org.apache.avro.SchemaParseException
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

case class Thing1Id(id: Int) extends AnyVal
case class Thing2Id(id: UUID) extends AnyVal
case class Thing2(id: Thing2Id, name: String)
case class OuterId(id: UUID) extends AnyVal
case class InnerId(id: String) extends AnyVal
case class Inner[LEFT, RIGHT](
                               id: InnerId,
                               left: LEFT,
                               right: Option[RIGHT] = None
                             )
case class Outer[LEFT, RIGHT](
                               id: OuterId,
                               @AvroName("innerThing")
                               theInnerThing: Inner[LEFT, RIGHT]
                             )

class GenericSchemaTest extends AnyFunSuite with Matchers {

  case class Generic[T](t: T)
  case class SameGenericWithDifferentTypeArgs(gi: Generic[Int], gs: Generic[String])

  @AvroErasedName
  case class GenericDisabled[T](t: T)
  case class SameGenericWithDifferentTypeArgsDisabled(gi: GenericDisabled[Int], gs: GenericDisabled[String])

  test("support same generic with different type parameters") {
    val schema = AvroSchema[SameGenericWithDifferentTypeArgs]
    val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/generic.json"))
    schema.toString(true) shouldBe expected.toString(true)
  }

  test("throw error if different type parameters are disabled by @AvroErasedName") {
    intercept[SchemaParseException] {
      AvroSchema[SameGenericWithDifferentTypeArgsDisabled]
    }
  }

  test("allow repeated erased names when their definitions agree") {
    case class Repeated(first: GenericDisabled[Int], second: GenericDisabled[Int])
    val schema = AvroSchema[Repeated]
    new org.apache.avro.Schema.Parser().parse(schema.toString) shouldBe schema
  }

  test("reject conflicting erased names nested in arrays and maps") {
    case class ConflictingNested(first: List[GenericDisabled[Int]], second: Map[String, GenericDisabled[String]])
    intercept[SchemaParseException] {
      AvroSchema[ConflictingNested]
    }
  }

  test("named schema validation terminates for recursive references") {
    val schema = new org.apache.avro.Schema.Parser().parse(
      """{"type":"record","name":"Node","fields":[{"name":"next","type":["null","Node"]}]}""")
    com.sksamuel.avro4s.avroutils.SchemaHelper.validateNames(schema)
  }

  test("support top level generic") {
    val schema1 = AvroSchema[Generic[String]]
    val expected1 = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/generic_string.json"))
    schema1.toString(true) shouldBe expected1.toString(true)

    val schema2 = AvroSchema[Generic[Int]]
    val expected2 = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/generic_int.json"))
    schema2.toString(true) shouldBe expected2.toString(true)
  }

  test("generate only raw name if @AvroErasedName is present") {
    val schema = AvroSchema[GenericDisabled[String]]
    val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/generic_disabled.json"))
    schema.toString(true) shouldBe expected.toString(true)
  }

  //  test("support @AvroName on generic type args") {
  //    val schema = AvroSchema[DibDabDob]
  //    val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/avro_name_on_type_arg.json"))
  //    schema.toString(true) shouldBe expected.toString(true)
  //  }

  //  test("deeply nested generics") {
  //    val schema = AvroSchema[Outer[Thing1Id, Option[Seq[Thing2]]]]
  //    val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/deeply_nested_generics.json"))
  //    schema.toString(true) shouldBe expected.toString(true)
  //  }

  // todo once magnolia adds value types
  // test("deeply nested generic with value type") {
  //   val schema = AvroSchema[Generic[Option[Seq[Thing1Id]]]]
  //   val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/deeply_nested_generic_with_value_type.json"))
  //   schema.toString(true) shouldBe expected.toString(true)
  //  }
}

object First {
  case class TypeArg(a: String)
}

object Second {
  @AvroName("supertypearg")
  case class TypeArg(a: String)
}

case class Generic[T](value: T)

case class DibDabDob(a: Generic[First.TypeArg], b: Generic[Second.TypeArg])
