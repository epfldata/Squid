// Copyright 2019 EPFL DATA Lab (data.epfl.ch)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package squid.ir
package graph3

import squid.utils._

import scala.collection.mutable

/** New new scheduling algorithm that supports recursive definitions and Haskell output. */
trait HaskellGraphScheduling2 { graph: HaskellGraph =>
  /* Notes.
  
  The goal of this rewrite is to switch from the previous extremely complicated fixed-point and reconstruction algorithm
  to an iterative algorithm that works on a simple AST and is much easier to reason about.
  This should also solve some fundamental problems of the old approach, including the fact that
  the old algorithm computed an approximate usage count to know which definitions to inline during program reconstruction,
  but this turned out to fail when inlined defs (that were considered used once) were supposed to become recursive definitions,
  as could happen for definitions associated with boxes...
  Make beta reduction not duplicate apps, to avoid unrolling recursions
  
  Still, it turned out this was not enough, because
  we used to duplicate applications all over the place, and that caused
  very strange structures with unrolled recursions, which the scheduler
  simply could not handle (it created cycles in the arguments passed to
  calls and calls themselves).
  Now, we use a more 'respectful' implementation of beta reduction that
  reconstructs more involved branch structures to avoid that.
  
  */
  
  object HaskellScheduleDebug extends PublicTraceDebug
  import HaskellScheduleDebug.{debug=>Sdebug}
  
  import mutable.{Map => M}
  //import mutable.{ListMap => M}
  
  
  def scheduleRec(rep: Rep): RecScheduler = scheduleRec(PgrmModule("<module>", "?", Map("main" -> rep)))
  
  def scheduleRec(mod: PgrmModule): RecScheduler = {
    new RecScheduler(mod)
  }
  
  
  class RecScheduler(mod: PgrmModule) {
    
    private val nameCounts = mutable.Map.empty[String, Int]
    private val nameAliases = mutable.Map.empty[Val, String]
    def printVal(v: Val): String = if (v === WildcardVal) "_" else nameAliases.getOrElseUpdate(v, {
      val nme = v.name takeWhile (_ =/= '$')
      val cnt = nameCounts.getOrElse(nme, 0)
      nameCounts(nme) = cnt+1
      if (nme.isEmpty) "_"+cnt else
      cnt match {
        case 0 => nme
        case 1 => nme+"'"
        case n => nme+"'"+n
      }
    })
    
    val scheduledReps = M.empty[Rep,SchedulableRep]
    def getSR(r: Rep): SchedulableRep = {
      scheduledReps.getOrElseUpdate(r, new SchedulableRep(r))
    }
    mod.letReps.foreach(r => printVal(r.bound))
    
    
    /** Contains all defs with new params, so whose calls may be missing arguments */
    var workList: List[SchedulableRep] = Nil
    
    // TODO better complexity, if turns out necessary
    def addWork_!(sr: SchedulableRep): Unit = if (!workList.contains(sr)) workList ::= sr
    
    val moduleDefs = mod.letReps.map(getSR)
    moduleDefs.foreach(_.init())
    
    val isExported = mod.letReps.toSet
    
    def mkSReps() = scheduledReps.valuesIterator.toList.sortBy(sr => (!isExported(sr.rep), sr.rep.bound.name))
    val sreps = mkSReps()
    
    Sdebug("REPS:"+sreps.map("\n\t"+_).mkString)
    
    ///** Contains all defs with new params, so whose calls may be missing arguments */
    //var workList = scheduledReps.valuesIterator.filter(_.params.nonEmpty).toList
    
