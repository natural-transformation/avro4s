# Avro4s Fork

This repository is an actively maintained fork of the original [avro4s](https://github.com/sksamuel/avro4s). Our fork focuses on supporting modern requirements—namely **Java 21**, **Scala 3**, and **Nix**—and may include changes that are not backward compatible with earlier Java or Scala versions.

## Disclaimer
You are free to copy any features or improvements from this fork back into the original avro4s project. Please note that this repository is optimized for our own production needs, so it may diverge substantially from the original.

## Why This Fork?
We created this fork to meet specific enterprise and client requirements that demand:
1. **Java 21 Support** (production-ready)
2. **Scala 3 Support** (production-ready)
3. **Nix Support** for reproducible builds and environments

These updates may introduce breaking changes for projects using older Java or Scala versions.

## Usage

To integrate Avro4s Fork into your Scala project, add the following dependency to your build.sbt:

```scala
libraryDependencies += "com.natural-transformation" %% "avro4s-core" % "5.1.0"
```

If you need to work with [cats](https://github.com/typelevel/cats), you also need to add `avro4s-cats` to your dependencies.

### Example

```scala
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import com.sksamuel.avro4s.SchemaFor
import com.sksamuel.avro4s.cats.{given, _}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class AvroMappingSpec extends AnyWordSpecLike with Matchers {

  "Avro4s" should {
    "Serialize and deserialize a case class with a Validated field" in {
      case class CatsValidatedTest(f1: Validated[String, Int], f2: Validated[String, Long])

      val schemaFor: SchemaFor[CatsValidatedTest] = summon[SchemaFor[CatsValidatedTest]]
      println(s"schema for CatsValidatedTest is ${schemaFor.schema.toString(true)}")

      val serialized    = AvroUtil.toBin(CatsValidatedTest(Validated.Invalid("error1"), Validated.Valid(1L)))
      val derserialized = AvroUtil.fromBin[CatsValidatedTest](serialized)

      derserialized shouldEqual CatsValidatedTest(Validated.Invalid("error1"), Validated.Valid(1L))

    }

    "Serialize and deserialize a case class with a ValidatedNel field" in {
      case class CatsValidatedNelTest(errorsOrNum: ValidatedNel[String, Int])
      val schemaFor: SchemaFor[CatsValidatedNelTest] = summon[SchemaFor[CatsValidatedNelTest]]
      println(s"schema for CatsValidatedNelTest is ${schemaFor.schema.toString(true)}")

      val serialized    = AvroUtil.toBin(CatsValidatedNelTest(Validated.Invalid(NonEmptyList.one("error1"))))
      val derserialized = AvroUtil.fromBin[CatsValidatedNelTest](serialized)

      derserialized shouldEqual CatsValidatedNelTest(Validated.Invalid(NonEmptyList.one("error1")))

    }
  }

}
```
**Note**: Although the library is published under the group ID `com.natural-transformation`, the package imports remain the same as the original Avro4s library (`com.sksamuel.avro4s`). This ensures backward compatibility with existing codebases, requiring no changes to import statements.

## Commercial Support
If your company is considering this fork for production, we can assist with both **adoption** and **migration**. Because each use case varies, pricing is arranged on an individual basis. For inquiries, please contact [Natural Transformation](mailto:zli@natural-transformation.com).

## Reproducibility and Security
We provide a strong guarantee of reproducibility and security by utilizing Nix for both local development and GitHub Actions CI. This approach ensures that every environment, from your local machine to our CI pipelines, uses exactly the same dependencies. As a result, we achieve the highest levels of reproducibility and security, eliminating discrepancies across different environments.

## Contributing and Issue Reporting
We welcome all contributions—bug reports, feature requests, and pull requests. Before contributing, please:
- Check existing [issues](../../issues) and [pull requests](../../pulls) to avoid duplication
- Ensure any changes align with this project’s focus on Java 21, Scala 3, and Nix support

We appreciate your feedback and help in making this fork even better!
