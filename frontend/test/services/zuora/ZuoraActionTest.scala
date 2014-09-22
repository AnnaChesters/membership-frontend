package services.zuora

import model.Zuora.{Authentication, ZuoraResult}
import org.specs2.mutable.Specification

import scala.xml.XML

class ZuoraActionTest extends Specification {

  implicit val auth = Authentication("token", "http://example.com/")

  "ZuoraAction" should {
    "create a standard action" in {
      val xml = XML.loadString(TestAction().xml)
      (xml \ "Body" \ "test").length mustEqual 1
      (xml \ "Header" \ "SessionHeader" \ "session").text.trim mustEqual "token"
      (xml \ "Header" \ "CallOptions" \ "useSingleTransaction").length mustEqual 0
    }

    "create an un-authenticated action" in {
      val action = new TestAction {
        override val authRequired = false
      }

      val xml = XML.loadString(action.xml)
      (xml \ "Header" \ "SessionHeader").length mustEqual 0
    }

    "create a single transaction action" in {
      val action = new TestAction {
        override val singleTransaction = true
      }

      val xml = XML.loadString(action.xml)
      (xml \ "Header" \ "CallOptions" \ "useSingleTransaction").text mustEqual "true"
    }

    "create an un-authenticated, single transaction action" in {
      val action = new TestAction {
        override val authRequired = false
        override val singleTransaction = true
      }

      val xml = XML.loadString(action.xml)
      (xml \ "Header" \ "SessionHeader").length mustEqual 0
      (xml \ "Header" \ "CallOptions" \ "useSingleTransaction").text mustEqual "true"
    }
  }

  case class TestResult() extends ZuoraResult

  case class TestAction() extends ZuoraAction[TestResult] {
    val body = <test></test>
  }

}
