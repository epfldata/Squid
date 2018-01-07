package squid
package ir.fastanf

import squid.ir._
import squid.lang.{Base, InspectableBase, ScalaCore}
import squid.utils._

import scala.collection.immutable.{ListMap, ListSet}

/**
  * ANF representation of the code. 
  * The IR is mutable in order to be able to do O(1) substitutions.
  */
class FastANF extends InspectableBase with CurryEncoding with StandardEffects with ScalaCore {
  private[this] implicit val base = this
  
  
  // * --- * --- * --- *  Basic Definitions * --- * --- * --- *
  
  type Rep = ir.fastanf.Rep
  type TypeRep = ir.fastanf.TypeRep
  type BoundVal = ir.fastanf.Symbol
  type TypSymbol = TypeSymbol
  type MtdSymbol = MethodSymbol
  
  
  // * --- * --- * --- *  Reification  * --- * --- * --- *
  
  var scopes: List[ReificationContext] = Nil

  /**
    * Runs the thunk `r`, appends it to the the last let-binding in the reification context,
    * and returns the first let-binding.
    */ 
  @inline final def wrap(r: => Rep, inXtor: Bool): Rep = {
    val scp = new ReificationContext(inXtor)
    scopes ::= scp
    try scp.finalize(r)
    finally scopes = scopes.tail
  }
  @inline final def wrapNest(r: => Rep): Rep = {
    wrap(r, currentScope.inExtractor)
  }
  override final def wrapConstruct(r: => Rep): Rep = wrap(super.wrapConstruct(r), false)
  override final def wrapExtract(r: => Rep): Rep = wrap(super.wrapExtract(r), true)
  
  @inline final def currentScope = scopes.head
  
  def toArgumentLists(argss: List[ArgList]): ArgumentLists = {
    // Note: some arguments may be let-bindings (ie: blocks), which is only possible if they are by-name arguments
    
    def toArgumentList(args: Seq[Rep]): ArgumentList =
      args.foldRight(NoArguments: ArgumentList)(_ ~: _)
    def toArgumentListWithSpliced(args: Seq[Rep])(splicedArg: Rep) =
      args.foldRight(SplicedArgument(splicedArg): ArgumentList)(_ ~: _)
    
    argss.foldRight(NoArgumentLists: ArgumentLists) {
      (args, acc) => args match {
        case Args(as @ _*) => toArgumentList(as) ~~: acc
        case ArgsVarargs(Args(as @ _*), Args(bs @ _*)) => toArgumentList(as ++ bs) ~~: acc // ArgVararg ~converted as Args!
        case ArgsVarargSpliced(Args(as @ _*), s) => toArgumentListWithSpliced(as)(s) ~~: acc
      }
    }
  }
  
  def toListOfArgList(argss: ArgumentLists): List[ArgList] = {
    def toArgList(args: ArgumentList): List[Rep] -> Option[Rep] = args match {
      case NoArguments => Nil -> None
      case SplicedArgument(a) => Nil -> Some(a) // Everything after spliced argument is ignored.
      case r : Rep => List(r) -> None
      case ArgumentCons(h, t) =>
        val (rest, spliced) = toArgList(t)
        (h :: rest) -> spliced
    }

    argss match {
      case ArgumentListCons(h, t) =>
        val (args, spliced) = toArgList(h)
        val _args = Args(args: _*)
        spliced.fold(_args: ArgList)(s => ArgsVarargSpliced(_args, s)) :: toListOfArgList(t)
      case NoArgumentLists => Nil
      case SplicedArgument(spliced) => List(ArgsVarargSpliced(Args(), spliced)) // Not sure
      case ac : ArgumentCons =>
        val (args, spliced) = toArgList(ac)
        val _args = Args(args: _*)
        spliced.fold(_args: ArgList)(s => ArgsVarargSpliced(_args, s)) :: Nil
      case NoArguments => Nil
      case r : Rep => List(Args(r))
    }
  }
  
  
  // * --- * --- * --- *  Implementations of `Base` methods  * --- * --- * --- *
  
  def bindVal(name: String, typ: TypeRep, annots: List[Annot]): BoundVal = new UnboundSymbol(name,typ)
  def readVal(bv: BoundVal): Rep = curSub getOrElse (bv, bv)
  def const(value: Any): Rep = Constant(value)
  
  // Note: method `lambda(params: List[BoundVal], body: => Rep): Rep` is implemented by CurryEncoding
  def abs(param: BoundVal, mkBody: => Rep): Rep = {
    val body = wrapNest(mkBody)
    new Lambda(param.name, param, param.typ, body).alsoApply(param rebind _) |> letbind
  }
  def funType(paramTyp: TypeRep, ret: TypeRep): TypeRep = lambdaType(paramTyp :: Nil, ret)
  def lambdaType(paramTyps: List[TypeRep], ret: TypeRep): TypeRep = DummyTypeRep
  
  def staticModule(fullName: String): Rep = StaticModule(fullName)
  def module(prefix: Rep, name: String, typ: TypeRep): Rep = Module(prefix, name, typ)
  def newObject(typ: TypeRep): Rep = NewObject(typ)
  def methodApp(self: Rep, mtd: MtdSymbol, targs: List[TypeRep], argss: List[ArgList], tp: TypeRep): Rep = mtd match {
    // Converts the call to `Imperative` to let-bindings
    case MethodSymbol(TypeSymbol("squid.lib.package$"), "Imperative") => argss match {
      case List(h, t) =>
        val holes = h.reps.filter {
          case Hole(_, _) => true
          case _ => false
        }

        val lastArgss = t.reps
        assert(lastArgss.size == 1)
        holes.foldRight(lastArgss.head) { case (h, acc) =>
          letin(bindVal("tmp", h.typ, Nil), h, acc, acc.typ)
        }
    }

    case _ => MethodApp(self |> inlineBlock, mtd, targs, argss |> toArgumentLists, tp) |> letbind
  }
  def byName(mkArg: => Rep): Rep = ByName(wrapNest(mkArg))

  /**
    * Let-bind `d` and add it the current reification scope.
    */
  def letbind(d: Def): Rep = currentScope += d

  /**
    * Adds all the statements of `r` to the current reification context.
    * Returns the final non-statement unchanged.
    */
  def inlineBlock(r: Rep): Rep = r |>=? {
    case lb: LetBinding =>
      currentScope += lb
      inlineBlock(lb.body)
  }

