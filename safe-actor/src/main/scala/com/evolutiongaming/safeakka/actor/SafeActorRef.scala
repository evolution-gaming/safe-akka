package com.evolutiongaming.safeakka.actor

import akka.actor.{ActorPath, ActorRef, ActorRefFactory, Props}


trait SafeActorRef[-T] extends SafeActorRef.Tell[T] {
  def path: ActorPath
  def unsafe: ActorRef
  def compose[TT](f: TT => T): SafeActorRef[TT] = SafeActorRef.Compose[TT, T](this, f)
}

object SafeActorRef {

  def apply[T](ref: ActorRef): SafeActorRef[T] = Impl(ref)

  def apply[T](
    setup: SetupActor[T],
    name: Option[String] = None)(implicit factory: ActorRefFactory, unapply: Unapply[T]): SafeActorRef[T] = {

    val props = Props(SafeActor(setup))
    val ref = name map { name => factory.actorOf(props, name) } getOrElse factory.actorOf(props)
    SafeActorRef(ref)
  }

  trait Tell[-T] {
    /**
      * @param canRcv ensures you can pass either T or one of the predefined system msgs
      */
    def tell[TT](msg: TT, sender: Option[ActorRef] = None)(implicit canRcv: CanRcv[TT, T]): Unit


    def tell[TT](msg: TT, sender: ActorRef)(implicit canRcv: CanRcv[TT, T]): Unit = tell(msg, Option(sender))

    def ![TT](msg: TT)(implicit sender: ActorRef = ActorRef.noSender, canRcv: CanRcv[TT, T]): Unit = tell(msg, sender)
  }


  private case class Impl[T](ref: ActorRef) extends SafeActorRef[T] {

    def tell[TT](msg: TT, sender: Option[ActorRef])(implicit canRcv: CanRcv[TT, T]): Unit = {
      ref.tell(msg, sender getOrElse ActorRef.noSender)
    }

    def path = ref.path

    def unsafe = ref

    override def toString: String = s"SafeActorRef($ref)"
  }

  private case class Compose[A, B](ref: SafeActorRef[B], f: A => B) extends SafeActorRef[A] {
    def path = ref.path
    def unsafe = ref.unsafe
    def tell[TT](msg: TT, sender: Option[ActorRef])(implicit canRcv: CanRcv[TT, A]) = canRcv match {
      case CanRcv.Identity        => ref.tell(f(msg.asInstanceOf[A]), sender)(CanRcv.identity)
      case canRcv: CanRcv.Sys[TT] => ref.tell(msg, sender)(canRcv)
    }
  }


  sealed trait CanRcv[-T, +TT]

  object CanRcv {
    implicit def identity[T]: CanRcv[T, T] = Identity

    private[safeakka] case object Identity extends CanRcv[Any, Nothing]

    sealed trait Sys[T] extends CanRcv[T, Nothing]
    implicit case object Identify extends Sys[akka.actor.Identify]
    implicit case object PoisonPill extends Sys[akka.actor.PoisonPill]
    implicit case object Kill extends Sys[akka.actor.Kill]
  }
}