    Sdebug(s">>>> Starting fixed point... <<<<")
    while (workList.nonEmpty) {
      val sr = workList.head
      assert(sr.directChildren.isComputed) //sr.init()
      workList = workList.tail
      Sdebug(s"Work list: [${sr.rep.bound}], ${workList.map(_.rep.bound).mkString(", ")}")
      if (HaskellScheduleDebug.isDebugEnabled)
        Thread.sleep(100)
      HaskellScheduleDebug nestDbg sr.usages.foreach { c =>
        Sdebug(s"Call ${c}")
        val old = if (HaskellScheduleDebug.isDebugEnabled) s"${c}" else ""
        
        val psr = c.parent
        assert(psr.directChildren.isComputed) //psr.init()
        val pctrl = psr.baseCtrl
        Sdebug(s"Parent [$pctrl] ${psr}")
        HaskellScheduleDebug nestDbg sr.params.foreach { case (br, param) =>
          //Sdebug(s"Param ${param}")
          if (!c.args.contains(param)) {
            Sdebug(s"Param $br${param} not yet in call ${c}")
            //Sdebug(s"Param $br${param} not yet in call ${c}  [param.ctrl:${param.ctrl} c.ctrl:${c.ctrl}]")
            //assert(c.ctrl === param.ctrl, s"${c.ctrl} ${param.ctrl}") // nope
            
            //val ctrl = pctrl `;` br.ctrl
            //Sdebug(s"? ${pctrl} ; ${br.ctrl} ? ${br.cid}")
            //mayHaveCid(br.ctrl, br.cid)(pctrl) match {
            Sdebug(s"? ${pctrl} ; ${c.ctrl} ; ${br.ctrl} ? ${br.cid}")
            //Sdebug(s"? ${pctrl} ; ${c.ctrl} ; ${br.ctrl} == ${pctrl `;` c.ctrl `;` br.ctrl}")
            mayHaveCid(br.ctrl, br.cid)(pctrl`;`c.ctrl) match {
            //Sdebug(s"? ${pctrl} ; ${param.ctrl} ; ${br.ctrl} ? ${br.cid}")
            //mayHaveCid(br.ctrl, br.cid)(pctrl`;`param.ctrl) match {
            //Sdebug(s"? ${pctrl} ; ${br.ctrl} ? ${br.cid}")
            //mayHaveCid(br.ctrl, br.cid)(param.ctrl) match {
            case Some(cnd) =>
              //Sdebug(s"Resolved ${pctrl} ; ${br}  -->  $cnd")
              
              // What if new branch?!
              //val schCall = psr.call(if (cnd) br.lhs else br.rhs)
              //val schCall = new SchCall(if (cnd) br.lhs else br.rhs, param.ctrl, psr) // FIXME?
              val schCall = new SchCall(if (cnd) br.lhs else br.rhs, c.ctrl`;`param.ctrl, psr) // FIXME?
              //schCall.sr.init()
              //val schCall = new SchCall(getSR(if (cnd) br.lhs else br.rhs), c.ctrl`;`param.ctrl, psr) // FIXME?
              //val schCall = new SchCall(if (cnd) br.lhs else br.rhs, psr)
              
              Sdebug(s"Resolved ${pctrl} ; ${br}  ($cnd) -->  ${schCall}")
              
              c.args += param -> schCall
              //workList ::= schCall.sr
              addWork_!(schCall.sr)
              
            case None =>
              
              /* Because of tricky recursion patterns (like the one in HOR2), it doesn't seem possible to propagate a
                 branch as is even when it looks like it would not have changed... */
              val (propagatedBranch,propagatedParam) = if (/*(pctrl`;`c.ctrl) === Id*/false) { // the branch doesn't change
              //val (propagatedBranch,propagatedParam) = if ((pctrl`;`c.ctrl`;`param.ctrl)===Id) { // the branch doesn't change
              //val (propagatedBranch,propagatedParam) = if (((pctrl`;`c.ctrl)===Id) && ((pctrl`;`c.ctrl`;`param.ctrl)===Id)) { // the branch doesn't change
                //???
                Sdebug(s"Propagate param ${param} for $br")
                (br,param)
              } else {
                //val newBranch = Branch(pctrl`;`br.ctrl,br.cid,br.lhs,br.rhs)
                val newBranch = Branch(pctrl`;`c.ctrl`;`br.ctrl,br.cid,br.lhs,br.rhs)
                //val newParam = SchParam(param.branchVal.renew)(psr)
                val newParam = SchParam(pctrl`;`
                  //param.ctrl,
                  //c.ctrl,
                  //param.ctrl`;`c.ctrl,
                  //param.ctrl`;`pctrl`;`c.ctrl,
                  c.ctrl`;`param.ctrl,
                  param.branchVal.renew) // TODO don't create new param if not necessary
                Sdebug(s"New param ${newParam} for $newBranch")
                //assert(!psr.params.contains(newBranch))
                (newBranch,newParam)
              }
              /*
              c.args += param -> propagatedParam
              if (!psr.params.contains(propagatedBranch)) {
                psr.params += propagatedBranch -> propagatedParam
                workingSet ::= psr
              } else Sdebug(s"Param ${propagatedParam} already in ${psr.params}")
              */
              c.args += param -> (psr.params.get(propagatedBranch) match {
                case Some(a) =>
                  Sdebug(s"Param ${propagatedParam} already in ${psr.params} with value $a")
                  a
                case None =>
                  psr.params += propagatedBranch -> propagatedParam
                  addWork_!(psr)
                  propagatedParam
              })
              
            }
          }
        }
          
        if (HaskellScheduleDebug.isDebugEnabled) {
          val newCall = s"$c"
          if (newCall =/= old) Sdebug(s"Updated call: $old ~~> $newCall")
        }
        
      }
    }
    
    
    
