-- Generated Haskell code from Graph optimizer
-- Core obtained from: The Glorious Glasgow Haskell Compilation System, version 8.6.3
-- Optimized after GHC phase:
--   desugar
-- Beta reductions:  6
-- Incl. one-shot:   0
-- Case reductions:  4
-- Field reductions: 4
-- Case commutings:  5
-- Total nodes: 133; Boxes: 34; Branches: 21
-- Apps: 22; Lams: 3

{-# LANGUAGE UnboxedTuples #-}
{-# LANGUAGE MagicHash #-}
{-# LANGUAGE NoMonomorphismRestriction  #-}

module ListsFun (test,enumFromTo_mine,length_mine) where

import GHC.Classes
import GHC.Num
import GHC.Types

test = let
  _0 = (0::Int) + (1::Int)
  rec' _1 to = case _1 > to of { True -> []; False -> _1 : (rec' (_1 + (1::Int)) to) }
  rec _cfε = case _cfε of { (:) ρ ρ' -> (rec ρ') + (1::Int); [] -> (0::Int) }
  in case (0::Int) > (5::Int) of { True -> (0::Int); False -> (case _0 > (5::Int) of { True -> (0::Int); False -> (rec (rec' (_0 + (1::Int)) (5::Int))) + (1::Int) }) + (1::Int) }

enumFromTo_mine = \from -> \to -> 
        let rec _0 to' = case _0 > to' of { True -> []; False -> _0 : (rec (_0 + (1::Int)) to') } in
        case from > to of { True -> []; False -> from : (rec (from + (1::Int)) to) }

length_mine = \ds -> 
  let rec _cfε = case _cfε of { (:) ρ'2 ρ'3 -> (rec ρ'3) + (1::Int); [] -> (0::Int) } in
  case ds of { (:) ρ ρ' -> (rec ρ') + (1::Int); [] -> (0::Int) }
