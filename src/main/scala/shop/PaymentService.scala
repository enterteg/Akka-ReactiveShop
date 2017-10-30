package shop

import akka.actor.{Actor, ActorRef, Timers}
import akka.event.LoggingReceive
import reactive2.ExpirationTime

object PaymentService {
  case object PaymentConfirmed
  case object PaymentReceived
  case object PaymentFailed
  case object DoPayment
  case object PayApiCallTimerKey
  case object ApiResponseSuccess
  case object ApiResponseFailed
}

class PaymentService(checkout: ActorRef) extends Actor with Timers {
  import PaymentService._
  def receive = LoggingReceive {
    case PaymentService.DoPayment => {
      timers.startSingleTimer(PayApiCallTimerKey, ApiResponseSuccess, ExpirationTime.expirationTime)
      context become waitingForPaymentApi(sender)
    }
  }

  def waitingForPaymentApi(customer: ActorRef): Receive = LoggingReceive {
    case ApiResponseSuccess => {
      customer ! PaymentConfirmed
      checkout ! PaymentReceived
    }

    case ApiResponseFailed => {
      customer ! PaymentFailed
      checkout ! PaymentFailed
    }
  }
}
