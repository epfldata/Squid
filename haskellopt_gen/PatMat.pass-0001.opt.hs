-- Generated Haskell code from Graph optimizer
-- Core obtained from: The Glorious Glasgow Haskell Compilation System, version 8.6.3
-- Optimized after GHC phase:
--   Simplifier: Max iterations = 4
--               SimplMode {Phase = 0 [Non-opt simplification],
--                          inline,
--                          no rules,
--                          eta-expand,
--                          case-of-case}
-- Total nodes: 525; Boxes: 128; Branches: 44
-- Apps: 96; Lams: 22; Unreduced Redexes: 0

{-# LANGUAGE UnboxedTuples #-}
{-# LANGUAGE MagicHash #-}
{-# LANGUAGE NoMonomorphismRestriction  #-}

module PatMat (tf,x,f0'1,bat,e0'2,usum'0,f0'0,e1,f1'0,f2'0,f1,e0'1,a,slt0,f0'2,e0,orZero,f0,f1'2,tlsf,ds,tls,e1'1,b,f0'3,e0'0,c,f1'1,slt1,usum,e1'0,e0'3,f2) where

import Control.Exception.Base
import Data.Foldable
import GHC.Base
import GHC.Classes
import GHC.Maybe
import GHC.Num
import GHC.Prim
import GHC.Types

a = 1

b = 2

bat = (\ds' -> (case ds' of {(,) arg0 arg1 -> (_0(# arg0, arg1 #))}))

_0(# ds'2, ds'3 #) = (let sh = (r(# ds'2, ds'3 #)) in (((GHC.Num.+) (((GHC.Num.*) sh) sh)) (GHC.Num.fromInteger 1)))

c = 3

ds = 0

e0 = (GHC.Maybe.Just 2)

e0'0 = (((GHC.Num.+) 2) 1)

e0'1 = (((GHC.Num.+) 2) 1)

e0'2 = (((GHC.Num.+) 2) 1)

e0'3 = 1

e1 = GHC.Maybe.Nothing

e1'0 = 0

e1'1 = 0

f0 = (\ds'4 -> (case ds'4 of {Nothing -> (GHC.Maybe.Just 0); Just arg0' -> (GHC.Maybe.Just (((GHC.Num.+) arg0') 1))}))

f0'0 = (GHC.Maybe.Just (((GHC.Num.+) 2) 1))

f0'1 = (GHC.Maybe.Just 0)

f0'2 = (GHC.Maybe.Just (((GHC.Num.+) (((GHC.Num.+) 3) 1)) 1))

f0'3 = (GHC.Maybe.Just (((GHC.Num.+) 0) 1))

x = 0

f1 = (\x' -> (_1(# x' #)))

_1(# x'2 #) = (case (((GHC.Classes.>) x'2) 0) of {False -> GHC.Maybe.Nothing; True -> (GHC.Maybe.Just x'2)})

f1'0 = (_1(# 4 #))

f1'1 = (case (_1(# 5 #)) of {Nothing -> GHC.Types.False; Just arg0'2 -> GHC.Types.True})

f1'2 = (_2(# (_1(# 5 #)) #))

_2(# ds'5 #) = (case ds'5 of {Nothing -> 0; Just arg0'3 -> arg0'3})

f2 = (\ds'6 -> (case ds'6 of {(,,) arg0'4 arg1' arg2 -> (((GHC.Num.+) (((GHC.Num.+) arg0'4) arg1')) arg2)}))

f2'0 = (((GHC.Num.+) (((GHC.Num.+) 1) 2)) 3)

orZero = (\ds'7 -> (_2(# ds'7 #)))

slt0 = (\x'3 -> (((,) (_0(# Data.Foldable.sum, x'3 #))) (_0(# (((GHC.Base..) Data.Foldable.sum) (\xs -> (GHC.Base.build (\c' -> (\n -> (((GHC.Base.foldr (((GHC.Base..) c') (\x'4 -> (((GHC.Num.*) x'4) 2)))) n) xs)))))), (((GHC.Num.+) x'3) 1) #))))

slt1 = (\ls -> (GHC.Base.build (\c'2 -> (\n' -> (((GHC.Base.foldr (((GHC.Base..) (((GHC.Base..) c'2) (\c'3 -> 0))) (\c'4 -> 0))) n') ls)))))

tf = (\ds'8 -> (case ds'8 of {[] -> _3; (:) arg0'5 arg1'2 -> (case arg1'2 of {[] -> _3; (:) arg0'6 arg1'3 -> (case arg1'3 of {[] -> _3; (:) arg0'7 arg1'4 -> (case arg1'4 of {[] -> _3; (:) arg0'8 arg1'5 -> (case arg1'5 of {[] -> (_4(# arg0'5, arg0'6, arg0'7, arg0'8 #)); (:) arg0'9 arg1'6 -> _3})})})})}))

_3 = (Control.Exception.Base.patError "/Volumes/Macintosh HD/Users/lptk/work/EPFL/DATA/git/alt/Squid-1.0/haskellopt/src/test/haskell/PatMat.hs:50:1-22|function tf"#)

_4(# ds'9, ds'10, ds'11, ds'12 #) = (((GHC.Num.+) (((GHC.Num.+) (((GHC.Num.+) ds'9) ds'10)) ds'11)) ds'12)

tls = (((:) 1) (((:) 2) (((:) 3) (((:) 4) []))))

tlsf = (_4(# 1, 2, 3, 4 #))

usum = (\eta -> (_5(# eta #)))

_5(# ds'13 #) = (case ds'13 of {[] -> _6; (:) arg0'10 arg1'7 -> (((GHC.Num.+) arg0'10) (_5(# arg1'7 #)))})

usum'0 = _6

_6 = (GHC.Num.fromInteger 0)

r(# ds'14, ds'15 #) = (ds'14 (GHC.Base.build (\c'5 -> (\n'2 -> (((GHC.Base.foldr (((GHC.Base..) c'5) (\c'6 -> (((GHC.Num.+) c'6) ds'15)))) n'2) [])))))
