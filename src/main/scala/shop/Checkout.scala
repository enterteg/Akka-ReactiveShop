package shop

import akka.actor.{Actor, ActorRef, Timers}
import akka.event.LoggingReceive
import reactive2.{ExpirationTime, Shop}

object Checkout {
  case class SelectDelivery(delivery: String)
  case class SelectPayment(payment: String)
  case object CheckoutTimeExpired
  case object CheckoutTimeExpiredKey
  case object PaymentTimeExpired
  case object PaymentTimeExpiredKey
  case object PaymentReceived
  case object Done
  case object CheckoutClosed
  case object Failed
}


class Checkout extends Actor with Timers {

  import Checkout._

  var delivery: String = null
  var payment: String = null
  var checkoutTimerId: Unit = null


  def receive: Receive = LoggingReceive {

    case Shop.CheckoutStarted => {
      println("Checkout started")
      timers.startSingleTimer(CheckoutTimeExpiredKey, CheckoutTimeExpired, ExpirationTime.expirationTime)
      context become selectingDelivery(sender)
    }
  }

  def selectingDelivery(sender: ActorRef): Receive = LoggingReceive {

    case Checkout.CheckoutTimeExpired  => {
      println("Checkout time expired")
      sender ! Failed
      context.stop(self)
    }

    case Shop.CheckoutCanceled => {
      println("Checkout canceled")
      context.stop(self)
    }

    case Checkout.SelectDelivery(deliv) => {
      println(s"Checkout deliver selected $deliv")
      delivery = deliv
      context become selectingPayment(sender)
    }
  }

  def selectingPayment(sender: ActorRef): Receive = LoggingReceive {

    case Checkout.CheckoutTimeExpired => {
      println("CHECKOUT CANCELED")
      sender ! Failed
      context.stop(self)
    }

    case Shop.CheckoutCanceled =>
      context.stop(self)

    case Checkout.SelectPayment(pay) => {
      println(s"Checkout paymeny selected $pay")

      payment = pay
      timers.cancel(CheckoutTimeExpiredKey)
      timers.startSingleTimer(PaymentTimeExpiredKey, PaymentTimeExpired, ExpirationTime.expirationTime)
      context become processingPayment(sender)
    }
  }

  def processingPayment(sender: ActorRef): Receive = LoggingReceive {

    case Checkout.PaymentTimeExpired | Shop.CheckoutCanceled => {
      println("CHECKOUT CANCELED")
      sender ! Failed
      context.stop(self)
    }

    case Checkout.PaymentReceived => {
      println("Payment received")
      sender ! Done
      context.stop(self)
    }
  }
}