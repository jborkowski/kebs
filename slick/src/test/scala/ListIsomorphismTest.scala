import org.scalatest.{FunSuite, Matchers}
import slick.lifted.Isomorphism

class ListIsomorphismTest extends FunSuite with Matchers {
  import pl.iterators.kebs._

  case class C(a: String)

  test("Case class isomorphism implies list isomorphism") {
    val iso = implicitly[Isomorphism[List[C], List[String]]]
    iso.map(List(C("a"), C("b"))) shouldBe List("a", "b")
    iso.comap(List("a", "b")) shouldBe List(C("a"), C("b"))
  }

  test("Case class isomorphism implies seq to list isomorphism") {
    val iso = implicitly[Isomorphism[Seq[C], List[String]]]
    iso.map(Seq(C("a"), C("b"))) shouldBe List("a", "b")
    iso.comap(List("a", "b")) shouldBe Seq(C("a"), C("b"))
  }
}
