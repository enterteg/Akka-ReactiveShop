package reactive2

import akka.actor._
import akka.event.LoggingReceive

import scala.concurrent.Await
import scala.concurrent.duration._
import shop._

final case object ExpirationTime {
  val expirationTime = 5.seconds
}

object Customer {
  case object CheckoutClosed
  case object CheckoutCanceled
  case object StartCheckout
  case object Init
}

class Customer extends Actor {
  val cart = context.actorOf(Props[Cart], "cart")
  val items = List(Item("Komputer", 1), Item("Szczotka", 2), Item("Telefon", 3))

  def receive = LoggingReceive {
    case Customer.Init => {
      cart ! Cart.AddItem(items(0))
      cart ! Cart.RemoveItem(items(0))
      cart ! Cart.AddItem(items(0))
      cart ! Cart.AddItem(items(1))
      cart ! Cart.AddItem(items(2))
      cart ! Cart.RemoveItem(items(0))
      cart ! Cart.AddItem(items(2))
      cart ! Customer.StartCheckout
      context become awaitCheckout
    }
  }

  def awaitCheckout: Receive = LoggingReceive {
    case Cart.CheckoutStarted(checkout) =>
      context become inCheckout(checkout)

    case Cart.Empty => context become receive
  }

  def inCheckout(checkout: ActorRef): Receive = LoggingReceive {
    case Customer.CheckoutCanceled => {
      checkout ! Customer.CheckoutCanceled
      cart ! Customer.CheckoutCanceled
      context become receive
    }

    case Checkout.SelectDelivery(deliv) =>
      checkout ! Checkout.SelectDelivery(deliv)

    case Checkout.SelectPayment(payment) =>
      checkout ! Checkout.SelectPayment(payment)

    case Checkout.PaymentServiceStarted(paymentService) => {
      context become inPayment(paymentService)
    }

    case Checkout.PaymentReceived =>
      checkout ! Checkout.PaymentReceived

    case Checkout.Done => {
      cart ! Checkout.CheckoutClosed
      println("DONE WORK")
      context become receive
    }

    case Checkout.Failed => {
      cart ! Customer.CheckoutCanceled
      context become receive
    }
  }

  def inPayment(paymentService: ActorRef): Receive = LoggingReceive {
    case PaymentService.PaymentConfirmed => {
      println("Payment confirmed to customer")
      context become receive
    }
    case Cart.Empty => {
      context become receive
    }
  }
}

object ReactiveShop extends App {
  val system = ActorSystem("Reactive2")
  val mainActor = system.actorOf(Props[Customer], "ReactiveShop")

  def workingExample = {
    mainActor ! Customer.Init
    Thread.sleep(2000)
    mainActor ! Checkout.SelectDelivery("FedEx")
    Thread.sleep(2000)
    mainActor ! Checkout.SelectPayment("PayPal")
    Thread.sleep(2000)
    mainActor ! Checkout.PaymentReceived
  }

//  def cartTimeExpirtedExample = {
//    mainActor ! Customer.Init
//  }
//
//  def TimeExpirtedExample = {
//    mainActor ! Customer.Init
//  }
//
//  def checkoutCanceled = {
//    mainActor ! Customer.Init
//    Thread.sleep(2000)
//    mainActor ! Customer.CheckoutStarted
//    Thread.sleep(2000)
//    mainActor ! Customer.CheckoutCanceled
//  }
//
//  def paymentTimerExpired = {
//    mainActor ! Customer.Init
//    Thread.sleep(2000)
//    mainActor ! Customer.CheckoutStarted
//    Thread.sleep(2000)
//    mainActor ! Checkout.SelectDelivery("FedEx")
//    Thread.sleep(2000)
//    mainActor ! Checkout.SelectPayment("PayPal")
//  }

  workingExample
//  cartTimeExpirtedExample
//  checkoutCanceled
//  paymentTimerExpired

  Await.result(system.whenTerminated, Duration.Inf)
}