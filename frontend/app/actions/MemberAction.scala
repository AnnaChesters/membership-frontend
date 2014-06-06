package actions

import scala.concurrent.Future

import play.api.mvc.{Request, ActionBuilder}
import play.api.mvc.Results.Forbidden
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import services.{AwsMemberTable, AuthenticationService}
import controllers.NoCache
import play.api.mvc.SimpleResult

trait MemberAction extends ActionBuilder[MemberRequest] {
  val authService: AuthenticationService

  protected def invokeBlock[A](request: Request[A], block: MemberRequest[A] => Future[SimpleResult]) = {
    val result = for {
      authRequest <- authService.authenticatedRequestFor(request)
      member <- AwsMemberTable.get(authRequest.user.id)
    } yield {
      block(MemberRequest[A](request, member))
    }

    result.getOrElse(Future.successful(Forbidden)).map(NoCache(_))
  }
}

object MemberAction extends MemberAction {
  val authService = AuthenticationService
}
