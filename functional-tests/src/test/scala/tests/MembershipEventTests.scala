package tests

import com.gu.membership.tags._
import steps.MembershipSteps

class MembershipEventTests extends BaseMembershipTest {

  feature("See event list") {

    /*
     I order to view an event's details
     As a user
     I want to see a list of events
     */
    scenarioWeb("ME1. Logged in user sees event list", EventListTest, CoreTest) {
      implicit driver =>
        given {
          MembershipSteps().IAmLoggedIn
        }
          .when {
          _.IGoToTheEventsPage
        }
          .then {
          _.ISeeAListOfEvents
        }
    }

    scenarioWeb("ME2. Non logged in user sees event list", EventListTest, OptionalTest) {
      implicit driver =>
        given {
          MembershipSteps().IAmNotLoggedIn
        }
          .when {
          _.IGoToTheEventsPage
        }
          .then {
          _.ISeeAListOfEvents
        }
    }
  }

  feature("See details for an event") {
    /*
     In order to choose an event that I like
     As a user
     I want to see the details of an event
     */
    scenarioWeb("ME3. Logged in user sees details for an event", EventDetailTest, OptionalTest) {
      implicit driver =>
        given {
          MembershipSteps().IAmLoggedIn
        }
          .when {
          _.IClickOnAnEvent
        }
          .then {
          _.ISeeTheEventDetails
        }
    }

    scenarioWeb("ME4. Non logged in user sees details for an event", EventDetailTest, OptionalTest) {
      implicit driver =>
        given {
          MembershipSteps().IAmNotLoggedIn
        }
          .when {
          _.IClickOnAnEvent
        }
          .then {
          _.ISeeTheEventDetails
        }
    }

    scenarioWeb("ME5. Event details are the same as on the event provider", EventDetailTest, OptionalTest) {
      implicit driver =>
        given {
          MembershipSteps().IAmLoggedInAsAFriend
        }
          .when {
          _.IClickOnAnEvent
        }
          .then {
          _.TheDetailsAreTheSameAsOnTheEventProvider
        }
    }
  }

  feature("Require login for purchase") {
    /*
     In order to purchase a ticket
     As a user
     I need to be logged in
     */
    scenarioWeb("ME6. Logged in user can purchase a ticket", EventTicketPurchase, OptionalTest) {
      implicit driver =>
        given {
          MembershipSteps().IAmLoggedInAsAFriend
        }
          .when {
          _.IClickThePurchaseButton
        }
          .then {
          _.ICanPurchaseATicket
        }
    }

    // buy button is disabled for non members
//    scenarioWeb("ME7. Non logged in user has to login in order to purchase a ticket", OptionalTest) {
//      implicit driver =>
//        given {
//          steps.MembershipSteps().IAmNotLoggedIn
//        }
//          .when {
//          _.IClickThePurchaseButton
//        }
//          .then {
//          _.IAmRedirectedToTheChooseTierPage
//        }
//    }
//
//    scenarioWeb("ME8. Non-registered user can become a friend and purchase a ticket", OptionalTest) {
//      implicit driver =>
//        given {
//          steps.MembershipSteps().IAmNotLoggedIn
//        }
//          .when {
//          _.IClickThePurchaseButton
//        }
//          .then {
//          _.IAmRedirectedToTheChooseTierPage
//            .ICanBecomeAFriend
//            .ICanSeeTheTicketIframe
//        }
//    }
//
//    scenarioWeb("ME9. Non-registered user can become a partner and purchase a ticket", OptionalTest) {
//      implicit driver =>
//        given {
//          steps.MembershipSteps().IAmNotLoggedIn
//        }
//          .when {
//          _.IClickThePurchaseButton
//        }
//          .then {
//          _.IAmRedirectedToTheChooseTierPage
//            .ICanBecomeAPartner
//            .ICanSeeTheTicketIframe
//        }
//    }
  }
}