  /**
    * Let binds `value` to `bound` in the `body`.
    */
  override def letin(bound: BoundVal, value: Rep, body: => Rep, bodyType: TypeRep): Rep = value match {
    case s: Symbol =>
      s.owner |>? {
        case lb: RebindableBinding =>
          lb.name = bound.name
      }
      s.owner |>? {
        case lb: LetBinding =>
          lb.isUserDefined = true
      }
      withSubs(bound, value)(body)

    //s.owner |>? {
    //  case lb: RebindableBinding =>
    //    lb.name = bound.name
    //}
    //bound rebind s
    //body
      
    case lb: LetBinding =>
      // conceptually, does like `inlineBlock`, but additionally rewrites `bound` and renames `lb`'s last binding
      val last = lb.last
      val boundName = bound.name
      bound rebind last.bound
      last.body = body
      last.name = boundName // TODO make sure we're only renaming an automatically-named binding?
      lb

    case h: Hole =>
      val dh = DefHole(h) |> letbind
      withSubs(bound -> dh)(body)

    //(dh |>? {
    //  case bv: BoundVal => bv.owner |>? {
    //    case lb: LetBinding =>
    //      lb.body = body
    //      lb
    //  }
    //}).flatten.getOrElse(body)

    //new LetBinding(bound.name, bound, dh, body) alsoApply (currentScope += _) alsoApply (bound.rebind)

    case (_:HOPHole) | (_:HOPHole2) | (_:SplicedHole) =>
      ??? // TODO holes should probably be Def's; note that it's not safe to do a substitution for holes
    case _ =>
      withSubs(bound -> value)(body)
    // ^ executing `body` will reify some statements into the reification scope, and likely return a symbol
    // during this reification, we need all references to `bound` to be replaced by the actual `value`
  }

  var curSub: Map[Symbol,Rep] = Map.empty

  /**
    * Substitutes the [[Symbol]]s in `k` with the based on the mappings in [[curSub]] and `subs`. 
    */
  def withSubs[R](subs: Symbol -> Rep)(k: => R): R = {
    val oldSub = curSub
    curSub += subs
    try k finally curSub = oldSub
  }

  override def tryInline(fun: Rep, arg: Rep)(retTp: TypeRep): Rep = fun match {
    case lb: LetBinding => lb.value match {
      case l: Lambda => letin(l.bound, arg, l.body, l.body.typ)
      case _ => super.tryInline(fun, arg)(retTp)
    }
    case _ => super.tryInline(fun, arg)(retTp)
  }

  override def ascribe(self: Rep, typ: TypeRep): Rep = if (self.typ =:= typ) self else self match {
    case Ascribe(trueSelf, _) => Ascribe(trueSelf, typ) // Hopefully Scala's subtyping is transitive!
    case _ => Ascribe(self, typ)
  }

  def loadMtdSymbol(typ: TypSymbol, symName: String, index: Option[Int] = None, static: Boolean = false): MtdSymbol = MethodSymbol(typ, symName) // TODO

  object Const extends ConstAPI {
    def unapply[T: IRType](ir: IR[T,_]): Option[T] = ir.rep match {
      case cst @ Constant(v) if typLeq(cst.typ, irTypeOf[T].rep) => Some(v.asInstanceOf[T])
      case _ => None
    }
  }
  
  def repEq(a: Rep, b: Rep): Boolean =
    (a extractRep b) === Some(EmptyExtract) && (b extractRep a) === Some(EmptyExtract)


  // * --- * --- * --- *  Implementations of `IntermediateBase` methods  * --- * --- * --- *

  def nullValue[T: IRType]: IR[T,{}] = IR[T, {}](const(null)) // FIXME: should implement proper semantics; e.g. nullValue[Int] == ir"0", not ir"null"
  def reinterpret(r: Rep, newBase: Base)(extrudedHandle: BoundVal => newBase.Rep): newBase.Rep = {
    def reinterpret0: Rep => newBase.Rep = r => reinterpret(r, newBase)(extrudedHandle)
    def reinterpretType: TypeRep => newBase.TypeRep = t => newBase.staticTypeApp(newBase.loadTypSymbol("scala.Any"), Nil)
    def reinterpretBV:BoundVal => newBase.BoundVal = bv => newBase.bindVal(bv.name, reinterpretType(bv.typ), Nil)
    def reinterpretTypSym(t: TypeSymbol): newBase.TypSymbol = newBase.loadTypSymbol(t.name)
    def reinterpretMtdSym(s: MtdSymbol): newBase.MtdSymbol = newBase.loadMtdSymbol(reinterpretTypSym(s.typ), s.name)
    def reinterpretArgList(argss: ArgumentLists): List[newBase.ArgList] = toListOfArgList(argss) map {
      case ArgsVarargSpliced(args, varargs) => newBase.ArgsVarargSpliced(args.map(newBase)(reinterpret0), reinterpret0(varargs))
      case ArgsVarargs(args, varargs) => newBase.ArgsVarargs(args.map(newBase)(reinterpret0), varargs.map(newBase)(reinterpret0))
      case args : Args => args.map(newBase)(reinterpret0)
    }
    def defToRep(d: Def): newBase.Rep = d match {
      case app @ App(f, a) => newBase.app(reinterpret0(f), reinterpret0(a))(reinterpretType(app.typ))
      case ma : MethodApp => newBase.methodApp(
        reinterpret0(ma.self),
        reinterpretMtdSym(ma.mtd),
        ma.targs.map(reinterpretType),
        reinterpretArgList(ma.argss),
        reinterpretType(ma.typ))
      case l: Lambda => newBase.lambda(List(reinterpretBV(l.bound)), reinterpret0(l.body))
      case DefHole(h) => newBase.hole(h.name, reinterpretType(h.typ))
    }

    r match {
      case Constant(v) => newBase.const(v)
      case StaticModule(fn) => newBase.staticModule(fn)
      case NewObject(t) => newBase.newObject(reinterpretType(t))
      case Hole(n, t) => newBase.hole(n, reinterpretType(t))
      case SplicedHole(n, t) => newBase.splicedHole(n, reinterpretType(t))
      case Ascribe(s, t) => newBase.ascribe(reinterpret0(s), reinterpretType(t))
      case HOPHole(n, t, args, visible) => newBase.hopHole(
        n,
        reinterpretType(t),
        args.map(_.map(reinterpretBV)),
        visible.map(reinterpretBV))
      case HOPHole2(n, t, args, visible) => newBase.hopHole2(
        n,
        reinterpretType(t),
        args.map(_.map(reinterpret0)),
        visible.map(reinterpretBV)
      )
      case Module(p, n, t) => newBase.module(reinterpret0(p), n, reinterpretType(t))
      case lb: LetBinding => newBase.letin(
        reinterpretBV(lb.bound),
        defToRep(lb.value),
        reinterpret0(lb.body),
        reinterpretType(lb.typ))
      case s: Symbol => extrudedHandle(s)
      case ByName(r) => newBase.byName(reinterpret0(r))
    }

  }
  def repType(r: Rep): TypeRep = r.typ
  def boundValType(bv: BoundVal): TypeRep = bv.typ
  
  
  // * --- * --- * --- *  Implementations of `InspectableBase` methods  * --- * --- * --- *

