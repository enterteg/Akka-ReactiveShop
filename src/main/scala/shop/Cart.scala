package shop

import akka.actor.{Actor, ActorRef, Timers}
import akka.event.LoggingReceive
import reactive2.{ExpirationTime, Shop}

case class Item(name: String, id: Int)
case class CartItem(item: Item, quantity: Int)

object Cart {
  // REVCEIVED EVENTS
  case class AddItem(item: Item)
  case class RemoveItem(item: Item)
  case object CheckIfEmpty
  // SENT EVENTS
  case object Empty
  case object NonEmpty
  case object Done
  case object Failed
  case object CartTimeExpired
  case object CartTimeExpirationKey
}


class Cart extends Actor with Timers {
  import Cart._
  var items = Map[Int, CartItem]()

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
      context become nonEmpty(sender)
    }
    case CheckIfEmpty => sender ! Empty
    case _ => sender ! Failed
  }

  def nonEmpty(sender: ActorRef): Receive = LoggingReceive {
    case AddItem(item: Item) =>
      addItem(item)

    case RemoveItem(item: Item) => {
      removeItem(item)
      if (items.isEmpty) {
        println("Cart timer canceled")
        timers.cancel(CartTimeExpirationKey)
        println("EMPTY")
        context become empty
      }
    }

    case Shop.CheckoutStarted => {
      println("Cart timer canceled")
      timers.cancel(CartTimeExpirationKey)
      println("IN CHECKOUT")
      context become inCheckout(sender)
    }

    case CartTimeExpired => {
      println("Cart timer expired")
      println("EMPTY")
      context become empty
    }

    case CheckIfEmpty => sender ! NonEmpty

    case _ => sender ! Failed
  }

  def inCheckout(sender: ActorRef): Receive = LoggingReceive {
    case Shop.CheckoutCanceled => {
      startTimer
      context become nonEmpty(sender)
    }
    case Shop.CheckoutClosed=>
      context become empty

    case Done => {
      context.stop(self)
    }
  }


  def receive: Receive = empty

}
