package akka.persistence

import com.evolutiongaming.safeakka.persistence.{PersistenceSignal => Signal}
import org.scalatest.{FunSuite, Matchers}

import scala.util.control.NoStackTrace

class EventsResponseSpec extends FunSuite with Matchers {

  test("unapply DeleteMessagesSuccess") {
    EventsResponse.unapply(DeleteMessagesSuccess(0l)) shouldEqual Some(Signal.DeleteEventsSuccess(0l))
  }

  test("unapply DeleteMessagesFailure") {
    val failure = new RuntimeException with NoStackTrace
    EventsResponse.unapply(DeleteMessagesFailure(failure, 0l)) shouldEqual Some(Signal.DeleteEventsFailure(0l, failure))
  }
}
