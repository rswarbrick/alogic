////////////////////////////////////////////////////////////////////////////////
// Argon Design Ltd. Project P8009 Alogic
// Copyright (c) 2017-2018 Argon Design Ltd. All rights reserved.
//
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// Module: Alogic Compiler
// Author: Geza Lore
//
// DESCRIPTION:
//
// Common members of ast.Trees.Expr
// These are factored out into a separate file to keep ast.Trees readable
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.ast

import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.CompilerContext
import com.argondesign.alogic.core.Symbols.TermSymbol
import com.argondesign.alogic.core.Types._
import com.argondesign.alogic.passes.FoldExpr
import com.argondesign.alogic.util.PartialMatch._
import com.argondesign.alogic.util.unreachable

import scala.language.implicitConversions
import scala.math.BigInt.int2bigInt

trait ExprOps { this: Expr =>

  private final def addLoc(expr: Expr): expr.type = {
    if (hasLoc) expr withLoc loc else expr
  }

  private final def makeExprBinary(op: String, rhs: Expr) = {
    val expr = ExprBinary(this, op, rhs)
    if (hasLoc) {
      if (!rhs.hasLoc) { rhs withLoc loc }
      expr withLoc loc
    }
    expr
  }

  private final def makeExprCall(symbol: TermSymbol, args: Expr*) = {
    val expr = ExprCall(ExprRef(symbol), args.toList)
    if (hasLoc) {
      for (arg <- args if !arg.hasLoc) { arg withLoc loc }
      expr.expr visitAll { case tree: Tree => tree withLoc loc }
      expr withLoc loc
    }
    expr
  }

  // Helpers to easily combine expression trees manually with other expressions
  final def *(rhs: Expr): Expr = makeExprBinary("*", rhs)
  final def /(rhs: Expr): Expr = makeExprBinary("/", rhs)
  final def %(rhs: Expr): Expr = makeExprBinary("%", rhs)
  final def +(rhs: Expr): Expr = makeExprBinary("+", rhs)
  final def -(rhs: Expr): Expr = makeExprBinary("-", rhs)
  final def <<(rhs: Expr): Expr = makeExprBinary("<<", rhs)
  final def >>(rhs: Expr): Expr = makeExprBinary(">>", rhs)
  final def <<<(rhs: Expr): Expr = makeExprBinary("<<<", rhs)
  final def >>>(rhs: Expr): Expr = makeExprBinary(">>>", rhs)
  final def &(rhs: Expr): Expr = makeExprBinary("&", rhs)
  final def ^(rhs: Expr): Expr = makeExprBinary("^", rhs)
  final def |(rhs: Expr): Expr = makeExprBinary("|", rhs)
  final def &&(rhs: Expr): Expr = makeExprBinary("&&", rhs)
  final def ||(rhs: Expr): Expr = makeExprBinary("||", rhs)

  final def *(rhs: Int): Expr = makeExprBinary("*", Expr(rhs))
  final def /(rhs: Int): Expr = makeExprBinary("/", Expr(rhs))
  final def %(rhs: Int): Expr = makeExprBinary("%", Expr(rhs))
  final def +(rhs: Int): Expr = makeExprBinary("+", Expr(rhs))
  final def -(rhs: Int): Expr = makeExprBinary("-", Expr(rhs))
  final def <<(rhs: Int): Expr = makeExprBinary("<<", Expr(rhs))
  final def >>(rhs: Int): Expr = makeExprBinary(">>", Expr(rhs))
  final def <<<(rhs: Int): Expr = makeExprBinary("<<<", Expr(rhs))
  final def >>>(rhs: Int): Expr = makeExprBinary(">>>", Expr(rhs))
  final def &(rhs: Int): Expr = makeExprBinary("&", Expr(rhs))
  final def ^(rhs: Int): Expr = makeExprBinary("^", Expr(rhs))
  final def |(rhs: Int): Expr = makeExprBinary("|", Expr(rhs))
  final def &&(rhs: Int): Expr = makeExprBinary("&&", Expr(rhs))
  final def ||(rhs: Int): Expr = makeExprBinary("||", Expr(rhs))