  def extractType(xtor: TypeRep, xtee: TypeRep, va: squid.ir.Variance): Option[Extract] = Some(EmptyExtract) //unsupported
  def bottomUp(r: Rep)(f: Rep => Rep): Rep = transformRep(r)(identity, f)
  def topDown(r: Rep)(f: Rep => Rep): Rep = transformRep(r)(f)
  
  def transformRep(r: Rep)(pre: Rep => Rep, post: Rep => Rep = identity): Rep = {
    def transformRep0(r: Rep) = transformRep(r)(pre, post)
    
    def transformDef(d: Def): Either[Rep, Def] = d match {
      case ma: MethodApp => 
        Left(MethodApp.toANF(transformRep0(ma.self), ma.mtd, ma.targs, ma.argss argssMap transformRep0, ma.typ))
      case l: Lambda =>
        l.body = l.body |> transformRep0
        Right(l)
      case _ => Right(d)
    }
    
    post(pre(r) match {
      case lb: LetBinding =>
        lb.value |> transformDef match {
          case Right(d) =>
            lb.value = d
            lb.body = lb.body |> transformRep0
            lb
          case Left(r) => LetBinding.withRepValue(lb.name, lb.bound, r, lb.body |> transformRep0)
        }
      case ByName(r) => ByName(transformRep0(r))
      case Ascribe(s, t) => Ascribe(transformRep0(s), t)
      case Module(p, n, t) => Module(transformRep0(p), n, t)
      case r @ (_: Constant | _: Hole | _: Symbol | _: SplicedHole | _: HOPHole | _: HOPHole2 | _: NewObject | _: StaticModule) => r
    })
  }

  protected def extract(xtor: Rep, xtee: Rep): Option[Extract] = extractWithState(xtor, xtee)(State.forExtraction(xtor, xtee)).toOption map (_.ex)

  // Context is mapping from xtor BVs to xtee BVs
  type Ctx = Map[BoundVal, BoundVal]

  // * --- * --- * --- *  Extraction State  * --- * --- * --- *

  /**
    * Mapping of failed matches between xtor BVs and xtee BVs.
    */
  type Failed = Map[BoundVal, Set[BoundVal]]
  
  /**
    * Signals if the current extraction attempt has failed. 
    * `Left` returns the matchings that failed (xtor -> xtee),
    * `Right` the update state after a successful extraction.
    */
  type ExtractState = Either[Set[(BoundVal, BoundVal)], State]

  /* Helper functions for `ExtractState` */
  def succeed(implicit es: State): ExtractState = Right(es)
  def fail = Left(Set.empty[(BoundVal, BoundVal)])
  def failWith(failed: Set[(BoundVal, BoundVal)]): ExtractState = Left(failed)
  
  implicit def rightBias[A, B](e: Either[A, B]): Either.RightProjection[A,B] = e.right

  /**
    * Represents the current of the state of the extraction.
    * @param ex what has been extracted by holes.
    * @param ctx discovered matchings between bound values in the `xtor` and the `xtee`.
    * @param instructions what has to be done for each let-binding
    * @param matchedImpureBVs impure statements that have been matched
    * @param failed statements that do not match
    * @param strategy see [[Strategy]]
    */
  case class State(ex: Extract, ctx: Ctx, instructions: Instructions, matchedImpureBVs: Set[BoundVal],
                   failed: Failed, strategy: Strategy) {
    private val _strategy = strategy
    private val _instructions = instructions
    
    def withNewExtract(newEx: Extract): State = copy(ex = newEx)
    def withCtx(newCtx: Ctx): State = copy(ctx = newCtx)
    def withCtx(p: (BoundVal, BoundVal)): State = copy(ctx = ctx + p)

    /**
      * Adds all the BVs referencing impure statements in `r` to `matchedImpureBVs`.
      */
    def withMatchedImpures(r: Rep): State = r match {
      case lb: LetBinding if !isPure(lb.value) => copy(matchedImpureBVs = matchedImpureBVs + lb.bound) withMatchedImpures lb.body
      case lb: LetBinding => this withMatchedImpures lb.body
      case _ => this // Everything else is pure so we ignore it
    } 
    def withMatchedImpure(bv: BoundVal): State = copy(matchedImpureBVs = matchedImpureBVs + bv)
    
    def withFailed(newFailed: Set[(BoundVal, BoundVal)]): State = {
      val updatedFailed = newFailed.foldLeft(failed) { case (mergedF, (k, v)) =>
        mergedF + (k -> mergedF.get(k).map(_ + v).getOrElse(Set(v)))
      }
      copy(failed = updatedFailed)
    }
    
    def withStrategy(s: Strategy): State = copy(strategy = s)
    def withDefaultStrategy: State = copy(strategy = _strategy)
    
    def withInstructionsFor(r: Rep): State = copy(instructions = instructions.copy(flags = instructions.flags ++ Instructions.gen(r)))
    def withDefaultInstructions: State = copy(instructions = _instructions)
    
    def updateExtractWith(e: Option[Extract]*)(implicit default: State): ExtractState = {
      mergeAll(Some(ex) +: e).fold[ExtractState](fail)(ex => succeed(this withNewExtract ex))
    }
  }
  
