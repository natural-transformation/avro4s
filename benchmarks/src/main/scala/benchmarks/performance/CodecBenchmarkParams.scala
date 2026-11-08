package benchmarks.performance

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*

// Keep JVM tuning identical before/after and avoid obsolete flags in CommonParams.
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = Array("-Xms512m", "-Xmx512m"))
abstract class CodecBenchmarkParams
