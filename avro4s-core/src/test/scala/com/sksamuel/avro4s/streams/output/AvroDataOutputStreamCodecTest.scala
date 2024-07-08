package com.sksamuel.avro4s.streams.output

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

import com.sksamuel.avro4s.{AvroOutputStream, AvroSchema, Encoder}
import org.apache.avro.file.CodecFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AvroDataOutputStreamCodecTest extends AnyWordSpec with Matchers {

  case class Composer(name: String, birthplace: String, compositions: Seq[String])
  val schema = AvroSchema[Composer]
  val ennio = Composer("ennio morricone", "rome", Seq("legend of 1900", "ecstasy of gold"))

  "AvroDataOutputStream" should {
    "include schema" in {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.data[Composer](schema, Encoder[Composer]).to(baos).build()
      output.write(ennio)
      output.close()
      baos.toString(StandardCharsets.UTF_8.name()) should include("birthplace")
      baos.toString(StandardCharsets.UTF_8.name()) should include("compositions")
    }

    "include deflate coded in metadata when serialized with deflate" in {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.data[Composer](schema, Encoder[Composer]).to(baos).withCodec(CodecFactory.deflateCodec(CodecFactory.DEFAULT_DEFLATE_LEVEL)).build()
      output.write(ennio)
      output.close()
      baos.toString(StandardCharsets.UTF_8.name()) should include("deflate")
    }

    "include bzip2 coded in metadata when serialized with bzip2" in {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.data[Composer](schema, Encoder[Composer]).to(baos).withCodec(CodecFactory.bzip2Codec).build()
      output.write(ennio)
      output.close()
      baos.toString(StandardCharsets.UTF_8.name()) should include("bzip2")
    }
  }
}
