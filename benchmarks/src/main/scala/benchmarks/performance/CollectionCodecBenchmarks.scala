package benchmarks.performance

import com.sksamuel.avro4s.*
import org.apache.avro.Schema
import org.apache.avro.util.Utf8
import org.openjdk.jmh.annotations.*

import scala.jdk.CollectionConverters.*

@State(Scope.Thread)
class CollectionCodecBenchmarks extends CodecBenchmarkParams {
  @Param(Array("0", "1", "8", "128", "1024"))
  var size: Int = 0

  private val arraySchema = Schema.createArray(Schema.create(Schema.Type.INT))
  private val encodeArray = Encoder[Array[Int]].encode(arraySchema)
  private val encodeList = Encoder[List[Int]].encode(arraySchema)
  private val encodeVector = Encoder[Vector[Int]].encode(arraySchema)
  private val decodeArray = Decoder[Array[Int]].decode(arraySchema)
  private val decodeList = Decoder[List[Int]].decode(arraySchema)
  private val decodeVector = Decoder[Vector[Int]].decode(arraySchema)
  private var array: Array[Int] = _
  private var list: List[Int] = _
  private var vector: Vector[Int] = _
  private var javaList: java.util.List[Integer] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    array = Array.tabulate(size)(i => 1000 + i)
    list = array.toList
    vector = array.toVector
    javaList = new java.util.ArrayList[Integer](size)
    array.foreach(value => javaList.add(Integer.valueOf(value)))
    require(decodeArray(javaList).toVector == vector)
    require(decodeList(javaList) == list)
    require(decodeVector(javaList) == vector)
    require(decodeArray(list).toVector == vector)
    require(decodeVector(list) == vector)
    require(decodeVector(vector) == vector)
    require(decodeVector(array) == vector)
    require(decodeArray(encodeArray(array)).toVector == vector)
    require(decodeList(encodeList(list)) == list)
    require(decodeVector(encodeVector(vector)) == vector)
  }

  @Benchmark def arrayEncode(): AnyRef = encodeArray(array)
  @Benchmark def listEncode(): AnyRef = encodeList(list)
  @Benchmark def vectorEncode(): AnyRef = encodeVector(vector)
  @Benchmark def javaCollectionToArray(): Array[Int] = decodeArray(javaList)
  @Benchmark def javaCollectionToList(): List[Int] = decodeList(javaList)
  @Benchmark def javaCollectionToVector(): Vector[Int] = decodeVector(javaList)
  @Benchmark def scalaListToArray(): Array[Int] = decodeArray(list)
  @Benchmark def scalaListToVector(): Vector[Int] = decodeVector(list)
  @Benchmark def scalaVectorToVector(): Vector[Int] = decodeVector(vector)
  @Benchmark def arrayToVector(): Vector[Int] = decodeVector(array)
}

@State(Scope.Thread)
class MapCodecBenchmarks extends CodecBenchmarkParams {
  @Param(Array("0", "1", "8", "128", "1024"))
  var size: Int = 0

  private val schema = Schema.createMap(Schema.create(Schema.Type.INT))
  private val encode = Encoder[Map[String, Int]].encode(schema)
  private val decode = Decoder[Map[String, Int]].decode(schema)
  private var input: Map[String, Int] = _
  private var stringKeys: java.util.Map[String, Integer] = _
  private var utf8Keys: java.util.Map[Utf8, Integer] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    input = (0 until size).iterator.map(i => s"key-$i" -> (1000 + i)).toMap
    stringKeys = new java.util.HashMap[String, Integer]()
    utf8Keys = new java.util.HashMap[Utf8, Integer]()
    input.foreach { case (key, value) =>
      stringKeys.put(key, Integer.valueOf(value))
      utf8Keys.put(new Utf8(key), Integer.valueOf(value))
    }
    require(decode(stringKeys) == input)
    require(decode(utf8Keys) == input)
    require(decode(encode(input)) == input)
  }

  @Benchmark def mapEncode(): AnyRef = encode(input)
  @Benchmark def stringKeyMapDecode(): Map[String, Int] = decode(stringKeys)
  @Benchmark def utf8KeyMapDecode(): Map[String, Int] = decode(utf8Keys)
}
