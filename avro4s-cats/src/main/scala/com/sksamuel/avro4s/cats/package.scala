package com.sksamuel.avro4s

import _root_.cats.data.{NonEmptyList, NonEmptyVector, NonEmptyChain, Validated}
import org.apache.avro.Schema
import com.sksamuel.avro4s.avroutils.SchemaHelper

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

  given[E, T](using leftSchemaFor: SchemaFor[E], rightSchemaFor: SchemaFor[T]): SchemaFor[Validated[E, T]] = 
    new SchemaFor[Validated[E, T]]:
      override def schema = SchemaFor(SchemaHelper.createSafeUnion(leftSchemaFor.schema, rightSchemaFor.schema)).schema

  given[E, T](using leftEncoder: Encoder[E], rightEncoder: Encoder[T]): Encoder[Validated[E, T]] = 
    new Encoder[Validated[E, T]]:
      override def encode(schema: Schema): Validated[E, T] => Any = {
        require(schema.getType == Schema.Type.UNION)
        val leftSchema = SchemaHelper.extractEitherLeftSchema(schema, "cats.data.Validated")
        val rightSchema = SchemaHelper.extractEitherRightSchema(schema, "cats.data.Validated")
        val encodeLeft = leftEncoder.encode(leftSchema)
        val encodeRight = rightEncoder.encode(rightSchema)
        { value =>
          value match {
            case Validated.Invalid(e) => encodeLeft(e)
            case Validated.Valid(t) => encodeRight(t)
          }
        }
      }


  given[E, T](using leftDecoder: Decoder[E], leftGuard: TypeGuardedDecoding[E], rightDecoder: Decoder[T], rightGuard: TypeGuardedDecoding[T]): Decoder[Validated[E, T]] = 
    new Decoder[Validated[E, T]]:
      override def decode(schema: Schema): Any => Validated[E, T] = {
        require(schema.getType == Schema.Type.UNION)
        
        val leftSchema = SchemaHelper.extractEitherLeftSchema(schema, "cats.data.Validated")
        val rightSchema = SchemaHelper.extractEitherRightSchema(schema, "cats.data.Validated")
        
        val decodeLeft = leftDecoder.decode(leftSchema)
        val decodeRight = rightDecoder.decode(rightSchema)

        val leftP: PartialFunction[Any, Boolean] = leftGuard.guard(leftSchema)
        val rightP: PartialFunction[Any, Boolean] = rightGuard.guard(rightSchema)

        // how do we know whether the incoming value should be decoded to a a left or a right ?
        // we can compare types for primitives, and if a record we can compare schemas
        { value =>
          if (leftP.isDefinedAt(value)) {
            Validated.Invalid(decodeLeft(value))
          } else if (rightP.isDefinedAt(value)) {
            Validated.Valid(decodeRight(value))
          } else {
            val nameL = leftSchema.getFullName
            val nameR = rightSchema.getFullName
            throw new Avro4sDecodingException(s"Could not decode $value into cats.data.Validated[$nameL, $nameR]", value)
          }
        }
      } 
