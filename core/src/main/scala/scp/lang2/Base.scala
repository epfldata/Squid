package scp
package lang2

import utils.meta.RuntimeUniverseHelpers.sru

trait Base extends TypingBase with quasi2.QuasiBase {
  
  type Rep
  type BoundVal
  
  def bindVal(name: String, typ: TypeRep): BoundVal
  
  def readVal(v: BoundVal): Rep
  def const(value: Any): Rep // Note: not taking a sru.Constant as argument, because `const` also receives things like () -- Unit "literal"
  def lambda(params: List[BoundVal], body: => Rep): Rep
  
  /** Important to support packages that are not objects because of typeApp (that may take sthg like `java.lang` for `String`)
    * Note: for consistency, maybe we should separate into two methods package/module, where typeApp and module are based on a package... */
  def moduleObject(fullName: String, isPackage: Boolean): Rep // TODO rm; fix typeApp
  def staticModule(fullName: String): Rep
  def module(prefix: Rep, name: String, typ: TypeRep): Rep
  def newObject(tp: TypeRep): Rep
  def methodApp(self: Rep, mtd: MtdSymbol, targs: List[TypeRep], argss: List[ArgList], tp: TypeRep): Rep
  
  def byName(arg: => Rep): Rep
  
  
  type MtdSymbol
  
  /** Parameter `static` shpuld be true only for truly static methods (in the Java sense)
    * Note: index should be None when the symbol is not overloaded, to allow for more efficient caching */
  def loadMtdSymbol(typ: TypSymbol, symName: String, index: Option[Int] = None, static: Boolean = false): MtdSymbol
  
  
  
  val Const: ConstAPI
  trait ConstAPI { // TODO put in InspectableBase!
    def apply[T: sru.TypeTag](v: T): IR[T,{}] = `internal IR`(const(v))  // TODO rm TypeTag
    def unapply[T: sru.TypeTag](ir: IR[T,_]): Option[T]  // TODO make it IRType?
  }
  
  
  def repEq(a: Rep, b: Rep): Boolean
  
  
  
  implicit class RepOps(private val self: Rep) {
    def =~= (that: Rep) = repEq(self, that)
    def show = showRep(self)
  }
  
  
  def showRep(r: Rep) = r.toString
  
  
  
  // Helpers:
  
  /** Just a shortcut for methodApp */
  final def mapp(self: Rep, mtd: MtdSymbol, tp: TypeRep)(targs: TypeRep*)(argss: ArgList*): Rep =
    methodApp(self, mtd, targs.toList, argss.toList, tp)
  
  
  protected lazy val Function1ApplySymbol = loadMtdSymbol(loadTypSymbol("scala.Function1"), "apply")
  def app(fun: Rep, arg: Rep)(retTp: TypeRep): Rep = methodApp(fun, Function1ApplySymbol, Nil, Args(arg)::Nil, retTp)
  def letin(bound: BoundVal, value: Rep, body: => Rep, bodyType: TypeRep): Rep = {
    app(lambda(bound::Nil, body), value)(bodyType)
  }
  
  /** Override to give special meaning */
  def ascribe(self: Rep, typ: TypeRep): Rep = self
  
  
  
  sealed trait ArgList {
    def reps: Seq[Rep]
    //def extract(al: ArgList): Option[Extract] = (this, al) match {
    //  case (a0: Args, a1: Args) => a0 extract a1
    //  case (ArgsVarargs(a0, va0), ArgsVarargs(a1, va1)) => for {
    //    a <- a0 extract a1
    //    va <- va0 extractRelaxed va1
    //    m <- merge(a, va)
    //  } yield m
    //  case (ArgsVarargSpliced(a0, va0), ArgsVarargSpliced(a1, va1)) => for {
    //    a <- a0 extract a1
    //    va <- baseSelf.extract(va0, va1)
    //    m <- merge(a, va)
    //  } yield m
    //  case (ArgsVarargSpliced(a0, va0), ArgsVarargs(a1, vas1)) => for { // case dsl"List($xs*)" can extract dsl"List(1,2,3)"
    //    a <- a0 extract a1
    //    va <- baseSelf.spliceExtract(va0, vas1)
    //    m <- merge(a, va)
    //  } yield m
    //  case _ => None
    //}
    override def toString = show(_ toString)
    def show(rec: Rep => String, forceParens: Boolean = true) = {
      val strs = this match {
        case Args(as @ _*) => as map rec
        case ArgsVarargs(as, vas) => as.reps ++ vas.reps map rec
        case ArgsVarargSpliced(as, va) => as.reps.map(rec) :+ s"${rec(va)}: _*"
      }
      if (forceParens || strs.size != 1) strs mkString ("(",",",")") else strs mkString ","
    }
    def map(b: Base)(f: Rep => b.Rep): b.ArgList
  }
  case class Args(reps: Rep*) extends ArgList {
    def apply(vreps: Rep*) = ArgsVarargs(this, Args(vreps: _*))
    def splice(vrep: Rep) = ArgsVarargSpliced(this, vrep)
    
    //def extract(that: Args): Option[Extract] = {
    //  require(reps.size == that.reps.size)
    //  extractRelaxed(that)
    //}
    //def extractRelaxed(that: Args): Option[Extract] = {
    //  if (reps.size != that.reps.size) return None
    //  val args = (reps zip that.reps) map { case (a,b) => baseSelf.extract(a, b) }
    //  (Some(EmptyExtract) +: args).reduce[Option[Extract]] { // `reduce` on non-empty; cf. `Some(EmptyExtract)`
    //    case (acc, a) => for (acc <- acc; a <- a; m <- merge(acc, a)) yield m }
    //}
    def map(b: Base)(f: Rep => b.Rep): b.Args = b.Args(reps map f: _*)
  }
  object ArgList {
    def unapplySeq(x: ArgList) = x match {
      case Args(as @ _*) => Some(as)
      case ArgsVarargs(as, vas) => Some(as.reps ++ vas.reps)
      case ArgsVarargSpliced(_, _) => None
    }
  }
  case class ArgsVarargs(args: Args, varargs: Args) extends ArgList {
    val reps = args.reps ++ varargs.reps
    def map(b: Base)(f: Rep => b.Rep): b.ArgsVarargs = b.ArgsVarargs(args.map(b)(f), varargs.map(b)(f))
  }
  case class ArgsVarargSpliced(args: Args, vararg: Rep) extends ArgList {
    val reps = args.reps :+ vararg
    def map(b: Base)(f: Rep => b.Rep): b.ArgsVarargSpliced = b.ArgsVarargSpliced(args.map(b)(f), f(vararg))
  }
  
}












