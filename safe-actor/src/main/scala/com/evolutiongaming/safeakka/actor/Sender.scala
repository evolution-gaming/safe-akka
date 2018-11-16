package com.evolutiongaming.safeakka.actor

import akka.actor.{Actor, ActorContext, ActorPath, ActorRef}

trait Sender {

  protected def ref: ActorRef

  def path: ActorPath = ref.path

  def ![A](msg: A)(implicit sender: ActorRef = Actor.noSender, marshalReply: Sender.MarshalReply[A]): Unit =
    ref.!(marshalReply marshal msg)(sender)

  def tell[A](msg: A, sender: ActorRef)(implicit marshalReply: Sender.MarshalReply[A]): Unit = this.!(msg)(sender, marshalReply)

  def forward[A](message: A)
    (implicit context: ActorContext, marshalReply: Sender.MarshalReply[A]): Unit = tell(message, context.sender())

  override def hashCode: Int = ref.hashCode()

  override def equals(that: Any): Boolean = that match {
    case s: Sender => ref equals s.ref
    case _         => ref equals that
  }

  override def toString: String = s"Sender(${ref.toString()})"
}

object Sender {

  trait MarshalReply[-A] {
    def marshal: A => Any
  }

  def apply(actorRef: ActorRef): Sender = new Sender {
    protected val ref: ActorRef = actorRef
  }

  val Empty: Sender = Sender(ActorRef.noSender)
}
