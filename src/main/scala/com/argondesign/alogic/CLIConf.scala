////////////////////////////////////////////////////////////////////////////////
// Argon Design Ltd. Project P8009 Alogic
// Copyright (c) 2017-2018 Argon Design Ltd. All rights reserved.
//
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// Module: Alogic Compiler CLI
// Author: Geza Lore
//
// DESCRIPTION:
//
// Command line option parser
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic

import java.io.File

import org.rogach.scallop.ArgType
import org.rogach.scallop.ScallopConf
import org.rogach.scallop.ScallopOption
import org.rogach.scallop.ValueConverter
import org.rogach.scallop.singleArgConverter

// Option parser based on Scallop. See the Scallop wiki for usage:
// https://github.com/scallop/scallop/wiki
class CLIConf(args: Seq[String]) extends ScallopConf(args) {
  private[this] implicit val fileConverter =
    singleArgConverter[File](path => (new File(path)).getCanonicalFile())

  // Ensures all option instances have only a single argument
  // eg -I foo -I bar -I baz, but not -I foo bar
  private[this] val singlefileListConverter = new ValueConverter[List[File]] {

    def parse(instances: List[(String, List[String])]) = {
      val bad = instances.filter(_._2.size > 1)
      if (!bad.isEmpty) {
        val msg: List[String] = "Only one argument can be provided for each " +
          s"instance of option '${bad.head._1}'. Provided:" :: (for ((_, r) <- bad)
          yield r mkString " ");
        Left(msg mkString "\n")
      } else {
        Right(Some(instances.flatMap(_._2) map { path =>
          (new File(path)).getCanonicalFile()
        }))
      }
    }
    val argType = ArgType.SINGLE
  }

  private[this] def validateListOption[T](
      option: ScallopOption[List[T]]
  )(
      check: PartialFunction[T, String]
  ) = addValidation {
    val msgs = option.toOption.getOrElse(Nil) collect check
    if (msgs.nonEmpty) {
      Left(msgs mkString ("\n", "\n", ""))
    } else {
      Right(())
    }
  }

  private[this] def validateFilesExist(option: ScallopOption[List[File]]) =
    validateListOption(option) {
      case path: File if !path.exists() => s"'${path}' does not exist"
    }

  private[this] def validateFilesAreDirectories(option: ScallopOption[List[File]]) =
    validateListOption(option) {
      case path: File if !path.isDirectory() => s"'${path}' is not a directory"
    }

  printedName = "alogic"

  banner("Alogic compiler")

  val ydir = opt[List[File]](
    short = 'y',
    descr = "Directory to search for entities"
  )(singlefileListConverter)
  validateFilesExist(ydir)
  validateFilesAreDirectories(ydir)

  val incdir = opt[List[File]](
    short = 'I',
    descr = "Directory to search for includes"
  )(singlefileListConverter)
  validateFilesExist(incdir)
  validateFilesAreDirectories(incdir)

  val odir = opt[File](
    short = 'o',
    required = true,
    descr = "Output directory. See description of --srcbase as well"
  )

  val defs = props[String](
    name = 'D',
    keyName = "name",
    descr = "Predefine preprocessor macro"
  )

  val srcbase = opt[File](
    noshort = true,
    required = false,
    descr = """|Base directory for source files. When specified, all directories
               |specified with -y must be under this directory, and output files
               |will be written to the same relative path under the output
               |directory specified with -o, as the corresponding source is
               |relative to --srcbase. When --srcbase is not provided, output
               |files are written to the output directory directly
               |""".stripMargin.replace('\n', ' ')
  )

  validateOpt(srcbase, ydir) {
    case (Some(base), Some(ys)) => {
      val basePath = base.toPath.toRealPath()
      val bad = ys filterNot { _.toPath.toRealPath() startsWith basePath }
      if (bad.isEmpty) {
        Right(Unit)
      } else {
        val msgs = for (file <- bad) yield s"-y '${file}' is not under --srcbase '${base}'"
        Left(msgs mkString "\n")
      }
    }
    case _ => Right(Unit)
  }

  val sep = opt[String](
    noshort = true,
    required = false,
    descr = "Structure field separator sequence used in the output. Default is '__'.",
    default = Some("__")
  )

  val uninitialized = opt[String](
    noshort = true,
    required = false,
    descr = """|Specify whether to default initialize local variables declared
               |without an explicit initializer expression. Possible values
               |are: 'none' meaning leave them un-initialized, 'zeros' means
               |initialize them to zero. 'ones' means initialize them to all
               |ones, 'random' means initialize them to a compile time constant,
               |deterministic, but otherwise arbitrary bit pattern. Default is
               |'none'
               |""".stripMargin.replace('\n', ' '),
    default = Some("none")
  )

  validate(uninitialized) { value =>
    val valid = List("none", "zeros", "ones", "random")
    if (valid contains value) {
      Right(Unit)
    } else {
      Left("Option 'uninitialized' must be one of: " + (valid mkString " "))
    }
  }

  val toplevel = trailArg[List[String]](
    required = true,
    descr = "List of top level entity names"
  )

  verify()
}
