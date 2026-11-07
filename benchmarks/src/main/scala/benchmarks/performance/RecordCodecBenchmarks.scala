package benchmarks.performance

import com.sksamuel.avro4s.*
import org.apache.avro.generic.{GenericDatumReader, GenericDatumWriter, IndexedRecord}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.openjdk.jmh.annotations.*

import java.io.ByteArrayOutputStream

object RecordCodecFixtures {
  case class Flat(number: Int, total: Long, fraction: Double, enabled: Boolean)
  case class Wide(a: Long, b: Long, c: Long, d: Long, e: Long, f: Long,
                  g: Long, h: Long, i: Long, j: Long, k: Long, l: Long)
  case class Nested(left: Flat, right: Flat, wide: Wide)

  val flat = Flat(1001, 2002L, 3.25, true)
  val wide = Wide(1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012)
  val nested = Nested(flat, Flat(4004, 5005L, 6.75, false), wide)
}

@State(Scope.Thread)
class RecordCodecBenchmarks extends CodecBenchmarkParams {
  import RecordCodecFixtures.*

  private val flatSchema = AvroSchema[Flat]
  private val wideSchema = AvroSchema[Wide]
  private val nestedSchema = AvroSchema[Nested]
  private val flatEncode = Encoder[Flat].encode(flatSchema)
  private val flatDecode = Decoder[Flat].decode(flatSchema)
  private val wideEncode = Encoder[Wide].encode(wideSchema)
  private val wideDecode = Decoder[Wide].decode(wideSchema)
  private val nestedEncode = Encoder[Nested].encode(nestedSchema)
  private val nestedDecode = Decoder[Nested].decode(nestedSchema)
  private val flatRecord = flatEncode(flat).asInstanceOf[IndexedRecord]
  private val wideRecord = wideEncode(wide)
  private val nestedRecord = nestedEncode(nested)

  // A hand-written reference for the proposed macro, not a general-purpose codec.
  // It uses the same child codecs and record representation as the generated path.
  // Only this fixture's fixed schema is supported; setup verifies equivalent values.
  private val encodeInt = Encoder[Int].encode(flatSchema.getField("number").schema())
  private val encodeLong = Encoder[Long].encode(flatSchema.getField("total").schema())
  private val encodeDouble = Encoder[Double].encode(flatSchema.getField("fraction").schema())
  private val encodeBoolean = Encoder[Boolean].encode(flatSchema.getField("enabled").schema())
  private val decodeInt = Decoder[Int].decode(flatSchema.getField("number").schema())
  private val decodeLong = Decoder[Long].decode(flatSchema.getField("total").schema())
  private val decodeDouble = Decoder[Double].decode(flatSchema.getField("fraction").schema())
  private val decodeBoolean = Decoder[Boolean].decode(flatSchema.getField("enabled").schema())

  @Setup(Level.Trial)
  def checkFixtures(): Unit = {
    require(flatDecode(flatRecord) == flat)
    require(wideDecode(wideRecord) == wide)
    require(nestedDecode(nestedRecord) == nested)
    require(encodeFlatDirectReference() == flatRecord)
    require(decodeFlatDirectReference() == flat)
  }

  @Benchmark def encodeFlat(): AnyRef = flatEncode(flat)
  @Benchmark def decodeFlat(): Flat = flatDecode(flatRecord)
  @Benchmark def encodeWide(): AnyRef = wideEncode(wide)
  @Benchmark def decodeWide(): Wide = wideDecode(wideRecord)
  @Benchmark def encodeNested(): AnyRef = nestedEncode(nested)
  @Benchmark def decodeNested(): Nested = nestedDecode(nestedRecord)

  @Benchmark def encodeFlatDirectReference(): ImmutableRecord = {
    val values = new Array[Any](4)
    values(0) = encodeInt(flat.number)
    values(1) = encodeLong(flat.total)
    values(2) = encodeDouble(flat.fraction)
    values(3) = encodeBoolean(flat.enabled)
    ImmutableRecord(flatSchema, values.toSeq)
  }

  @Benchmark def decodeFlatDirectReference(): Flat = Flat(
    decodeInt(flatRecord.get(0)), decodeLong(flatRecord.get(1)),
    decodeDouble(flatRecord.get(2)), decodeBoolean(flatRecord.get(3)))
}

@State(Scope.Thread)
class CodecPreparationBenchmarks extends CodecBenchmarkParams {
  import RecordCodecFixtures.*

  private val schema = AvroSchema[Nested]
  private val encoder = Encoder[Nested]
  private val decoder = Decoder[Nested]
  private val encode = encoder.encode(schema)
  private val decode = decoder.decode(schema)
  private val record = encode(nested).asInstanceOf[IndexedRecord]
  private val toRecord = ToRecord[Nested](schema)(using encoder)
  private val fromRecord = FromRecord[Nested](schema)(using decoder)

  @Setup(Level.Trial)
  def checkFixtures(): Unit = {
    require(fromRecord.from(record) == nested)
    require(toRecord.to(nested) == record)
  }

  @Benchmark def wrapperEncode(): Record = toRecord.to(nested)
  @Benchmark def wrapperDecode(): Nested = fromRecord.from(record)
  @Benchmark def preparedEncodeReference(): AnyRef = encode(nested)
  @Benchmark def preparedDecodeReference(): Nested = decode(record)
}

@State(Scope.Thread)
class RecordBinaryBenchmarks extends CodecBenchmarkParams {
  import RecordCodecFixtures.*

  private val schema = AvroSchema[Nested]
  private val encode = Encoder[Nested].encode(schema)
  private val decode = Decoder[Nested].decode(schema)
  private val writer = new GenericDatumWriter[Any](schema)
  private val reader = new GenericDatumReader[Any](schema)
  private val output = new ByteArrayOutputStream(512)
  private val binaryEncoder = EncoderFactory.get().binaryEncoder(output, null)
  private val bytes = encodeBinary()
  private var binaryDecoder = DecoderFactory.get().binaryDecoder(bytes, null)

  @Setup(Level.Trial)
  def checkFixture(): Unit = require(decodeBinary() == nested)

  @Benchmark def encodeBinary(): Array[Byte] = {
    output.reset()
    writer.write(encode(nested), binaryEncoder)
    binaryEncoder.flush()
    output.toByteArray
  }

  @Benchmark def decodeBinary(): Nested = {
    binaryDecoder = DecoderFactory.get().binaryDecoder(bytes, binaryDecoder)
    decode(reader.read(null, binaryDecoder))
  }
}