  object State {
    def forExtraction(xtor: Rep, xtee: Rep): State = apply(xtor, xtee, CompleteMatching)
    def forRewriting(xtor: Rep, xtee: Rep): State = apply(xtor, xtee, PartialMatching)
    
    private def apply(xtor: Rep, xtee: Rep, strategy: Strategy): State = 
      State(EmptyExtract, ListMap.empty, Instructions(xtor, xtee), Set.empty, Map.empty, strategy)
  }

  


  // * --- * --- * --- *  Strategy  * --- * --- * --- *

  /**
    * Specifies the semantics of the extraction.
    */
  sealed trait Strategy

  /**
    * Allows the return value of the `xtor` to match anywhere in `xtee`. 
    * This will potentially leave some of the statement of the `xtee` unmatched.
    * For instance, this [[Strategy]] is necessary for rewriting as we may only be rewriting parts of the `xtee`.
    */
  case object PartialMatching extends Strategy
  
  /**
    * Enforces the fact that `xtor` has to fully match the `xtee`. 
    * (Enforced by the extraction algorithm but having a final check might be a good idea)
    * For instance, this [[Strategy]] is necessary for extraction as 
    * the pattern `xtor` has to match the entire `xtee`.
    */
  case object CompleteMatching extends Strategy




  // * --- * --- * --- *  Instructions  * --- * --- * --- *
  
  /**
    * Specifies what the extraction should do.
    */
  sealed trait Instruction

  /**
    * Instructs the extraction has to look for a matching statements.
    * This instruction is attached to impure statements as well as pure statements 
    * that are not used by other statements. Giving this instructions to those two cases
    * means that the unused statements are handled the same way. So, even though they are pure, 
    * unused statements will be matched in order.
    */
  case object Start extends Instruction

  /**
    * Instructs the extraction can ignore this statement.
    * This instruction is attached to all pure statements that are used by the return value.
    */
  case object Skip extends Instruction
  
  
  case class Instructions(flags: Set[BoundVal]) {
    def get(bv: BoundVal): Instruction = if (flags contains bv) Start else Skip
  }
  
  object Instructions {
    def apply(xtor: Rep, xtee: Rep): Instructions = Instructions(gen(xtor) ++ gen(xtee))

    /**
      * Generates the instructions that will be flagged with [[Start]].
      * These will include the impure statements and the unused statements.
      */
    def gen(r: Rep): Set[BoundVal] = {
      def update(d: Def, unusedBVs: Set[BoundVal], impures: Set[BoundVal]): (Set[BoundVal], Set[BoundVal]) = d match {
        case l: Lambda => genInstructions0(l.body, unusedBVs, impures)
        
        case ma: MethodApp => ((ma.self :: ma.argss.argssList).foldLeft(unusedBVs) {
          case (acc, bv: BoundVal) => acc - bv
          case (acc, _) => acc
        }, impures)
          
        case _ => (unusedBVs, impures)
      }
      
      /*
       * The unused BVs are kept separate from the impures even thought both will be merged at the end in order to be
       * able to keep track of the unused statements when recursing in `r`.
       */
      def genInstructions0(r: Rep, unusedBVs: Set[BoundVal], impures: Set[BoundVal]): (Set[BoundVal], Set[BoundVal]) = r match {
        case lb: LetBinding =>
          val updated = update(
            lb.value,
            unusedBVs + lb.bound,
            effect(lb.value) match {
              case Pure => impures
              case Impure => impures + lb.bound
            }
          )
          genInstructions0(lb.body, updated._1, updated._2)
        
        case bv: BoundVal => (unusedBVs - bv, impures)

        case ByName(r) => genInstructions0(r, unusedBVs, impures)
          
        case _ => (unusedBVs, impures)
      }

      val instructions = genInstructions0(r, Set.empty, Set.empty)
      instructions._1 ++ instructions._2
    }
  }
  
