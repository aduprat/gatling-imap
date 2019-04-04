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

object AppendHandler {
  def props(session: IMAPSession, tag: Tag) = Props(new AppendHandler(session, tag))
}

class AppendHandler(session: IMAPSession, tag: Tag) extends BaseActor {

  override def receive: Receive = {
    case Command.Append(userId, mailbox, flags, date, content) =>
      if (!date.isEmpty) throw new NotImplementedError("Date parameter for APPEND is still not implemented")
      val listener = new AppendListener(self, sender, logger,userId, content)
      val flagsAsString = flags.map(_.mkString("(", " ", ")")).getOrElse("()")
      val length = content.length + content.lines.length - 1
      logger.debug(s"APPEND receive from sender ${sender.path} on ${self.path}")
      context.become(waitCallback(sender()))
      Try(session.executeAppendCommand(tag.string, mailbox, flagsAsString, length.toString, listener)) match {
        case Success(futureResult) =>
          futureResult.addListener(new IMAPChannelFutureListener {
            override def operationComplete(future: IMAPChannelFuture): Unit = {
              logger.debug(s"AppendHandler command completed, success : ${future.isSuccess}")
              if (!future.isSuccess) {
                logger.error("AppendHandler command failed", future.cause())
              }

            }
          })
        case Failure(e) =>
          logger.error("ERROR when executing APPEND COMMAND", e)
          throw e
      }
  }

  def waitCallback(sender: ActorRef): Receive = {
    case msg@Response.Appended(response) =>
      logger.debug(s"APPEND reply to sender ${sender.path}")
      sender ! msg
      context.stop(self)
  }



}
private[command] class AppendListener(self: ActorRef, sender: ActorRef, logger : Logger, userId: UserId, content: String) extends IMAPCommandListener {

  import collection.JavaConverters._

  override def onMessage(session: IMAPSession, response: IMAPResponse): Unit = {
    logger.trace(s"Untagged message for $userId : ${response.toString}")
    if (response.isContinuation) {
      content.lines.foreach { textCommand =>
        logger.trace(s"execute APPEND TEXT COMMAND : $textCommand")
        Try(session.executeRawTextCommand(textCommand)) match {
          case Success(value) => //DO NOTHING
          case Failure(e) =>
            logger.error("ERROR when executing APPEND TEXT COMMAND", e)
            throw e
        }

      }
    }
  }

  override def onResponse(session: IMAPSession, tag: String, responses: util.List[IMAPResponse]): Unit = {
    logger.trace(s"On response for $userId :\n  on actor ${self.path} to ${sender.path}")
    val response = ImapResponses(responses.asScala.to[Seq])
    logger.trace(s"On response for $userId :\n ${response.mkString("\n")}\n on actor${self.path} to ${sender.path}")
    self ! Response.Appended(response)
  }
}