    def count(sr: SchedulableRep): Unit = Sdebug(s"Counting ${sr}") thenReturn HaskellScheduleDebug nestDbg (sr.exp match {
      case _: SchVar | _: SchConst | _: SchParam =>
      //case SchBox(_, c) if (c.sr.rep.node match {case ConcreteNode(_:Branch)=>true case _ => false }) => count(b.sr)
      //case SchBox(_, b) => count(b.sr)
      //case SchBox(_, c) if (c.sr.rep.node match {case ConcreteNode(_:Branch)=>false case _ => true }) => count(c.sr)
      /* // Seems to cause code duplication:
      case SchBox(_, c) if !c.sr.rep.node.isInstanceOf[Branch] =>
        Sdebug(s"IGNORE COUNT ${sr}")
        count(c.sr)
      */
      case _ =>
      //assert(!sr.exp.isInstanceOf[SchBranch], sr)
      sr.usageCount += 1
      Sdebug(s"Children: ${sr.allChildren.mkString(", ")}")
      if (sr.usageCount === 1) sr.allChildren.foreach { c =>
        count(c.sr)
      }
    })
    Sdebug(s"\n\n\n=== Counting ===")
    moduleDefs.foreach(count)
    /*
    sreps.foreach { sr =>
      Sdebug(s"[${if (sr.shouldBeScheduled) sr.usageCount else " "}] $sr")
    }
    */
    
    
    // TODO
    //val topLevelRepsOrdered: List[SchedulableRep] = {
    //  val topLevels = topLevelReps.iterator.map { case(sr,d) => sr.rep -> d }.toMap
    //  val done = mutable.Set.empty[Rep]
    //  topLevelReps.flatMap { case(sr,d) =>
    //    (if (done(sr.rep)) Iterator.empty else { done += sr.rep; Iterator(d) }) ++ d.allChildren.collect {
    //      case SchCall(sr, _, _) if !done(sr.rep) && topLevels.contains(sr.rep) =>
    //        done += sr.rep; topLevels(sr.rep) }
    //  }
    //}
    //softAssert(topLevelRepsOrdered.size === topLevelReps.size, s"${topLevelRepsOrdered.size} === ${topLevelReps.size}")
    
    //val topLevelRepsOrdered = sreps
    //val topLevelRepsOrdered = moduleDefs
    val topLevelRepsOrdered = mkSReps().filter(sr => isExported(sr.rep) || sr.usageCount > 1)
    
    def toHaskell(imports: List[String], ghcVersion: String): String = {
      def commentLines(str: String) = str.split("\n").map("--   "+_).mkString("\n")
      //HaskellScheduleDebug debugFor
      s"""
      |-- Generated Haskell code from Graph optimizer
      |-- Core obtained from: $ghcVersion
      |-- Optimized after GHC phase:
      |${mod.modPhase |> commentLines}
      |-- Total nodes: ${mod.Stats.tot}; Boxes: ${mod.Stats.boxes}; Branches: ${mod.Stats.brans}
      |-- Apps: ${mod.Stats.apps}; Lams: ${mod.Stats.lams}; Unreduced Redexes: ${mod.Stats.unreducedRedexes}
      |
      |{-# LANGUAGE UnboxedTuples #-}
      |{-# LANGUAGE MagicHash #-}
      |
      |module ${mod.modName} (${mod.letReps.map(_.bound).mkString(",")}) where
      |
      |${imports.map("import "+_).mkString("\n")}
      |
      |${topLevelRepsOrdered.map(_.toHs).mkString("\n\n")}
      |""".tail.stripMargin
    }
    
