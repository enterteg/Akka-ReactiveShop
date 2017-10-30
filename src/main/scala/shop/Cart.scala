package shop

import akka.actor.{Actor, ActorRef, Props, Timers}
import akka.event.LoggingReceive
import reactive2.{Customer, ExpirationTime}

case class Item(name: String, id: Int)
case class CartItem(item: Item, quantity: Int)

object Cart {
  // REVCEIVED EVENTS
  case class AddItem(item: Item)
  case class RemoveItem(item: Item)
  case object CheckIfEmpty
  // SENT EVENTS
  case object Empty
  case class CheckoutStarted(checkout: ActorRef)
  case object Done
  case object Failed
  case object CartTimeExpired
  case object CartTimeExpirationKey
  case object ItemAdded
  case object ItemRemoved
}


class Cart extends Actor with Timers {
  import Cart._
  var items = Map[Int, CartItem]()
  var checkout: ActorRef = null

  // ACTIONS
  def getQuantity(item: Item): Int = {
    val cartItemOpt = items.get(item.id)
    var quantity: Int = 0
    cartItemOpt match {
      case Some(cartItem) => quantity = cartItem.quantity
      case None => quantity = 0
    }
    quantity
  }

  def addItem(item: Item): Unit = {
    var quantity = getQuantity(item)
    items += (item.id -> CartItem(item, quantity + 1))
    println(items)
  }

  def removeItem(item: Item): Unit = {
    var quantity = getQuantity(item);
    if (quantity <= 1) {
      items -= item.id
    } else {
      items += (item.id -> CartItem(item, quantity - 1))
    }
    println(items)
  }

  def startTimer = {
    println("Cart Timer started")
    timers.startSingleTimer(CartTimeExpirationKey, CartTimeExpired, ExpirationTime.expirationTime)
  }

  // CONTEXT STATES

  def empty: Receive = LoggingReceive {
    case AddItem(item: Item) => {
      addItem(item)
      startTimer
      println("NON EMPTY")
      sender ! Cart.ItemAdded
      context become nonEmpty(sender)
    }
    case Customer.StartCheckout => sender ! Empty
    case _ => sender ! Failed
  }

  def nonEmpty(customer: ActorRef): Receive = LoggingReceive {
    case AddItem(item: Item) => {
      sender ! ItemAdded
      addItem(item)
    }

    case RemoveItem(item: Item) => {
      removeItem(item)
      sender ! ItemRemoved
      if (items.isEmpty) {
        println("Cart timer canceled")
        timers.cancel(CartTimeExpirationKey)
        sender ! Cart.Empty
        println("EMPTY")
        context become empty
      }
    }

    case Customer.StartCheckout => {
      checkout = context.actorOf(Props(new Checkout(self)), "Checkout")
      timers.cancel(CartTimeExpirationKey)
      println("IN CHECKOUT")
      checkout ! Checkout.StartCheckout
      customer ! Cart.CheckoutStarted(checkout)
      context become inCheckout(customer)
    }

    case CartTimeExpired => {
      println("Cart timer expired")
      println("EMPTY")
      context become empty
    }

    case _ => customer ! Failed
  }

  def inCheckout(customer: ActorRef): Receive = LoggingReceive {
    case Customer.CheckoutCanceled | Checkout.Failed => {
      startTimer
      context become nonEmpty(customer)
    }
    case Customer.CheckoutClosed=>
      customer ! Cart.Empty
      context become empty

    case Done => {
      context.stop(self)
    }
  }


  def receive: Receive = empty

}
