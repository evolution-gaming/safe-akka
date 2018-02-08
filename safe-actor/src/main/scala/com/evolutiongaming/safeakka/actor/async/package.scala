package com.evolutiongaming.safeakka.actor

import scala.concurrent.Future


package object async {

  type Handler[T] = T => Future[Option[T]]

  object Handler {

    private lazy val Empty: Handler[Any] = now(identity)

    def empty[T]: Handler[T] = Empty.asInstanceOf[Handler[T]]

    def stop[T](f: T => Unit): Handler[T] = (x: T) => {
      f(x)
      Future successful None
    }

    def now[T](f: T => T): Handler[T] = (x: T) => {
      val xx = f(x)
      Future successful Some(xx)
    }
  }


  type AsyncHandler[T] = Future[Handler[T]]

  object AsyncHandler {

    private lazy val Empty: AsyncHandler[Any] = now(identity)

    def now[T](f: T => T): AsyncHandler[T] = {
      val handler = Handler now f
      Future successful handler
    }

    def stop[T](f: T => Unit): AsyncHandler[T] = {
      val handler = Handler stop f
      Future successful handler
    }

    def empty[T]: AsyncHandler[T] = Empty.asInstanceOf[AsyncHandler[T]]
  }
}
