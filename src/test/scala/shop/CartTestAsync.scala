package shop

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import reactive2.Customer

class CartTestAsync extends TestKit(ActorSystem("CartTestAsync"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  val items = List(Item("Komputer", 1), Item("Szczotka", 2), Item("Telefon", 3))

  override def afterAll(): Unit = {
    system.terminate
  }

  "A Cart" must {

    "start in an empty state" in {
      val cart = system.actorOf(Props[Cart])
      // cart will response for customer checkout
      // with Empty message if cart is empty
      // so we can use it to check this test
      cart ! Customer.StartCheckout
      expectMsg(Cart.Empty)
    }

    "receive item and go to nonEmpty state" in {
      val cart = system.actorOf(Props[Cart])
      cart ! Cart.AddItem(items(0))
      expectMsg(Cart.ItemAdded)
    }

    "remove item and go to empty state" in{
      val cart = system.actorOf(Props[Cart])
      cart ! Cart.AddItem(items(0))
      expectMsg(Cart.ItemAdded)
      cart ! Cart.RemoveItem(items(0))
      expectMsg(Cart.ItemRemoved)
      expectMsg(Cart.Empty)
    }

  }
}

