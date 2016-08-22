package scp
package feature

class RepEquality extends MyFunSuite2 {
  
  import TestDSL2.Predef._
  
  test("Functions") {
    
    assert(ir"(x: Int) => x" =~= ir"(x: Int) => x")
    
    assert(ir"(x: Int) => x" =~= ir"(y: Int) => y")
    
    assert(ir"val x = 42.toDouble; x + 1" =~= ir"val y = 42.toDouble; y + 1")
    
  }
  
  
  
}
