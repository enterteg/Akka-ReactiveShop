package shop

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import reactive2.Customer

class CartTestSync extends TestKit(ActorSystem("CartTestSync"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  val items = List(Item("Komputer", 1), Item("Szczotka", 2), Item("Telefon", 3))

  override def afterAll(): Unit = {
    system.terminate
  }

  "A Cart" must {
    "start in an empty state" in {
      val cart = TestActorRef[Cart]
      cart ! Customer.StartCheckout
      assert (cart.underlyingActor.items.isEmpty)
    }

    "receive item and go to nonEmpty state" in {
      val cart = TestActorRef[Cart]
      cart ! Cart.AddItem(items(0))
      assert (cart.underlyingActor.items.contains(items(0).id))
    }

    "remove item and go to empty state" in{
      val cart = TestActorRef[Cart]
      cart ! Cart.AddItem(items(0))
      cart ! Cart.RemoveItem(items(0))
      assert (cart.underlyingActor.items.isEmpty)
    }
  }
}

