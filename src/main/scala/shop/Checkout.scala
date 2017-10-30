package shop

import akka.actor.{Actor, ActorRef, Props, Timers}
import akka.event.LoggingReceive
import reactive2.{Customer, ExpirationTime}

object Checkout {
  case object StartCheckout
  case class SelectDelivery(delivery: String)
  case class SelectPayment(payment: String)
  case class PaymentServiceStarted(paymentService: ActorRef)
  case object CheckoutTimeExpired
  case object CheckoutTimeExpiredKey
  case object PaymentTimeExpired
  case object PaymentTimeExpiredKey
  case object PaymentReceived
  case object Done
  case object CheckoutClosed
  case object Failed
}


class Checkout(cart: ActorRef) extends Actor with Timers {

  import Checkout._
  var delivery: String = null
  var payment: String = null
  var paymentService: ActorRef = null
  var checkoutTimerId: Unit = null


  def receive: Receive = LoggingReceive {
    case Checkout.StartCheckout => {
      println("Checkout started")
      timers.startSingleTimer(CheckoutTimeExpiredKey, CheckoutTimeExpired, ExpirationTime.expirationTime)
      context become selectingDelivery(sender)
    }
  }

  def selectingDelivery(customer: ActorRef): Receive = LoggingReceive {

    case Checkout.CheckoutTimeExpired  => {
      println("Checkout time expired")
      customer ! Failed
      context.stop(self)
    }

    case Customer.CheckoutCanceled => {
      println("Checkout canceled")
      context.stop(self)
    }

    case Checkout.SelectDelivery(deliv) => {
      println(s"Checkout deliver selected $deliv")
      delivery = deliv
      context become selectingPayment(customer)
    }
  }

  def selectingPayment(customer: ActorRef): Receive = LoggingReceive {

    case Checkout.CheckoutTimeExpired => {
      println("CHECKOUT CANCELED")
      customer ! Failed
      context.stop(self)
    }

    case Customer.CheckoutCanceled =>
      context.stop(self)

    case Checkout.SelectPayment(pay) => {
      println(s"Checkout paymeny selected $pay")
      paymentService = context.actorOf(Props(new PaymentService(self)), "PaymentService")
      customer ! Checkout.PaymentServiceStarted(paymentService)
      payment = pay
      timers.cancel(CheckoutTimeExpiredKey)
      timers.startSingleTimer(PaymentTimeExpiredKey, PaymentTimeExpired, ExpirationTime.expirationTime)
      context become processingPayment(customer)
    }
  }

  def processingPayment(customer: ActorRef): Receive = LoggingReceive {

    case PaymentService.PaymentReceived => {
      println("Payment received")
      customer ! Checkout.CheckoutClosed
      cart ! Checkout.CheckoutClosed
      context.stop(self)
    }

    case PaymentService.PaymentFailed => {
      println("Payment failed")
      customer ! Failed
      cart ! Failed
      context.stop(self)
    }
  }
}