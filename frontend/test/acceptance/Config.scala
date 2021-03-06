package acceptance

import java.net.URL
import com.typesafe.config.ConfigFactory
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}
import org.openqa.selenium.{Platform, WebDriver}
import org.slf4j.LoggerFactory
import scala.util.Try

object Config {

  def logger = LoggerFactory.getLogger(this.getClass)

  private val conf = ConfigFactory.load("acceptance-test")

  val baseUrl = conf.getString("membership.url")
  val profileUrl = conf.getString("identity.webapp.url")

  lazy val driver: WebDriver = {
    Try { new URL(conf.getString("webDriverRemoteUrl")) }.toOption.map { url =>
      new RemoteWebDriver(url, defaultCapabilities)
    }.getOrElse {
      new FirefoxDriver()
    }
  }

  val defaultCapabilities = {
    val capabilities = DesiredCapabilities.firefox()
    capabilities.setCapability("platform", Platform.WIN8)
    capabilities.setCapability("name", "Membership Frontend: https://github.com/guardian/membership-frontend")
    capabilities
  }

  val testUsersSecret = conf.getString("identity.test.users.secret")

  def stage(): String = {
    conf.getString("stage")
  }

  def debug() = {
    conf.root().render()
  }

  def printSummary(): Unit = {
    logger.info("=====================================")
    logger.info("Acceptance Test Configuration summary")
    logger.info("=====================================")
    logger.info(s"Stage: ${stage}")
    logger.info(s"Membership Frontend: ${Config.baseUrl}")
    logger.info(s"Identity Frontend: ${conf.getString("identity.webapp.url")}")
    logger.info(s"Identity API: ${conf.getString("identity.api.url")}")

  }
}