  def extractWithState(xtor: Rep, xtee: Rep)(implicit es: State): ExtractState = {
    def extractHOPHole(name: String, typ: TypeRep, argss: List[List[Rep]], visible: List[BoundVal])(implicit es: State): ExtractState = {
      def hasNoUndeclaredUsages(r: Rep): Boolean = {
        def hasNoUndeclaredUsages0(r: Rep, declared: Set[BoundVal]): Boolean = r match {
          case bv: BoundVal => declared contains bv
          case lb: LetBinding =>
            val declared0 = declared + lb.bound
            defHasNoUndeclaredUsages(lb.value, declared0) && hasNoUndeclaredUsages0(lb.body, declared0)
          case _ => true
        }

        def defHasNoUndeclaredUsages(d: Def, declared: Set[BoundVal]): Boolean = d match {
          case l: Lambda => hasNoUndeclaredUsages0(l.body, declared + l.bound)
          case ma: MethodApp => (ma.self :: ma.argss.argssList) forall (hasNoUndeclaredUsages0(_, declared))
          case _ => true
        }

        hasNoUndeclaredUsages0(r, Set.empty)
      }

      /**
        * Attemps to find the `xtors` in the body of the function and replaces them with newly generated arguments, 
        * adding the new arguments to the function. Even if the `xtors` are not found in the body, 
        * arguments representing them will be generated and added to it. They will simply not appear in the function's body. 
        */
      def buildFunc(xtors: List[Rep], maybeFuncAndState: Option[(Rep, State)]): Option[(Rep, State)] = {
        val args = xtors.map(arg => bindVal("hopArg", arg.typ, Nil))
        val transformations = xtors zip args

        /**
          * Returns the body of function. 
          * This is the body of the most deeply nested [[Lambda]] since the function is curried. 
          */
        def body(func: Rep): Rep = {
          def body0(d: Def): Option[Rep] = d match {
            case l: Lambda => l.body match {
              case lb: LetBinding => Some(body(lb))
              case body => Some(body)
            }
            case _ => None
          }
          
          func match {
            case lb: LetBinding => body0(lb.value) getOrElse lb
            case _ => func
          }
        }
        
        for {
          (f, es) <- maybeFuncAndState

          newBodyAndState = transformations.foldLeft(body(f) -> es) {
            case ((body, es), (xtor, arg)) => xtor match {
              case bv: BoundVal =>
                /*
                 * Assumes the bv is already in the context. 
                 * This is enforced by how the instructions are chosen.
                 */
                es.ctx(bv) rebind arg
                body -> es
                
              case lb: LetBinding =>

                /**
                  * Replaces all occurrences of `lb` in the body by `arg`.
                  */
                def replaceAllOccurrences(body: Rep)(es: State): Rep -> State = {
                  def replaceAllOccurrences0(body: Rep)(implicit es: State): Rep -> State = {
                    def filterLBs(r: Rep)(p: LetBinding => Boolean): Rep = r match {
                      case lb: LetBinding if p(lb) => filterLBs(lb.body)(p)
                      case lb: LetBinding =>
                        lb.body = filterLBs(lb.body)(p)
                        lb
                      case _ => r
                    }

                    /*
                     * Replaces every occurrence of `lb` in the body with `arg`.
                     */
                    // TODO single pass
                    extractWithState(lb, body) map { es0 =>
                      val replace =  es0.ctx(lb.last.bound)
                      val body0  = bottomUpPartial(filterLBs(body)(es0.ctx.values.toSet contains _.bound)) { case `replace` => arg }
                      replaceAllOccurrences0(body0)
                    } getOrElse body -> es
                  }
                  
                  // We only want to extract `lb` and not rewrite it so the strategy is changed to `PartialMatching`.
                  replaceAllOccurrences0(body)(es withInstructionsFor lb withStrategy PartialMatching)
                    .mapSecond(_.withDefaultInstructions.withDefaultStrategy) // Reset the strategy so it resume doing a `CompleteMatching`.
                }
                
                replaceAllOccurrences(body)(es)

              // TODO implement this when we allow holes in HOPHoles
              // case Hole(n, t) => ???
                
              case _ => bottomUpPartial(body) { case `xtor` => arg } -> es
            }
          }
          
          // The body of the extracted function should not contains any references to elements of `visible`.
          _ = bottomUpPartial(newBodyAndState._1) { case bv: BoundVal if visible contains bv => return None }
        } yield newBodyAndState match {
          case (func0, es0) => wrapConstruct(lambda(args, func0)) -> es0
        }
      }
      
      val maybeES = for {
        es1 <- typ extract (xtee.typ, Covariant)
        m <- merge(es.ex, es1)

        /**
          * The arguments of HOPHoles are _always_ passed by-name
          */
        argss0 = argss.map(_.map {
          case ByName(r) => r 
          case _ => die // All HOPHole args have to be by-name!
        })
        
        (f, es2) <- argss0.foldRight(Option(xtee -> (es withNewExtract m)))(buildFunc)
        //                     ^ so arguments are in the right order
        
        if hasNoUndeclaredUsages(f)
      } yield es2 updateExtractWith Some(repExtract(name -> f))
      
      maybeES getOrElse fail
    }

    def extractLBs(lb1: LetBinding, lb2: LetBinding)(implicit es: State): ExtractState = {
      def extractAndContinue(lb1: LetBinding, lb2: LetBinding)(implicit es: State): ExtractState = for {
        es1 <- extractWithState(lb1.bound, lb2.bound)
        es2 <- extractWithState(lb1.body, lb2.body)(es1)
      } yield es2
      
      (es.instructions.get(lb1.bound), es.instructions.get(lb2.bound)) match {
        case (Start, Start) => extractAndContinue(lb1, lb2)

        /**
          * In the following case `aX` has to be extracted since when extracting the 
          * HOPHole `h` the BV has to be matched before it (see reason in [[extractHOPHole]].
          * 
          * XTOR: ir"val aX = 10.toDouble; $h(aX)"
          *          \-------Start------/
          * XTEE: ir"val a = 10.toDouble; a + 1"
          *          \------Skip------/
          */
        case (Start, Skip) => for {
          failed1 <- extractAndContinue(lb1, lb2).left
          failed2 <- extractWithState(lb1, lb2.body)(es withFailed failed1).left
        } yield failed1 ++ failed2

        case (Skip, Start) => extractWithState(lb1.body, lb2)
        
        case (Skip, Skip) => extractWithState(lb1.body, lb2.body)
      }
    }

    def extractHole(h: Hole, r: Rep)(implicit es: State): ExtractState = h match {
      case Hole(n, t) =>
        es.updateExtractWith(
          t extract(xtee.typ, Covariant),
          Some(repExtract(n -> r))
        ) map (_ withMatchedImpures r)
    }

    /**
      * Attemps to extract `r` by trying to match it with the component of `d`. 
      * Only extracts inside a [[MethodApp]], fails for all other cases.
      * It won't extract inside the [[ByName]] arguments. 
      */
    def extractInside(r: Rep, d: Def)(implicit es: State): ExtractState = d match {
      case ma: MethodApp => (ma.self :: ma.argss.argssList).foldLeft[ExtractState](fail) { case (acc, arg) =>
        for {
          failed1 <- acc.left
          failed2 <- extractWithState(r, arg)(es withFailed failed1).left
        } yield failed1 ++ failed2
      }
      case _ => fail
    }
     

    def extractedBy(h: Hole)(implicit es: State): Option[Rep] = es.ex._1 get h.name
    
    xtor -> xtee match {
      case (h: Hole, _) => extractedBy(h) match {
        case Some(`xtee`) => succeed
        case Some(_) => die // Something has gone wrong
        case None => extractHole(h, xtee)
      }

      case (HOPHole2(name, typ, argss, visible), _) => extractHOPHole(name, typ, argss, visible)

      case (lb1: LetBinding, lb2: LetBinding) => extractLBs(lb1, lb2)

      case (lb: LetBinding, _) => es.instructions.get(lb.bound) match {
        case Start => fail // The `xtor` has more impure statements than the `xtee`.
        case Skip => extractWithState(lb.body, xtee)
      }

      case (bv: BoundVal, lb: LetBinding) =>
        if (es.ctx.keySet contains bv) succeed // `bv` has already been extracted
        else es.strategy match {
          case CompleteMatching => es.instructions.get(lb.bound) match {
            
            // The `xtee` has more impure statements or dead-ends than the `xtor`
            case Start => fail 
            
            // The skipped statements of the `xtee` will be matched later 
            // when extracting its return value or the `Start` ones. 
            case Skip => extractWithState(bv, lb.body) 
          }

          // Attempts to extract the return value `bv` by trying
          // 1. The current let-binding
          // 2. The components of the let-bindings value
          // 3. (1. and then 2.) on the next statement  
          case PartialMatching => for {
            failed1 <- extractWithState(bv, lb.bound).left
            failed2 <- extractInside(bv, lb.value)(es withFailed failed1).left
            failed3 <- extractWithState(bv, lb.body)(es withFailed (failed1 ++ failed2)).left
          } yield failed1 ++ failed2 ++ failed3
        }

      case (Constant(()), _: LetBinding) => es.strategy match {
        // Unit return doesn't have to matched
        case PartialMatching => succeed
        case CompleteMatching => fail
      }

        
      case (_: Rep, lb: LetBinding) => es.strategy match {
        case PartialMatching => for {
          failed1 <- extractInside(xtor, lb.value).left
          failed2 <- extractWithState(xtor, lb.body)(es withFailed failed1).left
        } yield failed1 ++ failed2
          
        case CompleteMatching => fail
      }
        
      // Only a [[ByName]] can extract a [[ByName]] so that 
      // the rewriting will rewrite inside the [[ByName]].
      case (ByName(r1), ByName(r2)) => extractWithState(r1, r2)
        
      case (_, Ascribe(s, _)) => extractWithState(xtor, s)

      case (Ascribe(s, t), _) => for {
        es1 <- es.updateExtractWith(t extract(xtee.typ, Covariant))
        es2 <- extractWithState(s, xtee)(es1)
      } yield es2

      case (HOPHole(name, typ, argss, visible), _) => extractHOPHole(name, typ, argss, visible)

      // The actual extraction happens here.  
      case (bv1: BoundVal, bv2: BoundVal) => es.ctx.get(bv1) map { extractedByBV1 =>
          if (extractedByBV1 == bv2) succeed
          else fail
        } getOrElse {
          if (bv1 == bv2) succeed
          else if (es.failed.getOrElse(bv1, Set.empty) contains bv2) fail // Previously failed to extract `bv1` with `bv2`
          else (bv1.owner, bv2.owner) match {
            case (lb1: LetBinding, lb2: LetBinding) => extractDefs(lb1.value, lb2.value) match {
              case Right(es) => effect(lb2.value) match {
                case Pure => succeed(es withCtx lb1.bound -> lb2.bound)
                case Impure => succeed(es withCtx lb1.bound -> lb2.bound withMatchedImpure lb2.bound)
              }
              case Left(failed) => failWith(failed + (lb1.bound -> lb2.bound))
            }
            case (l1: Lambda, l2: Lambda) => 
              // We cannot know the owner of the [[Lambda]] 
              // so in case of a failure to extract there's nothing to do.
              extractDefs(l1, l2) map (_ withCtx l1.bound -> l2.bound)
            case _ => failWith(Set(bv1 -> bv2))
          }
        }

      case (Constant(v1), Constant(v2)) if v1 == v2 => es updateExtractWith (xtor.typ extract(xtee.typ, Covariant))

      // Assuming if they have the same name the type is the same
      case (StaticModule(fn1), StaticModule(fn2)) if fn1 == fn2 => succeed

      // Assuming if they have the same name and prefix the type is the same
      case (Module(p1, n1, _), Module(p2, n2, _)) if n1 == n2 => extractWithState(p1, p2)

      case (NewObject(t1), NewObject(t2)) => es updateExtractWith (t1 extract(t2, Covariant))

      case _ => fail
      }
  }
  