    override def toString = s"module ${mod.modName}${topLevelRepsOrdered.map("\n\t"+_.toHs).mkString}"
    
    
    
    
    
    
    
    
    type Param = SchParam
    
    class SchedulableRep(val rep: Rep) {
      val bound = rep.bound
      
      var usageCount: Int = 0
      var usages: List[SchCall] = Nil
      
      val params: M[Branch,Param] = M.empty
      
      def shouldBeScheduled = rep.node match {
        case _: Branch => false
        case ConcreteNode(d) => !d.isSimple
        case _ => true
      }
      
      val baseCtrl = rep.node |> {
        case Box(c, _) => c
        case ConcreteNode(a: Abs) => 
          //Push(DummyCallId,Id,Id) 
          Push(new CallId(a.param,dummy = true),Id,Id) 
        case _ => Id
      }
      
      private[this] 
      //def call(r: Rep) = new SchCall(r, this)
      def call(r: Rep) = new SchCall(r, Id, this)
      //def call(r: Rep) = new SchCall(getSR(r), Id, this)
      //def call(r: Rep) = new SchCall(r, baseCtrl, this)
      
      //val exp: Lazy[SchExp] = Lazy(rep.node match {
      //lazy val exp: SchExp = rep.node match {
      val exp: SchExp = rep.node match {
        case Box(c,b) => SchBox(c,call(b))
        case br @ Branch(c,i,l,r) =>
          //val sbr = SchBranch(c,i,call(l),call(r),i.v.renew)
          val sbr = SchParam(Id,i.v.renew)
          //getSR(l)
          //getSR(r)
          Sdebug(s"Initial param $sbr for $br")
          assert(baseCtrl===Id)
          //makeArgument(Id,br->sbr)
          params += br->sbr
          //workingSet ::= this // FIXME rm?
          //makeArgument(baseCtrl,br->sbr)
          sbr
        //case ConcreteNode(Apply(lhs, rhs)) => SchApp(call(lhs), call(rhs))(this)
        case ConcreteNode(d) => d match {
          case Apply(lhs, rhs) => SchApp(call(lhs), call(rhs))
          case v: Val => SchVar(v)
          case c: Constant => SchConst(c)
          case cs: CrossStageValue => SchConst(cs)
          case sm: StaticModule => SchConst(sm)
          case Abs(p,b) =>
            SchLam(p,call(b))
            // Another way to handle lambdas is to introduce a box here and remove the push from 'baseCtrl'
            //SchLam(p,call(Box.rep(Push(new CallId(p,dummy = true),Id,Id),b)))
          case MethodApp(scrut, CaseMtd, Nil, ArgsVarargs(Args(), Args(alts @ _*))::Nil, _) =>
            //SchCase(scrut: SchExp, arms: List[(String,List[Val],SchExp)])
            SchCase(call(scrut), alts.map(mkAlt(scrut,_)).toList)
          case MethodApp(scrut,GetMtd,Nil,Args(con,idx)::Nil,_) => (con,idx) |>! {
            case (Rep(ConcreteNode(StaticModule(con))),Rep(ConcreteNode(Constant(idx: Int)))) =>
              //SchCtorField(call(scrut),con,idx)
              SchCtorField(scrut,con,idx)
          }
        }
      }
      def mkAlt(scrut: Rep, r: Rep): (String,List[Val],SchCall) = r.node |>! {
        case ConcreteNode(MethodApp(_,Tuple2.ApplySymbol,Nil,Args(lhs,rhs)::Nil,_)) =>
          lhs.node |>! {
            case ConcreteNode(StaticModule(con)) =>
              val boundVals = List.tabulate(ctorArities(con))(idx => bindVal(s"arg$idx", Any, Nil))
              (con,boundVals,call(rhs))
          }
      }
      
