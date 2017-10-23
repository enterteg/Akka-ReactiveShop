package reactive2

import akka.actor._
import akka.event.LoggingReceive

import scala.concurrent.Await
import scala.concurrent.duration._
import shop._

final case object ExpirationTime {
  val expirationTime = 5.seconds
}

object Shop {
  case object CheckoutStarted
  case object CheckoutClosed
  case object CheckoutCanceled
  case object Init
}

class Shop extends Actor {
  val cart = context.actorOf(Props[CartFSM], "cart")
  var checkout: ActorRef = null
  val items = List(Item("Komputer", 1), Item("Szczotka", 2), Item("Telefon", 3))

  def receive = LoggingReceive {
    case Shop.Init => {
      cart ! Cart.AddItem(items(0))
      cart ! Cart.RemoveItem(items(0))
      cart ! Cart.AddItem(items(0))
      cart ! Cart.AddItem(items(1))
      cart ! Cart.AddItem(items(2))
      cart ! Cart.RemoveItem(items(0))
      cart ! Cart.AddItem(items(2))
    }
    case Shop.CheckoutStarted => {
      cart ! Cart.CheckIfEmpty
      context become awaitCheckout
    }
  }

  def awaitCheckout: Receive = LoggingReceive {
    case Cart.NonEmpty =>
      startCheckout

    case Cart.Empty => context become receive
  }

  def startCheckout = {
    this.checkout = context.actorOf(Props[CheckoutFSM], "Checkout")
    cart ! Shop.CheckoutStarted
    checkout ! Shop.CheckoutStarted
    context become inCheckout
  }

  def inCheckout: Receive = LoggingReceive {
    case Shop.CheckoutCanceled => {
      checkout ! Shop.CheckoutCanceled
      cart ! Shop.CheckoutCanceled
      context become receive
    }

    case Checkout.SelectDelivery(deliv) =>
      checkout ! Checkout.SelectDelivery(deliv)

    case Checkout.SelectPayment(payment) =>
      checkout ! Checkout.SelectPayment(payment)

    case Checkout.PaymentReceived =>
      checkout ! Checkout.PaymentReceived

    case Checkout.Done => {
      cart ! Checkout.CheckoutClosed
      println("DONE WORK")
      context become receive
    }

    case Checkout.Failed => {
      cart ! Shop.CheckoutCanceled
      context become receive
    }
  }
}

object ReactiveShop extends App {
  val system = ActorSystem("Reactive2")
  val mainActor = system.actorOf(Props[Shop], "ReactiveShop")

  def workingExample = {
    mainActor ! Shop.Init
    Thread.sleep(2000)
    mainActor ! Shop.CheckoutStarted
    Thread.sleep(2000)
    mainActor ! Checkout.SelectDelivery("FedEx")
    Thread.sleep(2000)
    mainActor ! Checkout.SelectPayment("PayPal")
    Thread.sleep(2000)
    mainActor ! Checkout.PaymentReceived
  }

  def cartTimeExpirtedExample = {
    mainActor ! Shop.Init
  }

  def TimeExpirtedExample = {
    mainActor ! Shop.Init
  }

  def checkoutCanceled = {
    mainActor ! Shop.Init
    Thread.sleep(2000)
    mainActor ! Shop.CheckoutStarted
    Thread.sleep(2000)
    mainActor ! Shop.CheckoutCanceled
  }

  def paymentTimerExpired = {
    mainActor ! Shop.Init
    Thread.sleep(2000)
    mainActor ! Shop.CheckoutStarted
    Thread.sleep(2000)
    mainActor ! Checkout.SelectDelivery("FedEx")
    Thread.sleep(2000)
    mainActor ! Checkout.SelectPayment("PayPal")
  }

//  workingExample
//  cartTimeExpirtedExample
//  checkoutCanceled
  paymentTimerExpired

  Await.result(system.whenTerminated, Duration.Inf)
}