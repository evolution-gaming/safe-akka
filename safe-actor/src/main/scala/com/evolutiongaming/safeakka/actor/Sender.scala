package com.evolutiongaming.safeakka.actor

import akka.actor.{Actor, ActorContext, ActorPath, ActorRef}

final case class Sender(private val ref: ActorRef) {

  def path: ActorPath = ref.path

  def ![O](msg: O)(implicit sender: ActorRef = Actor.noSender, marshalReply: MarshalReply[O]): Unit =
    ref.!(marshalReply(msg))(sender)

  def tell[O](msg: O, sender: ActorRef)(implicit marshalReply: MarshalReply[O]): Unit = this.!(msg)(sender, marshalReply)

  def forward[O](message: O)
    (implicit context: ActorContext, marshalReply: MarshalReply[O]): Unit = tell(message, context.sender())

  def compareTo(other: ActorRef): Int = ref compareTo other

  override def hashCode: Int = ref.hashCode()

  override def equals(that: Any): Boolean = ref equals that

  def ==(that: ActorRef): Boolean = ref equals that

  override def toString: String = ref.toString()
}

object Sender {
  lazy val Empty: Sender = Sender(ActorRef.noSender)
}
