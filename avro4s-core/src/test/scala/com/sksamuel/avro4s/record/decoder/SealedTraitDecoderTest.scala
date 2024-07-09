package com.sksamuel.avro4s.record.decoder

import com.sksamuel.avro4s.*
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.avro.util.Utf8
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.apache.avro.generic.GenericContainer

class SealedTraitDecoderTest extends AnyFunSuite with Matchers {

  test("support sealed trait enums") {
    val schema = AvroSchema[DibbleWrapper]
    val dibbleSchema = AvroSchema[Dibble]

    val record1 = new GenericData.Record(schema)
    record1.put("dibble", new GenericData.EnumSymbol(dibbleSchema, Dobble))

    val record2 = new GenericData.Record(schema)
    record2.put("dibble", new GenericData.EnumSymbol(dibbleSchema, Dabble))

    Decoder[DibbleWrapper].decode(schema)(record1) shouldBe DibbleWrapper(Dobble)
    Decoder[DibbleWrapper].decode(schema)(record2) shouldBe DibbleWrapper(Dabble)
  }

  test("support sealed traits of case classes") {
    val schema = AvroSchema[Wrapper]
    val record = new GenericData.Record(schema)
    val wobble = new GenericData.Record(AvroSchema[Wobble])
    wobble.put("str", new Utf8("foo"))
    record.put("wibble", wobble)

    val wrapper = Decoder[Wrapper].decode(schema)(record)
    wrapper shouldBe Wrapper(Wobble("foo"))
  }

  test("support trait subtypes fields with same name") {
    val schema = AvroSchema[Trapper]
    val record = new GenericData.Record(schema)
    val tobble = new GenericData.Record(AvroSchema[Tobble])
    tobble.put("str", new Utf8("foo"))
    tobble.put("place", new Utf8("bar"))
    record.put("tibble", tobble)

    val trapper = Decoder[Trapper].decode(schema)(record)
    trapper shouldBe Trapper(Tobble("foo", "bar"))
  }

  test("support trait subtypes fields with same name and same type") {
    val schema = AvroSchema[Napper]
    val record = new GenericData.Record(schema)
    val nabble = new GenericData.Record(AvroSchema[Nabble])
    nabble.put("str", new Utf8("foo"))
    nabble.put("age", java.lang.Integer.valueOf(44))
    record.put("nibble", nabble)

    val napper = Decoder[Napper].decode(schema)(record)
    napper shouldBe Napper(Nabble("foo", 44))
  }

  test("support round-trip for sealed traits of case classes") {
    val schema = AvroSchema[Fruits]

    val fruits = Fruits(Apple(2.45), Orange("blue"))

    val record = Encoder[Fruits].encode(schema)(fruits)
    val fruitsAgain = Decoder[Fruits].decode(schema)(record)

    fruitsAgain shouldBe fruits
  }

  test("support round-trip for sealed traits with inheritable namespace") {
    @AvroNamespace("spam")
    sealed trait Foo
    object Foo {
      case class Bar(i: Int) extends Foo
    }

    val value: Foo = Foo.Bar(42)
    val encoded = Encoder[Foo].encode(AvroSchema[Foo])(value)
    encoded.asInstanceOf[GenericContainer].getSchema().getNamespace() shouldBe "spam"

    val decoded = Decoder[Foo].decode(AvroSchema[Foo])(encoded)

    decoded shouldBe value
  }

    //test("support sealed traits of case classes") {
    //val schema = AvroSchema[Wrapper]
    //val record = new GenericData.Record(schema)
    //val wobble = new GenericData.Record(AvroSchema[Wobble])
    //wobble.put("str", new Utf8("foo"))
    //record.put("wibble", wobble)

    //val wrapper = Decoder[Wrapper].decode(schema)(record)
    //wrapper shouldBe Wrapper(Wobble("foo"))
  //}

  //test("support trait subtypes fields with same name") {
    //val schema = AvroSchema[Trapper]
    //val record = new GenericData.Record(schema)
    //val tobble = new GenericData.Record(AvroSchema[Tobble])
    //tobble.put("str", new Utf8("foo"))
    //tobble.put("place", new Utf8("bar"))
    //record.put("tibble", tobble)

    //val trapper = Decoder[Trapper].decode(schema)(record)
    //trapper shouldBe Trapper(Tobble("foo", "bar"))
  //}

