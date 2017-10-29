package shop

import akka.actor.{FSM, Timers}
import reactive2.{ExpirationTime, Customer}
import shop.Cart.{CartTimeExpirationKey, CartTimeExpired, CheckIfEmpty, Done}

object CartState {
  sealed trait State
  case object Empty extends State
  case object NonEmpty extends State
  case object InCheckout extends State
}

object CartData {
  sealed trait Data
  case object EmptyCart extends Data
  final case class ItemsList(items: Set[Item]) extends Data
}

class CartFSM extends FSM[CartState.State, CartData.Data] with Timers {
  import CartData._
  import CartState._

  startWith(Empty, EmptyCart)

  def startTimer = {
    println("Cart Timer started")
    timers.startSingleTimer(CartTimeExpirationKey, CartTimeExpired, ExpirationTime.expirationTime)
  }

  when(Empty) {
    case Event(Cart.AddItem(item), EmptyCart) => {
      println("Item added, cart non empty")
      startTimer
      goto(NonEmpty) using ItemsList(Set(item))
    }
    case Event(Cart.CheckIfEmpty, EmptyCart) => {
      sender ! Cart.Empty
      stay using EmptyCart
    }
  }

  when(NonEmpty) {
    case Event(Cart.AddItem(item), items @ ItemsList(itemsList)) => {
      println("Item added")
      val newList = itemsList + item
      println(newList)
      goto(NonEmpty) using ItemsList(newList)
    }

    case Event(Cart.RemoveItem(item), items @ ItemsList(itemsList)) => {
      val newList = itemsList - item
      println("Item removed")
      if (newList.isEmpty) {
        println("Cart timer canceled - cart is empty")
        timers.cancel(CartTimeExpirationKey)
        goto(Empty) using EmptyCart
      } else {
        stay using ItemsList(newList)
      }
    }

    case Event(Customer.CheckoutStarted, items) => {
      println("Cart timer canceled - go to checkout")
      timers.cancel(CartTimeExpirationKey)
      goto(InCheckout) using items
    }

    case Event(CartTimeExpired, items) => {
      println("Cart timer expired - go to Empty")
      goto(Empty) using EmptyCart
    }

    case Event(CheckIfEmpty, items) => {
      sender ! Cart.NonEmpty
      stay using items
    }
  }

  when(InCheckout) {
    case Event(Customer.CheckoutCanceled, items) => {
      startTimer
      goto(NonEmpty) using items
    }

    case Event(Customer.CheckoutClosed | CartTimeExpired, items) => {
      println("Cart -> Go to empty")
      goto(Empty) using EmptyCart
    }

    case Event(Done, items) => {
      println("Cart DONE")
      sender ! Done
      stop()
    }
  }

}
