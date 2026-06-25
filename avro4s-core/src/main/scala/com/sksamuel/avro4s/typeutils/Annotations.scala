package com.sksamuel.avro4s.typeutils

import com.sksamuel.avro4s.{AvroAliasable, AvroDoc, AvroDocumentable, AvroEnumDefault, AvroErasedName, AvroError, AvroFixed, AvroName, AvroNameable, AvroNamespace, AvroNoDefault, AvroProp, AvroProperty, AvroSortPriority, AvroTransient, AvroUnionPosition}
import magnolia1.{CaseClass, TypeInfo}

class Annotations(annos: Seq[Any], inheritedAnnos: Seq[Any] = Nil) {
  private[this] val allAnnos: Seq[Any] = annos ++ inheritedAnnos
  private[this] val transientAnnotation: Option[AvroTransient] = annos.collectFirst {
    case t: AvroTransient => t
  }

  def name: Option[String] = annos.collectFirst {
    case t: AvroNameable => t.name
  }

  def namespace: Option[String] = allAnnos.collectFirst {
    case t: AvroNamespace => t.namespace
  }

  def aliases: Seq[String] = annos.collect {
    case t: AvroAliasable => t.alias
  }.filterNot(_.trim.isEmpty)

  def props: Map[String, AnyRef] = annos.collect {
    case t: AvroProperty => (t.key, t.value.asInstanceOf[AnyRef])
  }.toMap

  def doc: Option[String] = annos.collectFirst {
    case t: AvroDocumentable => t.doc
  }

  def transient: Boolean = transientAnnotation.isDefined

  def transientUseDefault: Boolean = transientAnnotation.exists(_.useDefault)
  
  def nodefault: Boolean = annos.collectFirst {
    case t: AvroNoDefault => t
  }.isDefined

  def erased: Boolean = annos.collectFirst {
    case t: AvroErasedName => t
  }.isDefined

  def error: Boolean = annos.collectFirst {
    case t: AvroError => t
  }.isDefined

  def noDefault: Boolean = annos.collectFirst {
    case t: AvroNoDefault => t
  }.isDefined

  /**
    * Returns the fixed size when a type or field is annotated with @AvroFixed
    */
  def fixed: Option[Int] = annos.collectFirst {
    case t: AvroFixed => t.size
  }

  private def avroSortPriority: Option[Float] = annos.collectFirst {
    case t: AvroSortPriority => t.priority
  }

  private def avroUnionPosition: Option[Float] = annos.collectFirst {
    case t: AvroUnionPosition => 999999f - t.position
  }

  def sortPriority: Option[Float] = avroSortPriority.orElse(avroUnionPosition)

  def enumDefault: Option[Any] = annos.collectFirst {
    case t: AvroEnumDefault => t.default
  }

}

object Annotations {
  def apply(ctx: CaseClass[_, _]): Annotations = new Annotations(ctx.annotations, ctx.inheritedAnnotations)
  def apply(annos: Seq[Any]): Annotations = new Annotations(annos, Nil)
}