  final def max(rhs: Expr)(implicit cc: CompilerContext): Expr = {
    makeExprCall(cc.lookupGlobalTerm("@max"), this, rhs)
  }
  final def max(rhs: Int)(implicit cc: CompilerContext): Expr = {
    makeExprCall(cc.lookupGlobalTerm("@max"), this, Expr(rhs))
  }

  final def index(idx: Expr): ExprIndex = addLoc(ExprIndex(this, idx))
  final def index(idx: Int): ExprIndex = addLoc(ExprIndex(this, addLoc(Expr(idx))))

  final def slice(lidx: Expr, op: String, ridx: Expr): ExprSlice = {
    addLoc(ExprSlice(this, lidx, op, ridx))
  }
  final def slice(lidx: Expr, op: String, ridx: Int): ExprSlice = {
    addLoc(ExprSlice(this, lidx, op, addLoc(Expr(ridx))))
  }
  final def slice(lidx: Int, op: String, ridx: Expr): ExprSlice = {
    addLoc(ExprSlice(this, addLoc(Expr(lidx)), op, ridx))
  }
  final def slice(lidx: Int, op: String, ridx: Int): ExprSlice = {
    addLoc(ExprSlice(this, addLoc(Expr(lidx)), op, addLoc(Expr(ridx))))
  }

  final def select(name: String): ExprSelect = addLoc(ExprSelect(this, name))
  final def call(args: List[Expr]): ExprCall = addLoc(ExprCall(this, args))

  final def cat(rhs: Expr): ExprCat = addLoc(ExprCat(List(this, rhs)))

  final def rep(cnt: Expr): ExprRep = addLoc(ExprRep(cnt, this))
  final def rep(cnt: Int): ExprRep = addLoc(ExprRep(addLoc(Expr(cnt)), this))

  final def unary_+ : this.type = this
  final def unary_- : ExprUnary = addLoc(ExprUnary("-", this))
  final def unary_~ : ExprUnary = addLoc(ExprUnary("~", this))
  final def unary_! : ExprUnary = addLoc(ExprUnary("!", this))

  // Is this expression shaped as a valid type expression
  lazy val isTypeExpr: Boolean = this forall {
    case _: ExprIdent        => true
    case _: ExprRef          => true
    case _: ExprType         => true
    case ExprSelect(expr, _) => expr.isTypeExpr
    case _                   => false
  }

  // Is this expression shaped as a valid lvalue expression
  lazy val isLValueExpr: Boolean = this forall {
    case _: ExprIdent             => true
    case _: ExprRef               => true
    case ExprIndex(expr, _)       => expr.isLValueExpr
    case ExprSlice(expr, _, _, _) => expr.isLValueExpr
    case ExprSelect(expr, _)      => expr.isLValueExpr
    case ExprCat(parts)           => parts forall { _.isLValueExpr }
    case _                        => false
  }

  // Is this expression shaped as a valid port reference expression
  lazy val isPortRefExpr: Boolean = this match {
    case _: ExprIdent                => true
    case _: ExprRef                  => true
    case ExprSelect(_: ExprIdent, _) => true
    case ExprSelect(_: ExprRef, _)   => true
    case _                           => false
  }

  // Is this expression a known constant
  def isKnownConst(implicit cc: CompilerContext): Boolean = this match {
    case _: ExprNum  => true
    case _: ExprInt  => true
    case _: ExprStr  => true
    case _: ExprType => true
    case ExprRef(symbol) =>
      symbol.kind match {
        case _: TypeConst => true
        case _            => false
      }
    case ExprUnary(_, expr)      => expr.isKnownConst
    case ExprBinary(lhs, _, rhs) => lhs.isKnownConst && rhs.isKnownConst
    case ExprTernary(cond, thenExpr, elseExpr) =>
      cond.isKnownConst && thenExpr.isKnownConst && elseExpr.isKnownConst
    case ExprRep(count, expr)   => count.isKnownConst && expr.isKnownConst
    case ExprCat(parts)         => parts forall { _.isKnownConst }
    case ExprIndex(expr, index) => expr.isKnownConst && index.isKnownConst
    case ExprSlice(expr, lidx, op, ridx) =>
      expr.isKnownConst && lidx.isKnownConst && ridx.isKnownConst
    case ExprSelect(expr, _) => expr.isKnownConst
    case call @ ExprCall(ExprRef(symbol), _) if symbol.isBuiltin =>
      cc.isKnownConstBuiltinCall(call)
    case _ => false
  }

