package com.sksamuel.avro4s

import _root_.cats.data.{NonEmptyList, NonEmptyVector, NonEmptyChain, Validated, ValidatedNel}
import org.apache.avro.Schema
import com.sksamuel.avro4s.avroutils.SchemaHelper
import com.sksamuel.avro4s.ImmutableRecord
import scala.language.implicitConversions

package object cats:

  import scala.collection.JavaConverters._

  given[T](using schemaFor: SchemaFor[T]): SchemaFor[NonEmptyList[T]] = SchemaFor(Schema.createArray(schemaFor.schema))
  given[T](using schemaFor: SchemaFor[T]): SchemaFor[NonEmptyVector[T]] = SchemaFor(Schema.createArray(schemaFor.schema))
  given[T](using schemaFor: SchemaFor[T]): SchemaFor[NonEmptyChain[T]] = SchemaFor(Schema.createArray(schemaFor.schema))

  given[T](using encoder: Encoder[T]): Encoder[NonEmptyList[T]] = new Encoder[NonEmptyList[T]] :
    override def encode(schema: Schema): NonEmptyList[T] => Any = {
      require(schema.getType == Schema.Type.ARRAY)
      val encode = encoder.encode(schema)
      { value => value.map(encode).toList.asJava }
    }

  given[T](using encoder: Encoder[T]): Encoder[NonEmptyVector[T]] = new Encoder[NonEmptyVector[T]] :
    override def encode(schema: Schema): NonEmptyVector[T] => Any = {
      require(schema.getType == Schema.Type.ARRAY)
      val encode = encoder.encode(schema)
      { value => value.map(encode).toVector.asJava }
    }

  given[T](using encoder: Encoder[T]): Encoder[NonEmptyChain[T]] = new Encoder[NonEmptyChain[T]] :
    override def encode(schema: Schema): NonEmptyChain[T] => Any = {
      require(schema.getType == Schema.Type.ARRAY)
      val encode = encoder.encode(schema)
      { value => value.map(encode).toNonEmptyList.toList.asJava }
    }

  given[T](using decoder: Decoder[T]): Decoder[NonEmptyList[T]] = new Decoder[NonEmptyList[T]] :
    override def decode(schema: Schema): Any => NonEmptyList[T] = {
      require(schema.getType == Schema.Type.ARRAY)
      val decode = decoder.decode(schema)
      { value =>
        value match {
          case array: Array[_] => NonEmptyList.fromListUnsafe(array.toList.map(decode))
          case list: java.util.Collection[_] => NonEmptyList.fromListUnsafe(list.asScala.map(decode).toList)
          case other => sys.error("Unsupported type " + other)
        }
      }
    }

  given[T](using decoder: Decoder[T]): Decoder[NonEmptyVector[T]] = new Decoder[NonEmptyVector[T]] :
    override def decode(schema: Schema): Any => NonEmptyVector[T] = {
      require(schema.getType == Schema.Type.ARRAY)
      val decode = decoder.decode(schema)
      { value =>
        value match {
          case array: Array[_] => NonEmptyVector.fromVectorUnsafe(array.toVector.map(decode))
          case list: java.util.Collection[_] => NonEmptyVector.fromVectorUnsafe(list.asScala.map(decode).toVector)
          case other => sys.error("Unsupported type " + other)
        }
      }
    }

  given[T](using decoder: Decoder[T]): Decoder[NonEmptyChain[T]] = new Decoder[NonEmptyChain[T]] :
    override def decode(schema: Schema): Any => NonEmptyChain[T] = {
      require(schema.getType == Schema.Type.ARRAY)
      val decode = decoder.decode(schema)
      { value =>
        value match {
          case array: Array[_] => NonEmptyChain.fromSeq(array.toList.map(decode)).get
          case list: java.util.Collection[_] => NonEmptyChain.fromSeq(list.asScala.map(decode).toList).get
          case other => sys.error("Unsupported type " + other)
        }
      }
    }

  case class InvalidWrapper[E](value: E)
  case class ValidWrapper[T](value: T)

  given[E: SchemaFor, T: SchemaFor]: SchemaFor[Validated[E, T]] = 
    SchemaFor(
      Schema.createUnion(
        List(
          SchemaFor[InvalidWrapper[E]].schema,
          SchemaFor[ValidWrapper[T]].schema
        ).asJava
      )
    )

  given[E: Encoder, T: Encoder]: Encoder[Validated[E, T]] = 
    new Encoder[Validated[E, T]] {
      override def encode(schema: Schema): Validated[E, T] => Any = {
        require(schema.getType == Schema.Type.UNION)
        val invalidSchema = SchemaHelper.getFirstFromUnionOfTwo(schema, "InvalidWrapper")
        val validSchema = SchemaHelper.getSecondFromUnionOfTwo(schema, "ValidWrapper")
        
        val invalidEncoder = Encoder[InvalidWrapper[E]].encode(invalidSchema)
        val validEncoder = Encoder[ValidWrapper[T]].encode(validSchema)
        
        {
          case Validated.Invalid(e) => invalidEncoder(InvalidWrapper(e))
          case Validated.Valid(t) => validEncoder(ValidWrapper(t))
        }
      }
    }

  given[E: Decoder, T: Decoder]: Decoder[Validated[E, T]] = 
    new Decoder[Validated[E, T]] {
      override def decode(schema: Schema): Any => Validated[E, T] = {
        require(schema.getType == Schema.Type.UNION)

        val invalidSchema = SchemaHelper.getFirstFromUnionOfTwo(schema, "InvalidWrapper")
        val validSchema = SchemaHelper.getSecondFromUnionOfTwo(schema, "ValidWrapper")

        val invalidDecoder = Decoder[InvalidWrapper[E]].decode(invalidSchema)
        val validDecoder = Decoder[ValidWrapper[T]].decode(validSchema)

        value => {
          value match {
            case ImmutableRecord(`invalidSchema`, _) => 
              val InvalidWrapper(e) = invalidDecoder(value)
              Validated.Invalid(e)
            case ImmutableRecord(`validSchema`, _) => 
              val ValidWrapper(t) = validDecoder(value)
              Validated.Valid(t)
            case _ =>
              throw new Avro4sDecodingException(
                s"Could not decode $value into cats.data.Validated[${invalidSchema.getFullName}, ${validSchema.getFullName}]",
                value
              )
          }
        }
      }
    }
