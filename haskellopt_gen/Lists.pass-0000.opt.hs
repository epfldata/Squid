-- Generated Haskell code from Graph optimizer
-- Core obtained from: The Glorious Glasgow Haskell Compilation System, version 8.6.3
-- Optimized after GHC phase:
--   desugar
-- Total nodes: 94; Boxes: 19; Branches: 2
-- Apps: 57; Lams: 5; Unreduced Redexes: 0

{-# LANGUAGE UnboxedTuples #-}
{-# LANGUAGE MagicHash #-}

module Lists (lol,ls0,mutrec0,ls1,main,rec0,mutrec1) where

import Data.Foldable
import GHC.Base
import GHC.List
import GHC.Num
import GHC.Show
import GHC.Types
import System.IO

lol = (\x -> (\y -> (_0(# x, y #))))

_0(# x', y' #) = (((GHC.Num.+) x') y')

ls0 = (((:) 1) (((:) 2) (((:) 3) (((:) 4) []))))

ls1 = ((GHC.Base.map (\ds -> (((GHC.Num.+) ds) (_0(# 11, 22 #))))) ls0)

main = (((GHC.Base.$) System.IO.print) (Data.Foldable.sum ls1))

mutrec0 = ((GHC.List.take (GHC.Types.I# 20#)) a)

a = (((:) 1) (((:) 2) a))

mutrec1 = ((GHC.List.take (GHC.Types.I# 30#)) ((case ds' of {(,) arg0 arg1 -> arg0}) 0))

ds' = (((,) (\x'2 -> _2)) (\y'2 -> _1))

rec0 = rec0'

rec0' = (((:) 1) rec0')

_1 = (((:) (GHC.Num.fromInteger 2)) _2)

_2 = (((:) (GHC.Num.fromInteger 1)) _1)
