package squid
package lang

import utils._
import ir.Variance
import squid.ir.Transformer
import squid.utils.TraceDebug

import scala.annotation.StaticAnnotation

/** An Inspectable Base is one that supports code pattern-matching, and therefore more generally code transformation */
/* TODO proper error if user tries to do QQ matching with a Base that does not extend this */
trait InspectableBase extends IntermediateBase with quasi.QuasiBase with TraceDebug { baseSelf =>
  
  type Rep <: AnyRef  // AnyRef bound so we can 'eq' them and optimize traversals that leave subtrees identical
  
  /** Used by online rewritings; should be applied to all created IR nodes */
  def postProcess(r: Rep): Rep = r
  
  def bottomUp(r: Rep)(f: Rep => Rep): Rep
  def bottomUpPartial(r: Rep)(f: PartialFunction[Rep, Rep]): Rep = bottomUp(r)(r => f applyOrElse (r, identity[Rep]))
  
  def topDown(r: Rep)(f: Rep => Rep): Rep
  
  // Important: do not forget to override these to have better traversal performance (without useless tree reconstruction!) 
  def traverseTopDown(f: Rep => Unit)(r: Rep): Unit = topDown(r)(_ alsoApply f)
  def traverseBottomUp(f: Rep => Unit)(r: Rep): Unit = bottomUp(r)(_ alsoApply f)
  
  def rewriteRep(xtor: Rep, xtee: Rep, code: Extract => Option[Rep]): Option[Rep] =
    extractRep(xtor, xtee) flatMap (merge(_, repExtract(SCRUTINEE_KEY -> xtee))) flatMap code
  
  // Don't think this is still used anywhere:
  def disableRewritingsFor[A](r: => A): A = r
  
  protected def extract(xtor: Rep, xtee: Rep): Option[Extract]
  protected def spliceExtract(xtor: Rep, t: Args): Option[Extract]
  
  val Const: ConstAPI
  trait ConstAPI extends super.ConstAPI {
    def unapply[T: IRType](ir: IR[T,_]): Option[T]
  }
  
  
  /** The top-level function called by quasiquotes extractors */
  def extractRep(xtor: Rep, xtee: Rep): Option[Extract] = {
    import Console.BOLD
    import Console.RESET
    debug(s"${BOLD}Extracting$RESET $xtee ${BOLD}with$RESET $xtor")
    nestDbg(extract(xtor, xtee)) alsoApply (res => debug(s"${BOLD}Result:$RESET $res"))
  }
  
  def `internal checkExtract`(position: String, maps: Extract)(valKeys: String*)(typKeys: String*)(splicedValKeys: String*): Extract = {
    val prnt = (s: Traversable[_]) => s mkString ("{", ",", "}")
    //def keySets = s"{ ${valKeys.toSet}; ${typKeys.toSet}; ${flatValKeys.toSet} }" // Scala bug java.lang.VerifyError: Bad type on operand stack
    val keySets = () => s"( ${prnt(valKeys)}; ${prnt(typKeys)}; ${prnt(splicedValKeys)} )"
    
    assert(maps._1.keySet == valKeys.toSet, "Extracted term keys "+prnt(maps._1.keySet)+" do not correspond to specified keys "+keySets())//+valKeys.toSet)
    assert(maps._3.keySet == splicedValKeys.toSet, "Extracted spliced term keys "+prnt(maps._3.keySet)+" do not correspond to specified keys "+keySets())//+flatValKeys.toSet)
    //assert(maps._2.keySet == typKeys.toSet, "Extracted type keys "+maps._2.keySet+" do not correspond to specified keys "+typKeys)
    val xkeys = maps._2.keySet
    val keys = typKeys.toSet
    assert(xkeys -- keys isEmpty, "Unexpected extracted type keys "+(xkeys -- keys)+", not in specified keys "+keySets())//+keys)
    val noExtr = keys -- xkeys
    val plur = "s" * (noExtr.size - 1)
    
    // This is obsolete. All type holes should be assigned an extracted type, be it the Nothing..Any interval. 
    if (noExtr nonEmpty) System.err.print( // not 'println' since the position String contains a newLine
      s"""Warning: no type representations were extracted for type hole$plur: ${prnt(noExtr map ("$"+_))}
         |  Perhaps the type hole$plur ${if (plur isEmpty) "is" else "are"} in the position of an unconstrained GADT type parameter where the GADT is matched contravariantly...
         |${position}""".stripMargin)
    ( maps._1, maps._2 ++ (noExtr map (k => k -> typeHole(s"<error $k>"))), maps._3 ) // probably not safe to return a hole here, but at this point we're screwed anyway...
  }
  
