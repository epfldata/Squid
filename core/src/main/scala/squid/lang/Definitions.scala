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

package squid.lang

/** A set of types and methods for manipulating class, object, field, and method definitions using Squid.
  * This is notably used as the output of the `@lift` macro. */
trait Definitions extends Base {
  
  type FieldGetter <: MtdSymbol
  type FieldSetter  <: MtdSymbol
  
  // TODO impl monomorphization of classes and methods
  //   For monomorphized classes, we need to transform all the type references everywhere... so the process will take
  //   a bunch of other external code that uses the class and adapt it, too.
  
  object TopLevel extends Scope {
    type Scp = Any
    val members = Nil
  }
  trait Scope { outerScope =>
    type Scp
    
    sealed abstract class Member
    val members: List[Member]
    
    trait Parameterized {
      val tparams: List[TypParam]
      val typeParams: List[CodeType[_]] =
        tparams.map(tp => CodeType(staticTypeApp(tp, Nil)))
    }
    
    trait ClassWithObject[C] extends Clasz[C] {
      val companion: Some[outerScope.Object[_]]
    }
    trait ClassWithoutObject[C] extends Clasz[C] {
      val companion: None.type = None
    }
    trait ObjectWithClass[C] extends Object[C] {
      val companion: Some[outerScope.Clasz[_]]
    }
    trait ObjectWithoutClass[C] extends Object[C] {
      val companion: None.type = None
    }
    
    abstract class Object[C: CodeType](val name: String) extends ClassOrObject[C] {
      val companion: Option[outerScope.Clasz[_]]
    }
    /* Note: in Scala 2.11, naming this Class results in strange failures, as in:
     *   java.lang.NoClassDefFoundError: squid/lang/Definitions$Scope$Class (wrong name: squid/lang/Definitions$Scope$class) */
    abstract class Clasz[C: CodeType](val name: String, val tparams: List[TypParam]) extends ClassOrObject[C] with Parameterized {
      val companion: Option[outerScope.Object[_]]
      val self: Variable[C]
    }
    abstract class ClassOrObject[C](implicit val C: CodeType[C]) extends Member with Scope {
      type Scp <: outerScope.Scp
      
      // TODO should have special ctor method(s)...
      
      val name: String
      val parents: List[CodeType[_]]
      val fields: List[Field[_]]
      val methods: List[Method[_,_]]
      
      lazy val members: List[Member] = fields ::: methods
      
      abstract class FieldOrMethod[A](implicit val A: CodeType[A]) extends Member {
        val symbol: MtdSymbol
      }
      type AnyField = Field[_]
      class Field[A0: CodeType](
       val name: String,
       val get: FieldGetter,
       val set: Option[FieldSetter],
       val init: Code[A0,Scp]
     ) extends FieldOrMethod[A0] {
        type A = A0
        val symbol: MtdSymbol = get
        
        //println(s"FIELD $this")
        
        override def toString = s"va(l/r) ${symbol}: ${A.rep} = ${showRep(init.rep)}"
      }
      type AnyMethod[S <: Scp] = Method[_, S]
      class Method[A0: CodeType, S <: Scp](
        val symbol: MtdSymbol,
        val tparams: List[TypParam],
        val vparams: List[List[Variable[_]]],
        val body: Code[A0,S]
      ) extends FieldOrMethod[A0] with Parameterized {
        type Scp = S
        type A = A0
        
        //println(s"METHOD $this")
        
        override def toString = s"def ${symbol}[${tparams.mkString(",")}]${vparams.map(vps => vps.map(vp =>
          //s"${vp.`internal bound`}: ${vp.Typ.rep}"
          s"$vp"
        ).mkString("(",",",")")).mkString}: ${A.rep} = ${showRep(body.rep)}"
      }
      
      // A helper for creating Field objects; used by the `@lift` macro
      def mkField(name: String, get: MtdSymbol, set: Option[MtdSymbol], init: Rep)(typ: TypeRep): Field[_] =
        new Field[Any](name, get.asInstanceOf[FieldGetter],
          set.map(_.asInstanceOf[FieldSetter]),Code(init))(CodeType[Any](typ))
      
      
      // TODO an API for modifying these constructs in a safe way...
      /*
      sealed abstract class MethodTransformation[-A]
      case object Remove extends MethodTransformation[Any]
      case class Rewrite[A](newBody: Code[A,Scp]) extends MethodTransformation[A]
      
      //def transform(trans: Map[Method[_],MethodTransformation])
      def transform(trans: List[MethodTransformation[_]]) = ???
      */
      
    }
    
  }
  
}
