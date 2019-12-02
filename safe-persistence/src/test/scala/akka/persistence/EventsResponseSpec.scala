package akka.persistence

import com.evolutiongaming.safeakka.persistence.{PersistenceSignal => Signal}

import scala.util.control.NoStackTrace
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EventsResponseSpec extends AnyFunSuite with Matchers {

  test("unapply DeleteMessagesSuccess") {
    EventsResponse.unapply(DeleteMessagesSuccess(0L)) shouldEqual Some(Signal.DeleteEventsSuccess(0L))
  }

  test("unapply DeleteMessagesFailure") {
    val failure = new RuntimeException with NoStackTrace
    EventsResponse.unapply(DeleteMessagesFailure(failure, 0L)) shouldEqual Some(Signal.DeleteEventsFailure(0L, failure))
  }
}
