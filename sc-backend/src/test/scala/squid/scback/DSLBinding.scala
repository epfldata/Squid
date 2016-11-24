package squid.scback

import squid.utils._

import ch.epfl.data.sc._
import pardis._
import deep.scalalib._
import deep.scalalib.collection._
import PardisBinding._

trait DSLBinding {
  
  //object SC extends ir.Base
  //object SC extends ir.Base with SeqOps
  //object SC extends ir.Base with ArrayBufferOps
  //object SC extends ir.Base with StringOps with NoStringCtor
  //object SC extends ir.Base with ArrayBufferOps with NumericOps with ScalaPredefOps
  //object SC extends ir.Base with NumericOps
  //object SC extends ir.Base with ScalaCoreOps with NoStringCtor
  object SC extends ir.Base with ScalaCoreOps with NoStringCtor with ContOps
  
  /*_*/
  object Sqd extends AutoboundPardisIR(SC) with DefaultRedirections[SC.type]
  
  Sqd.ab = {
    import scala.collection.mutable.ArrayBuffer
    AutoBinder(SC, Sqd)
  }
  
}

