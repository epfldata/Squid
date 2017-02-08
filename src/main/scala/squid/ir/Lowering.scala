package squid
package ir

import squid.utils.meta.RuntimeUniverseHelpers
import utils._

trait Lowering extends Transformer {
  val base: ir.AST with ClassEmbedder
  import base._
  
  val warnRecursiveEmbedding = true
  
  val loweredPhases: Set[Symbol]
  
  import ClassEmbedder.Error._
  def transform(rep: Rep): Rep = rep match {
    case RepDef(ma @ MethodApp(self, sym, targs, argss, typ)) if ma.phase exists loweredPhases =>
      val fullArgss = if (sym.owner.isModuleClass) argss
        else Args(self)::argss
      val fullTargs = (self.typ.tpe.typeArgs map (new TypeRep(_))) ++ targs
      methodDef(sym, fullTargs) match {
        case Right(IR(r)) =>
          //println(s"Lowering $ma with ${r|>showRep}")
          fullArgss.foldLeft(r) {
            case (r, Args(reps @ _*)) =>
              val ruh = RuntimeUniverseHelpers
              val typ = r.typ.typeArgs.last // TODO be careful that a method implemented without parameter list can implement a method with an empty list (eg toString), which can make this crash
              base.rep(MethodApp(r, ruh.FunctionType.symbol(reps.size).toType member ruh.sru.TermName("apply") asMethod, Nil, Args(reps:_*)::Nil, typ))
            case (r, args) => wth(s"$args") // TODO
          }
        case Left(Recursive) =>
          if (warnRecursiveEmbedding) System.err.println(s"Warning: Recursive value ${sym fullName} cannot be fully embedded.")
          rep
        case Left(Missing) =>
          System.err.println(s"Warning: Could not find definition for lowered method: ${sym fullName}${sym typeSignature} @ phase ${ma.phase get}") // TODO B/W
          rep
        case _ => spuriousWarning
      }
    case _ => rep
  }
  
}
trait OnlineDesugaring extends OnlineOptimizer with Lowering with ClassEmbedder { self: ir.AST with ClassEmbedder =>
  val loweredPhases = Set('Sugar)
}