  //test("support trait subtypes fields with same name and same type") {
    //val schema = AvroSchema[Napper]
    //val record = new GenericData.Record(schema)
    //val nabble = new GenericData.Record(AvroSchema[Nabble])
    //nabble.put("str", new Utf8("foo"))
    //nabble.put("age", java.lang.Integer.valueOf(44))
    //record.put("nibble", nabble)

    //val napper = Decoder[Napper].decode(schema)(record)
    //napper shouldBe Napper(Nabble("foo", 44))
  //}

  //test("support top level ADTs") {
    //val schema = AvroSchema[Nibble]
    //val nabble = new GenericData.Record(schema)
    //nabble.put("str", new Utf8("foo"))
    //nabble.put("age", java.lang.Integer.valueOf(44))

    //Decoder[Nibble].decode(schema)(nabble) shouldBe Nabble("foo", 44)
  //}

  //test("use @AvroNamespace when choosing which type to decode") {

    //val appleschema = SchemaBuilder.record("Apple").namespace("market").fields().requiredDouble("weight").endRecord()
    //val orangeschema = SchemaBuilder.record("Orange").namespace("market").fields().requiredString("color").endRecord()
    //val union = SchemaBuilder.unionOf().`type`(appleschema).and().`type`(orangeschema).endUnion()
    //val schema = SchemaBuilder.record("Buy").fields().name("fruit").`type`(union).noDefault().endRecord()

    //Decoder[Buy].decode(schema)(ImmutableRecord(schema, Vector(ImmutableRecord(appleschema, Vector(java.lang.Double.valueOf(0.3)))))) shouldBe Buy(Apple(0.3))
    //Decoder[Buy].decode(schema)(ImmutableRecord(schema, Vector(ImmutableRecord(orangeschema, Vector(new Utf8("bright orange")))))) shouldBe Buy(Orange("bright orange"))
  //}

  //test("use @AvroNamespace and @AvroName with sealed traits of case objects") {
    //val thingySchema = SchemaBuilder.enumeration("thingy").symbols("whim_wham", "widget")
    //val schema = SchemaBuilder.record("ThingHolder").fields().name("thing").`type`(thingySchema).noDefault().endRecord()
    //val record1 = new GenericData.Record(schema)
    //record1.put("thing", "whim_wham")

    //val record2 = new GenericData.Record(schema)
    //record2.put("thing", "widget")

    //Decoder[ThingHolder].decode(schema)(record1) shouldBe ThingHolder(WhimWham)
    //Decoder[ThingHolder].decode(schema)(record2) shouldBe ThingHolder(Widget)
  //}

  //test("use @AvroNamespace and @AvroName with sealed traits of case objects in a round trip") {
    //val thingySchema = SchemaBuilder.enumeration("thingy").symbols("whim_wham", "widget")
    //val schema = SchemaBuilder.record("ThingHolder").fields().name("thing").`type`(thingySchema).noDefault().endRecord()

    //val value = ThingHolder(WhimWham)
    //val encodedRecord: GenericRecord = Encoder[ThingHolder].encode(schema)(value).asInstanceOf[GenericRecord]
    //val decoded = Decoder[ThingHolder].decode(schema)(encodedRecord)
    //decoded shouldBe value
  //}

}

sealed trait Dibble
case object Dobble extends Dibble
case object Dabble extends Dibble
case class DibbleWrapper(dibble: Dibble)

sealed trait Wibble
case class Wobble(str: String) extends Wibble
case class Wabble(dbl: Double) extends Wibble
case class Wrapper(wibble: Wibble)

sealed trait Tibble
case class Tobble(str: String, place: String) extends Tibble
case class Tabble(str: Double, age: Int) extends Tibble
case class Trapper(tibble: Tibble)

sealed trait Nibble
case class Nobble(str: String, place: String) extends Nibble
case class Nabble(str: String, age: Int) extends Nibble
case class Napper(nibble: Nibble)

sealed trait Fruit

@AvroNamespace("market")
final case class Apple(weight: Double) extends Fruit

@AvroNamespace("market")
final case class Orange(color: String) extends Fruit

final case class Fruits(fruit1: Fruit, fruit2: Fruit)

@AvroNamespace("market")
final case class Buy(fruit: Fruit)

final case class ThingHolder(thing: Thingy)

@AvroName("thingy")
sealed trait Thingy
@AvroName("whim_wham")
case object WhimWham extends Thingy
@AvroName("widget")
case object Widget extends Thingy
