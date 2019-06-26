package squid.haskellopt

import squid.utils._
import org.scalatest.FunSuite

class OptTests extends FunSuite {
  
  test("Lists") {
    //pipeline("haskellopt/target/dump/Lists.pass-0000.cbor")
    //pipeline("haskellopt/target/dump/Lists.pass-0001.cbor")
    //for (i <- 2 to 9) pipeline(s"haskellopt/target/dump/Lists.pass-000$i.cbor")
    TestHarness("Lists")
    // FIXMEnot does not reduce at all!
    // FIXMEnot generates extra params!
  }
  test("Basics") {
    TestHarness("Basics")
    //TestHarness("Basics", exec = true)
    //TestHarness("Basics", "0000"::Nil)
  }
  test("HigherOrder") {
    TestHarness("HigherOrder")
    //TestHarness("HigherOrder", "0000"::Nil)
  }
  test("HigherOrderHard") {
    //TestHarness("HigherOrderHard") // FIXME scope extrusion
    TestHarness("HigherOrderHard", compileResult = false)
    //TestHarness("HigherOrderHard", "0000"::Nil)
    //TestHarness("HigherOrderHard", "0001"::Nil)
  }
  test("HigherOrderRec") {
    TestHarness("HigherOrderRec", exec = true)
    //TestHarness("HigherOrderRec", compileResult = false)
    //TestHarness("HigherOrderRec")
    //TestHarness("HigherOrderRec", "0000"::Nil)
    //TestHarness("HigherOrderRec", "0001"::Nil)
  }
  test("HigherOrderRec2") { // FIXME the minimized lack of reduction problem: due to passing f through each recursive call...
    TestHarness("HigherOrderRec2", dumpGraph = true)
    //TestHarness("HigherOrderRec2", dumpGraph = true, compileResult = false)
    //TestHarness("HigherOrderRec2", "0000"::Nil, dumpGraph = true)
  }
  test("HigherOrderRec3") {
    // FIXME not fully reduced?: see k app
    TestHarness("HigherOrderRec3")
    
    //TestHarness("HigherOrderRec3", "0000"::Nil)
    //TestHarness("HigherOrderRec3", "0001"::Nil)
  }
  test("HigherOrderRec4") {
    TestHarness("HigherOrderRec4", dumpGraph = true)
    //TestHarness("HigherOrderRec4", "0000"::Nil, dumpGraph = true)
  }
  test("HigherOrderRec5") {
    // Note: it looks like the thing is not fully reduced (see k app), but I think it happens on an impossible branch...
    TestHarness("HigherOrderRec5")
  }
  test("HigherOrderRecLocal") {
    TestHarness("HigherOrderRecLocal")
  }
  test("BuildFoldr") {
    TestHarness("BuildFoldr", dumpGraph = true)
    //TestHarness("BuildFoldr", dumpGraph = true, compileResult = false)
  }
  test("IterCont") {
    // FIXME scheduling diverges
    //TestHarness("IterCont", dumpGraph = true, exec = true)
    //TestHarness("IterCont", "0000"::Nil, dumpGraph = true, compileResult = false, exec = true) // FIXME now infinite type error in hs
    TestHarness("IterCont", "0001"::Nil, exec = true)
  }
  test("IterCont2") {
    // Note: used to yield incorrect result [0,0,0] when we used the erroneous recursive call/arg kludge in the old scheduler
    
    // FIXME not fully reduced: nats still constructs k/s lambdas, because of the threading of `f` hrough recursive calls...
    
    TestHarness("IterCont2", exec = true)
    
    //TestHarness("IterCont2", "0000"::Nil, exec = true) // Note: for pass 0001, the old scheduler generates a program that never terminates...
    //TestHarness("IterCont2", "0000"::Nil, exec = true, compileResult = false)
    //TestHarness("IterCont2", "0000"::Nil, exec = true)
    //TestHarness("IterCont2", "0000"::Nil)
    //TestHarness("IterCont2", "0001"::Nil)
    //TestHarness("IterCont2", "0001"::Nil, opt = true)
  }
  test("IterContLocal") {
    TestHarness("IterContLocal", exec = true)
    //TestHarness("IterContLocal", "0000"::Nil/*, exec = true*/)
    //TestHarness("IterContLocal", exec = true)
    //TestHarness("IterContLocal", "0001"::Nil, exec = true) // works now!
  }
  test("IterContLocal2") {
    // TODO simplify case expressions
    // TODO simplify control-flow by some limited duplication, to avoid mostly useless and complicated sharing structures
    // FIXME not fully-reduced! we still have k/s lambdas around -- why?
    TestHarness("IterContLocal2", exec = true)
    //TestHarness("IterContLocal2", "0000"::Nil, exec = true)
    //TestHarness("IterContLocal2", "0001"::Nil, exec = true) // FIXME not reduced: _4 = (\k -> k) in nats
  }
  
}