  // Simplify this expression
  def simplify(implicit cc: CompilerContext): Expr = {
    this rewrite { new FoldExpr(assignTypes = hasTpe, foldRefs = true) } match {
      case expr: Expr => expr
      case _          => unreachable
    }
  }

  // Value of this expression if it can be determined right now, otherwise None
  def value(implicit cc: CompilerContext): Option[BigInt] = simplify match {
    // TODO: follow constants
    case ExprNum(_, value)    => Some(value)
    case ExprInt(_, _, value) => Some(value)
    case _                    => None
  }

  // Width of this expression if can be computed, note that this does not
  // necessarily need type info ...
  def width(implicit cc: CompilerContext): Option[Expr] = simplify match {
    // TODO: more possible cases
    case ExprInt(_, width, _) => Some(addLoc(Expr(width)))
    case _                    => tpeOpt map { _.width }
  }

}

trait ObjectExprOps {
  // Helpers to easily create ExprNum from integers
  final def apply(n: Int): ExprNum = ExprNum(false, n)
  final def apply(n: BigInt): ExprNum = ExprNum(false, n)

  object ImplicitConversions {
    // Implicit conversion for Int to ExprNum
    implicit final def int2ExprNum(n: Int): ExprNum = apply(n)
  }

  // And extractor so we can match against the the same as above
  final def unapply(num: ExprNum): Option[Int] = if (!num.signed) Some(num.value.toInt) else None

  // Extractors to match operators naturally
  final object * {
    def unapply(expr: ExprBinary) = if (expr.op == "*") Some((expr.lhs, expr.rhs)) else None
  }
  final object / {
    def unapply(expr: ExprBinary) = if (expr.op == "/") Some((expr.lhs, expr.rhs)) else None
  }
  final object % {
    def unapply(expr: ExprBinary) = if (expr.op == "%") Some((expr.lhs, expr.rhs)) else None
  }
  final object + {
    def unapply(expr: ExprBinary) = if (expr.op == "+") Some((expr.lhs, expr.rhs)) else None
  }
  final object - {
    def unapply(expr: ExprBinary) = if (expr.op == "-") Some((expr.lhs, expr.rhs)) else None
  }
  final object << {
    def unapply(expr: ExprBinary) = if (expr.op == "<<") Some((expr.lhs, expr.rhs)) else None
  }
  final object >> {
    def unapply(expr: ExprBinary) = if (expr.op == ">>") Some((expr.lhs, expr.rhs)) else None
  }
  final object >>> {
    def unapply(expr: ExprBinary) = if (expr.op == ">>>") Some((expr.lhs, expr.rhs)) else None
  }
  final object <<< {
    def unapply(expr: ExprBinary) = if (expr.op == "<<<") Some((expr.lhs, expr.rhs)) else None
  }
  final object & {
    def unapply(expr: ExprBinary) = if (expr.op == "&") Some((expr.lhs, expr.rhs)) else None
  }
  final object ^ {
    def unapply(expr: ExprBinary) = if (expr.op == "^") Some((expr.lhs, expr.rhs)) else None
  }
  final object | {
    def unapply(expr: ExprBinary) = if (expr.op == "|") Some((expr.lhs, expr.rhs)) else None
  }
  final object && {
    def unapply(expr: ExprBinary) = if (expr.op == "&&") Some((expr.lhs, expr.rhs)) else None
  }
  final object || {
    def unapply(expr: ExprBinary) = if (expr.op == "||") Some((expr.lhs, expr.rhs)) else None
  }

  // Extractor for instance port references
  final object InstancePortRef {

    def unapply(expr: ExprSelect): Option[(TermSymbol, TermSymbol)] = expr partialMatch {
      case ExprSelect(ExprRef(iSymbol: TermSymbol), sel) if iSymbol.kind.isInstance =>
        (iSymbol, iSymbol.kind.asInstanceOf[TypeInstance].portSymbol(sel).get)
    }
  }
}
