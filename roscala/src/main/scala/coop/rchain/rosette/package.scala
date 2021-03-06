package coop.rchain

import coop.rchain.rosette.parser.bytecode.ParseError
import coop.rchain.rosette.prim.PrimError

import reflect.runtime.universe._
import reflect.runtime.currentMirror
import scala.annotation.tailrec
import scala.reflect.ClassTag

package object rosette {
  sealed trait RblError
  case object DeadThread extends RblError
  case object Invalid extends RblError
  case object Suspend extends RblError
  case object Absent extends RblError
  case object Upcall extends RblError
  case class PrimErrorWrapper(value: PrimError) extends RblError
  case class RuntimeError(msg: String) extends RblError

  type Result = Either[RblError, Ob]

  implicit class RichOb(ob: Ob) {
    def as[T <: Ob : ClassTag]: Option[T] = {
      ob match {
        case t: T => Some(t)
        case _ => None
      }
    }
  }

  implicit class OptionOps[R](opt: Option[R]) {
    def or[L](alt: => L): Either[L, R] = opt.toRight(alt)
  }

  def suicide(msg: String): Unit = {
    System.err.println(s"*** fatal error: $msg")
    System.exit(1)
  }

  trait Show[A] {
    def show(a: A): String
  }

  object Show {
    def apply[A](implicit sh: Show[A]): Show[A] = sh

    def show[A: Show](a: A): String = Show[A].show(a)

    implicit class ShowOps[A: Show](a: A) {
      def show: String = Show[A].show(a)
    }

    implicit val parseErrorShow: Show[ParseError] =
      _ match {
        case e => e.toString
      }

    implicit val opsShow: Show[Seq[Op]] = { ops =>
      ops
        .map { op =>
          val r = currentMirror.reflect(op)

          val name = op.getClass.getSimpleName
          val args = r.symbol.typeSignature.members.toStream
            .collect { case s: TermSymbol if !s.isMethod => r.reflectField(s) }
            .map { r =>
              r.symbol.name.toString.trim + ":" + r.get
            }

          name + " " + args.mkString(" ")
        }
        .mkString("\n")
    }
  }

  @tailrec
  def recMap[A](count: Int, l: A)(f: A => A): A = {
    if (count == 0) l else recMap(count-1, f(l))(f)
  }
}
