package com.evolutiongaming.safeakka.actor

import scala.concurrent.Future


package object async {

  type Handler[A] = A => Future[Option[A]]

  object Handler {

    private lazy val Empty: Handler[Any] = now(identity)

    def empty[A]: Handler[A] = Empty.asInstanceOf[Handler[A]]

    def stop[A](f: A => Unit): Handler[A] = (x: A) => {
      f(x)
      Future successful None
    }

    def now[A](f: A => A): Handler[A] = (x: A) => {
      val xx = f(x)
      Future successful Some(xx)
    }
  }


  type AsyncHandler[A] = Future[Handler[A]]

  object AsyncHandler {

    private lazy val Empty: AsyncHandler[Any] = now(identity)

    def now[A](f: A => A): AsyncHandler[A] = {
      val handler = Handler now f
      Future successful handler
    }

    def stop[A](f: A => Unit): AsyncHandler[A] = {
      val handler = Handler stop f
      Future successful handler
    }

    def empty[A]: AsyncHandler[A] = Empty.asInstanceOf[AsyncHandler[A]]
  }
}
