package com.evolutiongaming.safeakka.actor

import akka.actor.{ActorPath, ActorRef, ActorRefFactory, Props}


trait SafeActorRef[-A] extends SafeActorRef.Tell[A] {
  def path: ActorPath
  def unsafe: ActorRef
  def compose[B](ba: B => A): SafeActorRef[B] = SafeActorRef.Compose[B, A](this, ba)
}

object SafeActorRef {

  def apply[A](ref: ActorRef): SafeActorRef[A] = Impl(ref)

  def apply[A](
    setup: SetupActor[A],
    name: Option[String] = None)(implicit factory: ActorRefFactory, unapply: Unapply[A]): SafeActorRef[A] = {

    val props = Props(SafeActor(setup))
    val ref = name map { name => factory.actorOf(props, name) } getOrElse factory.actorOf(props)
    SafeActorRef(ref)
  }

  trait Tell[-A] {
    /**
      * @param canRcv ensures you can pass either T or one of the predefined system msgs
      */
    def tell[B](msg: B, sender: Option[ActorRef] = None)(implicit canRcv: CanRcv[B, A]): Unit


    def tell[B](msg: B, sender: ActorRef)(implicit canRcv: CanRcv[B, A]): Unit = tell(msg, Option(sender))

    def ![B](msg: B)(implicit sender: ActorRef = ActorRef.noSender, canRcv: CanRcv[B, A]): Unit = tell(msg, sender)
  }


  private final case class Impl[A](ref: ActorRef) extends SafeActorRef[A] {

    def tell[B](msg: B, sender: Option[ActorRef])(implicit canRcv: CanRcv[B, A]): Unit = {
      ref.tell(msg, sender getOrElse ActorRef.noSender)
    }

    def path = ref.path

    def unsafe = ref

    override def toString: String = s"SafeActorRef($ref)"
  }

  private final case class Compose[A, B](ref: SafeActorRef[B], f: A => B) extends SafeActorRef[A] {
    def path = ref.path
    def unsafe = ref.unsafe
    def tell[C](msg: C, sender: Option[ActorRef])(implicit canRcv: CanRcv[C, A]) = canRcv match {
      case CanRcv.Identity        => ref.tell(f(msg.asInstanceOf[A]), sender)(CanRcv.identity)
      case canRcv: CanRcv.Sys[C] => ref.tell(msg, sender)(canRcv)
    }
  }


  sealed trait CanRcv[-A, +B]

  object CanRcv {
    implicit def identity[A]: CanRcv[A, A] = Identity

    private[safeakka] case object Identity extends CanRcv[Any, Nothing]

    sealed trait Sys[A] extends CanRcv[A, Nothing]
    implicit case object Identify extends Sys[akka.actor.Identify]
    implicit case object PoisonPill extends Sys[akka.actor.PoisonPill]
    implicit case object Kill extends Sys[akka.actor.Kill]
    implicit case object ReceiveTimeout extends Sys[akka.actor.ReceiveTimeout]
  }
}