  protected def spliceExtract(xtor: Rep, args: Args): Option[Extract] = xtor match {
    // Should check that type matches, but don't see how to access it for Args
    case SplicedHole(n, _) => Some(Map(), Map(), Map(n -> args.reps))

    case Hole(n, t) =>
      val rep = methodApp(
        staticModule("scala.collection.Seq"),
        loadMtdSymbol(
          loadTypSymbol("scala.collection.generic.GenericCompanion"),
          "apply",
          None),
        List(t),
        List(Args()(args.reps: _*)),
        staticTypeApp(loadTypSymbol("scala.collection.Seq"), List(t)))
      Some(repExtract(n -> rep))

    case _ => throw IRException(s"Trying to splice-extract with invalid extractor $xtor")
  }

  def extractDefs(v1: Def, v2: Def)(implicit es: State): ExtractState = (v1, v2) match {
    case (l1: Lambda, l2: Lambda) => for {
      es1 <- es updateExtractWith (l1.boundType extract(l2.boundType, Covariant))
      es2 <- extractWithState(l1.body, l2.body)(es1 withCtx l1.bound -> l2.bound withStrategy CompleteMatching)
    } yield es2 withDefaultStrategy

    case (ma1: MethodApp, ma2: MethodApp) if ma1.mtd == ma2.mtd =>
      def targExtract(es0: State): ExtractState =
        es0.updateExtractWith((for {
          (e1, e2) <- ma1.targs zip ma2.targs
        } yield e1 extract(e2, Invariant)): _*)

      def extractArgss(argss1: ArgumentLists, argss2: ArgumentLists)(implicit es: State): ExtractState = (argss1, argss2) match {
        case (ArgumentListCons(h1, t1), ArgumentListCons(h2, t2)) => for {
          es0 <- extractArgss(h1, h2)
          es1 <- extractArgss(t1, t2)(es0)
        } yield es1

        case (ArgumentCons(h1, t1), ArgumentCons(h2, t2)) => for {
          es0 <- extractArgss(h1, h2)
          es1 <- extractArgss(t1, t2)(es0)
        } yield es1

        case (SplicedArgument(arg1), SplicedArgument(arg2)) => extractWithState(arg1, arg2)
        case (SplicedArgument(arg), ac: ArgumentCons) => es updateExtractWith spliceExtract(arg, Args(ac.argssList: _*))
        case (SplicedArgument(arg), r: Rep) => es updateExtractWith spliceExtract(arg, Args(r))
        case (SplicedArgument(_), NoArguments) => succeed

        case (NoArguments, NoArguments) => succeed
        case (NoArgumentLists, NoArgumentLists) => succeed
        
        case (r1: Rep, r2: Rep) => extractWithState(r1, r2)
        
        case _ => fail
      }

      for {
        es1 <- extractWithState(ma1.self, ma2.self)
        es2 <- targExtract(es1)
        es3 <- extractArgss(ma1.argss, ma2.argss)(es2)
        es4 <- es3.updateExtractWith(ma1.typ extract (ma2.typ, Covariant))
      } yield es4

    // Assuming a [[DefHole]] only extracts impure statements  
    case (DefHole(h), _) if !isPure(v2) => extractWithState(h, wrapConstruct(letbind(v2)))

    case _ => fail
  }
  