  def extractType(xtor: TypeRep, xtee: TypeRep, va: Variance): Option[Extract]
  
  
  implicit class InspectableCodeOps[T](private val self: Code[T]) extends InspectableIROps[T,Any](self.asClosedIR) // TODO make it the other way around? or just separate it
  implicit class InspectableIROps[T,C](private val self: IR[T,C]) {
    import scala.language.experimental.macros
    import squid.utils.MacroUtils.MacroSetting
    
    /** Note: this is only a top-level call to `base.extractRep`; not supposed to be called in implementations of `extract` itself */
    def extractRep(that: IR[_,_]) = self.rep extractRep that.rep
    
    // TODO take the Transformer as an implicit (w/ default arg?) -- currently it arbitrarily uses a new SimpleRuleBasedTransformer with TopDownTransformer
    def rewrite(tr: IR[Any,utils.UnknownContext] => IR[Any,_]): IR[T,_ <: C] = macro ir.RuleBasedTransformerMacros.termRewrite
    @MacroSetting(debug = true) def dbg_rewrite(tr: IR[Any,utils.UnknownContext] => IR[Any,_]): IR[T,_ <: C] = macro ir.RuleBasedTransformerMacros.termRewrite
    @RecRewrite def fix_rewrite(tr: IR[Any,utils.UnknownContext] => IR[Any,_]): IR[T,_ <: C] = macro ir.RuleBasedTransformerMacros.termRewrite
    
    @inline final def analyse(pf: PartialFunction[IR[T,_ <: C],Unit]): Unit = analyseTopDown(pf)
    def analyseTopDown(pf: PartialFunction[IR[T,_ <: C],Unit]): Unit = 
      traverseTopDown(r => pf.runWith(identity)(IR(r)) thenReturn Unit)(self.rep)
    def analyseBottomUp(pf: PartialFunction[IR[T,_ <: C],Unit]): Unit = 
      traverseBottomUp(r => pf.runWith(identity)(IR(r)) thenReturn Unit)(self.rep)
    
  }
  protected implicit class ProtectedInspectableRepOps(private val self: Rep) {
    def extract (that: Rep) = baseSelf.extractRep(self, that)
  }
  implicit class InspectableRepOps(private val self: Rep) {
    /** Note: this is only a to-level call to `base.extractRep`; not supposed to be called in implementations of `extract` itself */
    def extractRep (that: Rep) = baseSelf.extractRep(self, that)
  }
  implicit class InspectableTypeRepOps(private val self: TypeRep) {
    def extract (that: TypeRep, va: Variance) = baseSelf.extractType(self, that, va)
  }
  
  def extractArgList(self: ArgList, other: ArgList): Option[Extract] = {
    def extractRelaxed(slf: Args, oth: Args): Option[Extract] = {
      import slf._
      if (reps.size != oth.reps.size) return None
      val args = (reps zip oth.reps) map { case (a,b) => baseSelf.extract(a, b) }
      (Option(EmptyExtract) /: args) {
        case (acc, a) => for (acc <- acc; a <- a; m <- merge(acc, a)) yield m }
    }
    import self._
    (self, other) match {
      case (a0: Args, a1: Args) =>
        require(reps.size == other.reps.size)
        extractRelaxed(a0,a1)
      case (ArgsVarargs(a0, va0), ArgsVarargs(a1, va1)) => for {
        a <- extractArgList(a0, a1)
        va <- extractRelaxed(va0,va1)
        m <- merge(a, va)
      } yield m
      case (ArgsVarargSpliced(a0, va0), ArgsVarargSpliced(a1, va1)) => for {
        a <- extractArgList(a0, a1)
        va <- baseSelf.extract(va0, va1)
        m <- merge(a, va)
      } yield m
      case (ArgsVarargSpliced(a0, va0), ArgsVarargs(a1, vas1)) => for { // case dsl"List($xs*)" can extract dsl"List(1,2,3)"
        a <- extractArgList(a0, a1)
        va <- baseSelf.spliceExtract(va0, vas1)
        m <- merge(a, va)
      } yield m
      case _ => None
    }
  }
  
  
  trait SelfTransformer extends ir.Transformer {
    val base: baseSelf.type = baseSelf
  }
  class TransformerWrapper(others: ir.Transformer{val base: baseSelf.type}*) extends SelfTransformer {
    def transform(rep: Rep): Rep = {
      others.foldLeft(rep)((r,t) => t transform r)
    }
  }
  object IdentityTransformer extends SelfTransformer with ir.IdentityTransformer
  trait SelfIRTransformer extends SelfTransformer with ir.IRTransformer
  
  private[squid] case class EarlyReturnAndContinueExc(cont: (Rep => Rep) => Rep) extends Exception
  
  private def earlyReturnDebug(x: => Any) = debug(s"${Console.RED}EARLY RETURN!${Console.RESET} -- $x")
  
  def `internal return`[T,C](x: IR[T,C]): IR[T,C] = {
    earlyReturnDebug(x)
    throw new EarlyReturnAndContinueExc(_ => x.rep)
  }
  def `internal return transforming`[A,CA,T,C](a: IR[A,CA])(f: IR[A,CA] => IR[T,C]): Nothing = {
    earlyReturnDebug(a)
    throw new EarlyReturnAndContinueExc(tr => f(IR(a.rep |> tr)).rep)
  }
  def `internal return transforming`[A,CA,B,CB,T,C](a: IR[A,CA], b: IR[B,CB])(f: (IR[A,CA], IR[B,CB]) => IR[T,C]): Nothing = {
    earlyReturnDebug(a,b)
    throw new EarlyReturnAndContinueExc(tr => f(IR(a.rep |> tr),IR(b.rep |> tr)).rep)
  }
  def `internal return transforming`[A,CA,B,CB,D,CD,T,C](a: IR[A,CA], b: IR[B,CB], d: IR[D,CD])(f: (IR[A,CA], IR[B,CB], IR[D,CD]) => IR[T,C]): Nothing = {
    earlyReturnDebug(a,b,d)
    throw new EarlyReturnAndContinueExc(tr => f(IR(a.rep |> tr),IR(b.rep |> tr),IR(d.rep |> tr)).rep)
  }
  def `internal return transforming`[A,CA,T,C](as: List[IR[A,CA]])(f: List[IR[A,CA]] => IR[T,C]): Nothing = {
    earlyReturnDebug(as)
    throw new EarlyReturnAndContinueExc(tr => f(as map (_.rep |> tr |> IR.apply)).rep)
  }
  def `internal return recursing`[T,C](cont: Transformer{val base: baseSelf.type} => IR[T,C]): IR[T,C] = {
    earlyReturnDebug(cont)
    throw new EarlyReturnAndContinueExc(tr => cont(new SelfTransformer { def transform(rep: base.Rep) = tr(rep) }).rep)
  }
  
}

private[squid] class RecRewrite extends StaticAnnotation


