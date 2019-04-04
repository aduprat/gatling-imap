package com.linagora.gatling.imap.protocol.command

import java.util

import akka.actor.{ActorRef, Props}
import com.lafaspot.imapnio.client.IMAPSession
import com.lafaspot.imapnio.listener.IMAPCommandListener
import com.linagora.gatling.imap.protocol._
import com.sun.mail.imap.protocol.IMAPResponse
import com.typesafe.scalalogging.Logger
import io.gatling.core.akka.BaseActor

import scala.collection.immutable.Seq

object SelectHandler {
  def props(session: IMAPSession, tag: Tag) = Props(new SelectHandler(session, tag))
}

class SelectHandler(session: IMAPSession, tag: Tag) extends BaseActor {

  override def receive: Receive = {
    case Command.Select(userId, mailbox) =>
      val listener = new SelectListener(self, sender, logger, userId)
      context.become(waitCallback(sender()))
      session.executeSelectCommand(tag.string, mailbox, listener)
  }

  def waitCallback(sender: ActorRef): Receive = {
    case msg@Response.Selected(response) =>
      sender ! msg
      context.stop(self)
  }

}

private[command] class SelectListener(self: ActorRef, sender: ActorRef, logger: Logger, userId: UserId) extends IMAPCommandListener {

  import collection.JavaConverters._

  override def onMessage(session: IMAPSession, response: IMAPResponse): Unit = {
    logger.trace(s"Untagged message for $userId : ${response.toString}")
  }

  override def onResponse(session: IMAPSession, tag: String, responses: util.List[IMAPResponse]): Unit = {
    val response = ImapResponses(responses.asScala.to[Seq])
    logger.trace(s"On response for $userId :\n ${response.mkString("\n")}\n ${sender.path}")
    self ! Response.Selected(response)
  }
}

