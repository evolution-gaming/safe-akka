package akka.persistence

import com.evolutiongaming.safeakka.persistence.{PersistenceSignal => Signal}
import org.scalatest.{FunSuite, Matchers}

import scala.util.control.NoStackTrace

class EventsResponseSpec extends FunSuite with Matchers {

  test("unapply DeleteMessagesSuccess") {
    EventsResponse.unapply(DeleteMessagesSuccess(0L)) shouldEqual Some(Signal.DeleteEventsSuccess(0L))
  }

  test("unapply DeleteMessagesFailure") {
    val failure = new RuntimeException with NoStackTrace
    EventsResponse.unapply(DeleteMessagesFailure(failure, 0L)) shouldEqual Some(Signal.DeleteEventsFailure(0L, failure))
  }
}