      /** Does NOT include the nested calls! Only the top-level ones!! */
      val directChildren = Lazy(exp.directCalls.map(_.sr))
      
      def init(): Unit = {
        if (!directChildren.isComputed && !directChildren.isComputing) {
          directChildren.value.foreach{ c => if (c.params.nonEmpty) addWork_!(c) }
        }
      }
      
      def allChildren = exp.directCalls.flatMap(_.allChildren)
      
      val (topSubBindings,nestedSubBindings) = (List.empty[Val->SchShareable],List.empty[Val->SchShareable])
      
      val name = rep.bound |> printVal
      def toHs: String = {
        implicit val Hstx: HsCtx = HsCtx.empty
        val paramList = if (params.isEmpty) "" else
          s"(# ${params.toList.sortBy(_._2.branchVal.name).map(_._2.branchVal |> printVal).mkString(", ")} #)"
        s"${topSubBindings.map(sb => s"${sb._1|>printVal} = ${sb._2.toHs}\n").mkString}$name$paramList = ${
          if (nestedSubBindings.isEmpty) "" else {
            s"let${nestedSubBindings.map(sb => s"\n    ${sb._1|>printVal} = ${sb._2.toHs}").mkString("")}\n  in "
          }
        }${exp.toHs}"
      }
      
      override def toString = s"let ${rep.bound}(${params.map(bp => s"${bp._2.branchVal}").mkString(", ")}) = ${exp}"
    }
    
    case class HsCtx(params: Map[SchParam, SchArg], enclosingCases: Map[(Rep,String), List[Val]])
    object HsCtx {
      val empty = HsCtx(Map.empty, Map.empty)
    }
    
    sealed abstract class SchShareable extends SchExp // TODO use for CSE or rm
    sealed abstract class SchExp {
      def directCalls: List[SchCall]
      def toHs(implicit ctx: HsCtx): String
    }
    
