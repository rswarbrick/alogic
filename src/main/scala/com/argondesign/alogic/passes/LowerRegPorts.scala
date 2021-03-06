////////////////////////////////////////////////////////////////////////////////
// Argon Design Ltd. Project P8009 Alogic
// Copyright (c) 2018 Argon Design Ltd. All rights reserved.
//
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// Module: Alogic Compiler
// Author: Geza Lore
//
// DESCRIPTION:
//
// Convert register output ports to local register connected to wire output port
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.passes

import com.argondesign.alogic.ast.TreeTransformer
import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.CompilerContext
import com.argondesign.alogic.core.FlowControlTypes._
import com.argondesign.alogic.core.StorageTypes._
import com.argondesign.alogic.core.Symbols._
import com.argondesign.alogic.core.Types._
import com.argondesign.alogic.typer.TypeAssigner

final class LowerRegPorts(implicit cc: CompilerContext) extends TreeTransformer {

  override def skip(tree: Tree): Boolean = tree match {
    case entity: Entity => entity.variant == "network"
    case _              => false
  }

  override def enter(tree: Tree): Unit = tree match {
    case Decl(symbol, _) => {
      symbol.kind match {
        case TypeOut(kind, FlowControlTypeNone, StorageTypeReg) => {
          // Allocate local register
          val loc = tree.loc
          val rName = "oreg" + cc.sep + symbol.name
          val rSymbol = cc.newTermSymbol(rName, loc, kind)
          // Move the clearOnStall attribute to the register symbol
          symbol.attr.clearOnStall.get foreach { attr =>
            rSymbol.attr.clearOnStall set attr
            symbol.attr.clearOnStall.clear()
          }
          // Move the default attribute to the register symbol
          symbol.attr.default.get foreach { attr =>
            rSymbol.attr.default set attr
            symbol.attr.default.clear()
          }
          symbol.attr.oReg set rSymbol
        }
        case _ =>
      }
    }

    case _ =>
  }

  override def transform(tree: Tree): Tree = tree match {
    case ExprRef(symbol: TermSymbol) => {
      // Rewrite all references to the registered output
      // port as references to the local reg
      symbol.attr.oReg.get map { rSymbol =>
        ExprRef(rSymbol) regularize tree.loc
      } getOrElse {
        tree
      }
    }

    case decl @ Decl(oSymbol, _) => {
      // Change storage type to wire and add register declaration
      oSymbol.attr.oReg.get map { rSymbol =>
        oSymbol.kind = {
          oSymbol.kind.asInstanceOf[TypeOut].copy(fct = FlowControlTypeNone, st = StorageTypeWire)
        }
        val declReg = Decl(rSymbol, None)
        Thicket(List(decl, declReg)) regularize tree.loc
      } getOrElse {
        tree
      }
    }

    case entity: Entity => {
      // Add connects to outputs
      val connects = for {
        Decl(oSymbol, _) <- entity.declarations
        if oSymbol.attr.oReg.isSet
      } yield {
        val rSymbol = oSymbol.attr.oReg.value
        Connect(ExprRef(rSymbol), List(ExprRef(oSymbol))) regularize oSymbol.loc
      }

      if (connects.isEmpty) {
        tree
      } else {
        val result = entity.copy(
          connects = connects ::: entity.connects
        ) withVariant entity.variant
        TypeAssigner(result withLoc tree.loc)
      }
    }

    case _ => tree
  }

  override def finalCheck(tree: Tree): Unit = {
    def check(node: Tree, kind: Type) = {
      kind visit {
        case TypeOut(_, _, StorageTypeReg) => {
          cc.ice(node, s"Output port with non-wire storage remains")
        }
      }
    }

    tree visit {
      case node @ Decl(symbol, _) => check(node, symbol.kind)
    }
  }

}

object LowerRegPorts extends TreeTransformerPass {
  val name = "lower-reg-ports"
  def create(implicit cc: CompilerContext) = new LowerRegPorts
}