  override def rewriteRep(xtor: Rep, xtee: Rep, code: Extract => Option[Rep]): Option[Rep] = 
    rewriteRep0(xtor, xtee, code)(State.forRewriting(xtor, xtee))

  def rewriteRep0(xtor: Rep, xtee: Rep, code: Extract => Option[Rep])(implicit es: State): Option[Rep] = {
    def rewriteRepWithState(xtor: Rep, xtee: Rep)(implicit es: State): ExtractState = (xtor, xtee) match {
      case (lb1: LetBinding, lb2: LetBinding) =>

        /**
          * Pure statements (annotated with the instruction [[Skip]] only have to extracted starting from their
          * return value and extract each sub-part recursively. Through this mechanism the order of the pure statements
          * does not matter.
          * For instance, this will successfully match : 
          *   {{{
          *   ir"val b = 22.toDouble; val a = 11.toDouble; a + b" match {
          *     case ir"val aX = 11.toDouble; val bX = 22.toDouble; aX + bX" => ???
          *   }
          *   }}}
          *   
          */
        (es.instructions.get(lb1.bound), es.instructions.get(lb2.bound)) match {
          
          /**
            * The traversal of the code is done externally by [[transformRep()]]. 
            * Hence, if the current statements don't have to be extracted at this point (both are pure and not return values)
            * we simply skip the extraction of the current `xtee`. 
            */
          case (Skip, Skip) => fail  
            
          case _ => extractWithState(lb1, lb2)
        }
        
      case _ => extractWithState(xtor, xtee)
    }

    def genCode(implicit es: State): Option[Rep] = {

      /**
        * Returns true if all the BV usages appearing in the extraction are declared inside the extraction and, if not,
        * that it has been declared by the user.
        * For instance:
        * {{{
        * ir"val r = readInt; r + 1" rewrite {
        *   case ir"val rX = readInt; $body" => ???
        * }
        * }}}
        * `$body` will extract `ir"r + 1"` where `r` <-> `rX`. Since the let-binding `val rX = readInt; ...` 
        * is user-defined.
        */
      def preCheck(ex: Extract): Boolean = {
        def preCheckRep(declaredBVs: Set[BoundVal], invCtx: Map[BoundVal, Set[BoundVal]], r: Rep): Boolean = {
          def preCheckDef(declaredBVs: Set[BoundVal], invCtx: Map[BoundVal, Set[BoundVal]], d: Def): Boolean = {
            d match {
              case l: Lambda => preCheckRep(declaredBVs, invCtx, l.body)
              case ma: MethodApp => (ma.self :: ma.argss.argssList) forall {
                case bv: BoundVal =>
                  (declaredBVs contains bv) ||
                    ((for {
                      bvs <- invCtx.get(bv)
                      isUserDefined = bvs map (_.owner) forall {
                        case lb: LetBinding => lb.isUserDefined
                        case _ => true
                      }
                    } yield isUserDefined) getOrElse false)
                case lb: LetBinding => preCheckRep(declaredBVs, invCtx, lb)
                case _ => true
              }
              case _ => true
            }
          }

          r match {
            case lb: LetBinding =>
              val acc0 = declaredBVs + lb.bound
              preCheckDef(acc0, invCtx, lb.value) && preCheckRep(acc0, invCtx, lb.body)
            case _ => true
          }
        }

        def reverse[A, B](m: Map[A, B]): Map[B, Set[A]] = m.groupBy(_._2).mapValues(_.keys.toSet)

        val invCtx = reverse(es.ctx)
        ex._1.values ++ ex._3.values.flatten forall (preCheckRep(Set.empty, invCtx, _))
      }

      def collectBVs(d: Def): Set[BoundVal] = d match {
        case ma: MethodApp => ma.self :: ma.argss.argssList collect { case bv: BoundVal => bv } toSet
        case _ => Set.empty
      }

      /**
        * Removes all the statements referencing elements from `remove`. 
        * Also takes care of statements referencing removed statements.
        * 
        * Returns None, if `r`'s return value is also removed. 
        * For instance in {{{filterNot(ir"val a = readInt; a", Set(a)}}}
        */
      def filterNot(remove: Set[BoundVal])(r: Rep): Option[Rep] = {
        
        /**
          * Returns the statements to remove based on the initial `remove` set.
          * 
          * Adds all the pure statements referencing removed ones.
          * In essence, computes the the transitive closures of pure statements that depend on the BVs in `remove` 
          * and adds it to `remove`.
          */
        def buildToRemove(r: Rep, remove: Set[BoundVal]): Set[BoundVal] = r match {
          case lb: LetBinding if remove contains lb.bound => buildToRemove(lb.body, remove)

          case lb: LetBinding if isPure(lb.value) =>
            val bvsInValue = collectBVs(lb.value)

            if (bvsInValue exists (remove contains)) {
              buildToRemove(lb.body, remove + lb.bound)
            } else {
              buildToRemove(lb.body, remove)
            }

          case lb: LetBinding => buildToRemove(lb.body, remove)

          case _ => remove
        }

        def filterNot0(remove: Set[BoundVal])(r: Rep): Rep = r match {
          case lb: LetBinding if remove contains lb.bound => filterNot0(remove)(lb.body)
          case lb: LetBinding => 
            lb.body = filterNot0(remove)(lb.body)
            lb
          case _ => r
        }
        
        val remove0 = buildToRemove(r, remove)
        r match {
          case lb: LetBinding => lb.last.body match {
            case bv: BoundVal if remove0 contains bv => None 
            case _ => Some(filterNot0(remove0)(r))
          }
          case _ => Some(r) // Nothing to do
        }
      }
      
      
      /**
        * Merges the generated `code` with the `xtee` 
        */
      def merge(code: Rep, xtee: Rep)(xtor: Rep, ctx: Ctx): Rep = {
        
        /**
          * Puts `code` in the right position in `xtee`.
          */
        def mergeLBs(code: LetBinding, xtee: LetBinding)(es: State): Rep = {
          def collectAllBVs(r: Rep): Set[BoundVal] = {
            def collectAllBVs0(r: Rep, acc: Set[BoundVal]): Set[BoundVal] = r match {
              case lb: LetBinding => collectAllBVs0(lb.body, acc ++ collectBVs(lb.value))
              case _ => acc
            }
            collectAllBVs0(r, Set.empty)
          }

          /**
            * Returns the statement in `r` at which point all the BVs from `lookFor` have been declared, 
            * if it exists. 
            */
          def findPos(r: LetBinding, lookFor: Set[BoundVal]): Option[LetBinding] = {
            if (lookFor.isEmpty) None
            else r match {
              case lb: LetBinding =>
                val lookFor0 = lookFor -- collectBVs(lb.value)
                if (lookFor0.isEmpty) Some(lb)
                else lb.body match {
                  case innerLB: LetBinding => findPos(innerLB, lookFor0)
                  case _ => None
                }
              case _ => None
            }
          }

          // All usages of BVs that come from the xtee
          val lookFor = collectAllBVs(code).filter((es.ex._1.values ++ es.ex._3.values.flatten).toSet contains)

          findPos(xtee, lookFor) match {
            case Some(pos) =>
              code.last.body = pos.body
              pos.body = code
              xtee
            case None =>
              code.last.body = xtee
              code
          }
        }

        xtor match {
          // Find what the return value of `xtor` matched in `xtee` and
          // replace that by the return value of `code`.
          case xtorLB: LetBinding =>
            val xtorLast = xtorLB.last
            xtorLast.body match {
              case xtorRet: BoundVal => code match {
                case codeLB: LetBinding =>
                  val codeLast = codeLB.last
                  val codeRet = codeLast.body
                  val bv = ctx(xtorRet)
                  bottomUpPartial(xtee) { case `bv` => codeRet } match {
                    case xteeLB: LetBinding => mergeLBs(codeLB, xteeLB)(es)
                    case r =>
                      codeLast.body = r
                      code
                  }

                case _ =>
                  val bv = ctx(xtorRet)
                  bottomUpPartial(xtee) { case `bv` => code }
              }
              case _ => code
            }

          case _ => code match {
            case codeLB: LetBinding =>
              val codeLast = codeLB.last
              val codeRet = codeLast.body
              bottomUpPartial(xtee) { case `xtor` => codeRet } match {
                case xteeLB: LetBinding => mergeLBs(codeLB, xteeLB)(es)
                case r =>
                  codeLast.body = r
                  code
              }

            case _ => bottomUpPartial(xtee) { case `xtor` => code }
          }
        }
      }
      
      if (preCheck(es.ex)) for {
        code <- code(es.ex)
        mergedCode = merge(code, xtee)(xtor, es.ctx)
        finalCode <- filterNot(es.matchedImpureBVs)(mergedCode)
      } yield finalCode
      else None
    }
    
    rewriteRepWithState(xtor, xtee) match {
      case Right(es) => genCode(es)
      case Left(_) => None
    }
  }
  
