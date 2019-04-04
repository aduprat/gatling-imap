package com.linagora.gatling.imap.protocol.command

import java.util

import akka.actor.{ActorRef, Props}
import com.lafaspot.imapnio.channel.IMAPChannelFuture
import com.lafaspot.imapnio.client.IMAPSession
import com.lafaspot.imapnio.listener.{IMAPChannelFutureListener, IMAPCommandListener}
import com.linagora.gatling.imap.protocol._
import com.sun.mail.imap.protocol.IMAPResponse
import com.typesafe.scalalogging.Logger
import io.gatling.core.akka.BaseActor

import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

object LoginHandler {
  def props(session: IMAPSession, tag: Tag) = Props(new LoginHandler(session, tag))
}

class LoginHandler(session: IMAPSession, tag: Tag) extends BaseActor {

  override def receive: Receive = {
    case Command.Login(userId, user, password) =>
      val listener = new LoginListener(self, sender, logger, userId)
      logger.trace(s"LoginHandler for user : ${userId.value}, on actor ${self.path} responding to ${sender.path}")
      context.become(waitForLoggedIn(sender()))
      Try(session.executeLoginCommand(tag.string, user, password, listener)) match {
        case Success(futureResult) =>
          futureResult.addListener(new IMAPChannelFutureListener {
            override def operationComplete(future: IMAPChannelFuture): Unit = {
              logger.debug(s"LoginHandler command completed, success : ${future.isSuccess}")
              if (!future.isSuccess) {
                logger.error("LoginHandler command failed", future.cause())
              }
            }
          })
        case Failure(e) =>
          logger.error("ERROR when executing LOGIN COMMAND", e)
          throw e
      }
  }

  def waitForLoggedIn(sender: ActorRef): Receive = {
    case msg@Response.LoggedIn(response) =>
      logger.trace(s"LoginHandler respond to ${sender.path} with $msg")
      sender ! msg
      context.stop(self)
  }
}

private[command] class LoginListener(self: ActorRef, sender: ActorRef, logger: Logger, userId: UserId) extends IMAPCommandListener {

  import collection.JavaConverters._

  override def onMessage(session: IMAPSession, response: IMAPResponse): Unit = {
    logger.trace(s"Untagged message for $userId : ${response.toString}")
  }

  override def onResponse(session: IMAPSession, tag: String, responses: util.List[IMAPResponse]): Unit = {
    logger.trace(s"On response for $userId :\n  on actor ${self.path} to ${sender.path}")
    val response = ImapResponses(responses.asScala.to[Seq])
    logger.trace(s"On response for $userId :\n ${response.mkString("\n")}\n on actor ${self.path} to ${sender.path}")
    self ! Response.LoggedIn(response)
  }
}

