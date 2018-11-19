package com.evolutiongaming.safeakka.actor

import akka.actor.{Actor, ActorContext, ActorPath, ActorRef}

trait Sender {

  def ![A](msg: A)(implicit sender: ActorRef = Actor.noSender, marshalReply: Sender.MarshalReply[A]): Unit

  def tell[A](msg: A, sender: ActorRef)(implicit marshalReply: Sender.MarshalReply[A]): Unit =
    this.!(msg)(sender, marshalReply)

  def forward[A](message: A)(implicit context: ActorContext, marshalReply: Sender.MarshalReply[A]): Unit =
    tell(message, context.sender())

  def path: ActorPath
}

object Sender {

  private case class SenderImpl(protected val ref: ActorRef) extends Sender {
    
    override def ![A](msg: A)(implicit sender: ActorRef = Actor.noSender, marshalReply: Sender.MarshalReply[A]): Unit =
      ref.!(marshalReply marshal msg)(sender)

    def path: ActorPath = ref.path
  }

  def apply(actorRef: ActorRef): Sender = SenderImpl(actorRef)

  val Empty: Sender = Sender(ActorRef.noSender)

  trait MarshalReply[-A] {
    def marshal: A => Any
  }

  object MarshalReply {
    lazy val AnyImpl: MarshalReply[Any] = new MarshalReply[Any] {
      def marshal = identity
    }
  }
}
