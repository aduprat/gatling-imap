package com.linagora.gatling.imap.protocol.command

import java.util

import akka.actor.{ActorRef, Props}
import com.lafaspot.imapnio.client.IMAPSession
import com.lafaspot.imapnio.listener.IMAPCommandListener
import com.linagora.gatling.imap.protocol.{Command, ImapResponses, Response, Tag, UserId}
import com.sun.mail.imap.protocol.IMAPResponse
import com.typesafe.scalalogging.Logger
import io.gatling.core.akka.BaseActor

import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

object ListHandler {
  def props(session: IMAPSession, tag: Tag) = Props(new ListHandler(session, tag))
}

class ListHandler(session: IMAPSession, tag: Tag) extends BaseActor {

  override def receive: Receive = {
    case Command.List(userId, reference, name) =>
      val listener = new ListListener(self, sender, logger, userId)
      context.become(waitCallback(sender()))
      Try(session.executeListCommand(tag.string, reference, name, listener)) match {
        case Success(value) =>
        case Failure(e) =>
          logger.error("ERROR when executing LIST COMMAND", e)
          throw e;
      }


  }

  def waitCallback(sender: ActorRef): Receive = {
    case msg@Response.Listed(response) =>
      sender ! msg
      context.stop(self)
  }

}


private[command] class ListListener(self: ActorRef, sender: ActorRef, logger: Logger, userId: UserId) extends IMAPCommandListener {

  import collection.JavaConverters._

  override def onMessage(session: IMAPSession, response: IMAPResponse): Unit = {
    logger.trace(s"Untagged message for $userId : ${response.toString}")
  }

  override def onResponse(session: IMAPSession, tag: String, responses: util.List[IMAPResponse]): Unit = {
    val response = ImapResponses(responses.asScala.to[Seq])
    logger.trace(s"On response for $userId :\n ${response.mkString("\n")}\n ${sender.path}")
    self ! Response.Listed(response)
  }
}

