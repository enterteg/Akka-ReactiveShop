package shop

import akka.actor.{ActorRef, FSM, Timers}
import reactive2.{ExpirationTime, Shop}

object CheckoutState {
  sealed trait State
  case object Initial extends State
  case object SelectingDelivery extends State
  case object SelectingPayment extends State
  case object ProcessingPayment extends State
}

object CheckoutData {
  sealed trait Data
  case object Uninitialized extends Data
  final case class CheckoutInfo(shop: ActorRef, delivery: String, payment: String) extends Data
}

class CheckoutFSM extends FSM[CheckoutState.State, CheckoutData.Data] with Timers {
  import CheckoutData._
  import CheckoutState._
  import Checkout._

  startWith(Initial, Uninitialized)

  when(Initial) {
    case Event(Shop.CheckoutStarted, Uninitialized) => {
      println("Checkout started")
      timers.startSingleTimer(CheckoutTimeExpiredKey, CheckoutTimeExpired, ExpirationTime.expirationTime)
      goto(SelectingDelivery) using CheckoutInfo(sender, null, null)
    }
  }

  when(SelectingDelivery) {
    case Event(CheckoutTimeExpired, checkoutInfo: CheckoutInfo) => {
      println("Checkout time expired")
      checkoutInfo.shop ! Checkout.Failed
      stop()
    }

    case Event(Shop.CheckoutCanceled, checkoutInfo: CheckoutInfo) => {
      println("Checkout canceled")
      stop()
    }

    case Event(SelectDelivery(deliv), checkoutInfo: CheckoutInfo) => {
      println(s"Checkout deliver selected $deliv")
      goto(SelectingPayment) using CheckoutInfo(checkoutInfo.shop, deliv, null)
    }

  }

  when(SelectingPayment) {
    case Event(CheckoutTimeExpired, checkoutInfo: CheckoutInfo) => {
      println("CHECKOUT CANCELED")
      checkoutInfo.shop ! Checkout.Failed
      stop()
    }

    case Event(Shop.CheckoutCanceled, checkoutInfo) => {
      stop()
    }

    case Event(Checkout.SelectPayment(payment), checkoutInfo: CheckoutInfo) => {
      println(s"Checkout paymeny selected $payment")
      timers.cancel(CheckoutTimeExpiredKey)
      timers.startSingleTimer(PaymentTimeExpiredKey, PaymentTimeExpired, ExpirationTime.expirationTime)
      goto(ProcessingPayment) using CheckoutInfo(checkoutInfo.shop, checkoutInfo.delivery, payment);
    }
  }

  when(ProcessingPayment) {
    case Event(PaymentTimeExpired | Shop.CheckoutCanceled, checkoutInfo: CheckoutInfo) => {
      println("CHECKOUT CANCELED")
      checkoutInfo.shop ! Failed
      stop()
    }
    case Event(Checkout.PaymentReceived, checkoutInfo: CheckoutInfo) => {
      println("Payment received")
      checkoutInfo.shop ! Checkout.Done
      stop()
    }
  }
}
