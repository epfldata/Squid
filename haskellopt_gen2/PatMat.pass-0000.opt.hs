-- Generated Haskell code from Graph optimizer
-- Core obtained from: The Glorious Glasgow Haskell Compilation System, version 8.6.3
-- Optimized after GHC phase:
--   desugar
-- Beta reductions:  30
-- Incl. one-shot:  1
-- Case reductions:  34
-- Field reductions:  48
-- Total nodes: 841; Boxes: 158; Branches: 166
-- Apps: 138; Lams: 15

{-# LANGUAGE UnboxedTuples #-}
{-# LANGUAGE MagicHash #-}
{-# LANGUAGE NoMonomorphismRestriction  #-}

module PatMat (f1'2,f1'1,f1'0,f1,f0'3,f0'2,f0'1,f0'0,f0,f2'0,slt0,t1'0,t0'0,u0_0,u1_0,u1,u0,t1'1,t1,t0,t'ls,slt1,f2,orZero,e1'1,e1'0,e1,e0'3,e0'2,e0'1,e0'0,e0) where

import Data.Foldable
import GHC.Base
import GHC.Classes
import GHC.Maybe
import GHC.Num
import GHC.Prim
import GHC.Tuple
import GHC.Types

f1'2 = case case 5 > 0 of { True -> Just 5; False -> Nothing } of { Just ρ -> ρ; Nothing -> 0 }

f1'1 = case case 5 > 0 of { True -> Just 5; False -> Nothing } of { Just ρ -> True; Nothing -> False }

f1'0 = case 4 > 0 of { True -> Just 4; False -> Nothing }

f1 = \x -> case x > 0 of { True -> Just x; False -> Nothing }

f0'3 = Just (0 + 1)

f0'2 = Just ((3 + 1) + 1)

f0'1 = Just 0

f0'0 = Just (2 + 1)

f0 = \ds -> case ds of { Just ρ -> Just (ρ + 1); Nothing -> Just 0 }

f2'0 = (1 + 2) + 3

slt0 = \x -> let
  _1 = (Data.Foldable.sum . map (\x' -> x' * 2)) (map (\c' -> c' + (x + 1)) [])
  _0 = Data.Foldable.sum (map (\c -> c + x) [])
  in (,) ((_0 * _0) + fromInteger 1) ((_1 * _1) + fromInteger 1)

t1'0 = ((1 + 2) + 3) + 4

t0'0 = ((1 + 2) + 3) + 4

u0_0 = 1 + 2

u1_0 = ((1 + 2) + 3) + 4

u1 = \ds -> case ds of { (,) ρ ρ' -> (case ρ' of { (,) ρ'2 ρ'3 -> (case ρ'3 of { (,) ρ'4 ρ'5 -> (case ρ'5 of { (,) ρ'6 ρ'7 -> (case ρ'7 of { () -> ((ρ + ρ'2) + ρ'4) + ρ'6 }) }) }) }) }

u0 = \ds -> case ds of { (,) ρ ρ' -> ρ + ρ' }

t1'1 = \xs -> case xs of { (:) ρ ρ' -> (case ρ' of { (:) ρ'2 ρ'3 -> (case ρ'3 of { [] -> ((fromInteger 5 + fromInteger 6) + ρ) + ρ'2; _ -> fromInteger 666 }); _ -> fromInteger 666 }); _ -> fromInteger 666 }

t1 = \ds -> case ds of { (:) ρ ρ' -> (case ρ' of { (:) ρ'2 ρ'3 -> (case ρ'3 of { (:) ρ'4 ρ'5 -> (case ρ'5 of { (:) ρ'6 ρ'7 -> (case ρ'7 of { [] -> ((ρ + ρ'2) + ρ'4) + ρ'6; _ -> fromInteger 666 }); _ -> fromInteger 666 }); _ -> fromInteger 666 }); _ -> fromInteger 666 }); _ -> fromInteger 666 }

t0 = \ds -> case ds of { (:) ρ ρ' -> (case ρ' of { (:) ρ'2 ρ'3 -> (case ρ'3 of { (:) ρ'4 ρ'5 -> (case ρ'5 of { (:) ρ'6 ρ'7 -> (case ρ'7 of { [] -> ((ρ + ρ'2) + ρ'4) + ρ'6; _ -> fromInteger 666 }); _ -> fromInteger 666 }); _ -> fromInteger 666 }); _ -> fromInteger 666 }); _ -> fromInteger 666 }

t'ls = 1 : (2 : (3 : (4 : [])))

slt1 = \ls -> map (\c -> 0) (map (\c' -> 0) ls)

f2 = \ds -> case ds of { (,,) ρ ρ' ρ'2 -> (ρ + ρ') + ρ'2 }

orZero = \ds -> case ds of { Just ρ -> ρ; Nothing -> 0 }

e1'1 = 0

e1'0 = 0

e1 = Nothing

e0'3 = 1

e0'2 = 2 + 1

e0'1 = 2 + 1

e0'0 = 2 + 1

e0 = Just 2