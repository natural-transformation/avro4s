package benchmarks.performance

import com.sksamuel.avro4s.*
import org.apache.avro.Schema
import org.openjdk.jmh.annotations.*

object SchemaDerivationFixtures {
  case class Leaf(value: Int)
  case class Level1(child: Leaf, other: Leaf)
  case class Level2(child: Level1, other: Leaf)
  case class Level3(child: Level2, other: Leaf)
  case class Level4(child: Level3, other: Leaf)
  case class Level5(child: Level4, other: Leaf)
  case class Level6(child: Level5, other: Leaf)
}

@State(Scope.Thread)
class SchemaDerivationBenchmarks extends CodecBenchmarkParams {
  import SchemaDerivationFixtures.*

  @Setup(Level.Trial)
  def verifySchema(): Unit = {
    val schema = AvroSchema[Level6]
    require(new Schema.Parser().parse(schema.toString) == schema)
  }

  @Benchmark def deriveRaw(): Schema = SchemaFor[Level6].schema
  @Benchmark def deriveExported(): Schema = AvroSchema[Level6]
}
