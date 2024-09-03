package com.evolutiongaming.safeakka.persistence

import java.io.NotSerializableException

import akka.serialization.Serializer

class TestSerializer extends Serializer {
  import com.evolutiongaming.safeakka.persistence.TestSerializer.*

  val identifier = 1234567890

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case Msg.Serializable    => Array.empty
    case Msg.NotSerializable => throw new NotSerializableException("")
    case Msg.Illegal         => throw new IllegalArgumentException("")
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[?]]): AnyRef = {
    Msg.Serializable
  }

  def includeManifest = false
}

object TestSerializer {

  sealed trait Msg
  object Msg {
    object Serializable extends Msg
    object NotSerializable extends Msg
    object Illegal extends Msg
  }
}