  // * --- * --- * --- *  Implementations of `QuasiBase` methods  * --- * --- * --- *

  def hole(name: String, typ: TypeRep) = Hole(name, typ)
  def splicedHole(name: String, typ: TypeRep): Rep = SplicedHole(name, typ)
  def typeHole(name: String): TypeRep = DummyTypeRep
  def hopHole(name: String, typ: TypeRep, yes: List[List[BoundVal]], no: List[BoundVal]) = HOPHole(name, typ, yes, no)
  override def hopHole2(name: String, typ: TypeRep, args: List[List[Rep]], visible: List[BoundVal]) =
    HOPHole2(name, typ, args, visible filterNot (args.flatten contains _))
  def substitute(r: => Rep, defs: Map[String, Rep]): Rep = {
    val r0 = 
      if (defs isEmpty) r
      else bottomUp(r) {
        case h@Hole(n, _) => defs getOrElse(n, h)
        case h@SplicedHole(n, _) => defs getOrElse(n, h)
        case h => h
      } 
    
    r0 |> inlineBlock
  }
  override def insertAfterTransformation(r: => Rep, defs: Map[String, Rep]): Rep = {
    // TODO for now we do nothing to r. Later make sure that after applying the defs it is still valid in ANF!
    require(defs.isEmpty)
    r
  }


  // * --- * --- * --- *  Implementations of `TypingBase` methods  * --- * --- * --- *
  
  import scala.reflect.runtime.universe.TypeTag // TODO do without this

  def uninterpretedType[A: TypeTag]: TypeRep = DummyTypeRep
  def typeApp(self: TypeRep, typ: TypSymbol, targs: List[TypeRep]): TypeRep = DummyTypeRep
  def staticTypeApp(typ: TypSymbol, targs: List[TypeRep]): TypeRep = DummyTypeRep //unsupported
  def recordType(fields: List[(String, TypeRep)]): TypeRep = DummyTypeRep
  def constType(value: Any, underlying: TypeRep): TypeRep = DummyTypeRep

  def typLeq(a: TypeRep, b: TypeRep): Boolean = true

  def loadTypSymbol(fullName: String): TypSymbol = new TypeSymbol(fullName) // TODO


  // * --- * --- * --- *  Misc  * --- * --- * --- *
  
  def unsupported = lastWords("This part of the IR is not yet implemented/supported")
  
  override def showRep(r: Rep) = r.toString // TODO impl pretty-printing
  
  val FunApp = `scala.Function1`.`method apply`.value
  
}

class ReificationContext(val inExtractor: Bool) { reif =>
  var firstLet: FlatOpt[LetBinding] = Non
  var curLet: FlatOpt[LetBinding] = Non

  /**
    * Updates the current let-binding with `lb`.
   */
  def += (lb: LetBinding): Unit = {
    curLet match {
      case Non => firstLet = lb.som
      case Som(cl) => cl.body = lb
    }
    curLet = lb.som
  }

  /**
    * Let-binds `d` and updates the current let-binding with it.
    */
  def += (d: Def): Symbol = new Symbol {
    protected var _parent: SymbolParent = new LetBinding("tmp", this, d, this) alsoApply (reif += _)
  }
  
  def finalize(r: Rep): Rep = {
    firstLet match {
      case Non => 
        assert(curLet.isEmpty)
        r
      case Som(fl) =>
        curLet.get.body = r
        fl
    }
  }
}