    /** `value` can be a Constant, a CrossStageValue, or a StaticModule */
    case class SchConst(value: Any) extends SchExp {
      def directCalls: List[SchCall] = Nil
      def toHs(implicit ctx: HsCtx) = value match {
        case Constant(n: Int) => n.toString
        case Constant(s: String) => '"'+s+'"'
        case CrossStageValue(n: Int, UnboxedMarker) => s"$n#"
        case sm: StaticModule => sm.fullName
      }
      override def toString = value.toString
    }
    case class SchVar(v: Val) extends SchExp {
      def directCalls: List[SchCall] = Nil
      val vStr = v |> printVal
      def toHs(implicit ctx: HsCtx) = vStr
      override def toString = vStr
    }
    case class SchApp(lhs: SchCall, rhs: SchCall) extends SchShareable {
      def directCalls: List[SchCall] = lhs :: rhs :: Nil
      def toHs(implicit ctx: HsCtx) = s"(${lhs.toHs} ${rhs.toHs})"
      override def toString = s"(${lhs} @ ${rhs})"
    }
    case class SchLam(param: Val, body: SchCall) extends SchShareable {
      def directCalls: List[SchCall] = body :: Nil
      def toHs(implicit ctx: HsCtx) = {
        val bindings = List.empty[SchExp]
        val subBindings = List.empty[Val->SchShareable]
        s"(\\${param |> printVal} -> ${
          if (bindings.isEmpty && subBindings.isEmpty) "" else
          s"let { ${(bindings.map(_.toHs) ++ subBindings.map(sb => s"${sb._1 |> printVal} = ${sb._2.toHs}")).mkString("; ")} } in "
        }${body.toHs})"
      }
      override def toString = s"($param -> ${body})"
    }
    case class SchCase(scrut: SchCall, arms: List[(String,List[Val],SchCall)]) extends SchShareable {
      def directCalls: List[SchCall] = scrut :: arms.map(_._3)
      def toHs(implicit ctx: HsCtx) = s"(case ${scrut.toHs} of {${arms.map { case (con, vars, rhs) =>
          (if (con.head.isLetter || con === "[]" || con.head === '(') con else s"($con)") +
            vars.map(printVal).map{" "+_}.mkString + s" -> ${
              rhs.toHs(ctx.copy(enclosingCases = ctx.enclosingCases + ((scrut.sr.rep,con) -> vars)))}"
      }.mkString("; ")}})"
    }
    case class SchCtorField(scrut: Rep, ctor: String, idx: Int) extends SchShareable {
      def directCalls: List[SchCall] = Nil
      def toHs(implicit ctx: HsCtx) = s"${ctx.enclosingCases.get(scrut->ctor) match {
        case Some(vars) => printVal(vars(idx))
        case None => ??? // TODO
      }}"
    }
    
    case class SchBox(ctrl: Control, body: SchCall) extends SchExp {
      def directCalls: List[SchCall] = body :: Nil
      
      def toHs(implicit ctx: HsCtx) = body.toHs
      
      override def toString = s"$ctrl ; $body"
    }
    case class SchParam(ctrl: Control, branchVal: Val) extends SchExp with SchArg {
      def directCalls: List[SchCall] = Nil
      
      def mkHs(implicit ctx: HsCtx): String = branchVal |> printVal
      
      def toHs(implicit ctx: HsCtx) = {
        //Sdebug(s"> $this >---> $assignedTo")
        assignedTo.mkHs
      }
      
      override def toString = s"${if (ctrl === Id) "" else s"[$ctrl]"}$branchVal"
    }
    
    
    class SchCall(r: Rep, val ctrl: Control, val parent: SchedulableRep) extends SchArg {
      lazy val sr = getSR(r) also { sr => sr.init(); sr.usages ::= this }
      
      val args: M[SchParam,SchArg] = M.empty
      
      def allChildren: List[SchCall] =
        this :: args.valuesIterator.collect { case c: SchCall => c }.toList.flatMap(_.allChildren)
      
      lazy val name = sr.rep.bound |> printVal
      
      def mkHs(implicit ctx: HsCtx): String = toHs
      
      def toHs(implicit ctx: HsCtx): String = {
        Sdebug(s"Call $this")
        //Sdebug(s"Call $this   with  {${ctx.params.mkString(",")}}")
        
        if (sr.usageCount > 1) {
          val argStrs = args.toList.sortBy(_._1.branchVal.name).map(_._2.toHs)
          //val valArgs = sr.valParams.fold("??"::Nil)(_.map(printVal))
          val valArgs = List.empty[Val->String]
          val allArgStrs = valArgs.map(_._2) ++ argStrs
          val body = if (allArgStrs.isEmpty) name else s"($name(# ${allArgStrs.mkString(", ")} #))"
          //rec.fold(body) { v => val vstr = printVal(v); s"(let{-rec-} $vstr = $body in $vstr)" }
          body
        } else HaskellScheduleDebug nestDbg { 
          Sdebug(s"Inline! ${sr}")
          sr.exp.toHs(ctx.copy(params = ctx.params ++ args.flatMap(a => {
            if (a._2.assignedTo === a._1.assignedTo) Nil else // avoid creating a cycle
            a._1 -> a._2 :: Nil
            //{Sdebug(s"Augment Ctx ${a._1} -> ${a._2}");a._1 -> a._2 :: Nil}
          })))
        }
      }
      
      override def toString = s"${sr.rep.bound}${if (ctrl === Id) "" else s"{$ctrl}"}(${args.map(a => 
        s"${if (a._1.ctrl === Id) "" else s"[${a._1.ctrl}]"}${a._1.branchVal}=${a._2 match {
          case br: SchParam => br.branchVal
          case _ => a._2.toString
        }
      }").mkString(", ")})"
      //}").mkString(", ")})<$ctrl>"
    }
    
    sealed trait SchArg {
      def toHs(implicit ctx: HsCtx): String
      def mkHs(implicit ctx: HsCtx): String
      
      def assignedTo(implicit ctx: HsCtx): SchArg = this match {
        case sp: SchParam =>
          //ctx.getOrElse(sp.branchVal, this)
          ctx.params.get(sp).map(_.assignedTo).getOrElse(this)
        case _: SchCall => this
      }
      
    }
    
    
    
  }
  
  
  
  
  
}