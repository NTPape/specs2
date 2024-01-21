package org.specs2
package reporter

import matcher.*
import main.Arguments
import io.StringOutput
import sbt.testing.*
import runner.*
import text.Whitespace.*
import specification.core.*
import specification.process.DefaultExecutor
import scala.collection.mutable.ListBuffer
import language.adhocExtensions

class SbtPrinterSpec(val env: Env) extends Specification with OwnEnv {
  def is = s2"""

 A SbtPrinter should
   print the specification title if defined      ${printer1().e1}
   print HelloWorldSpec ok                       ${printer1().e2}

 Sbt event must be fired when a specification is being executed with the SbtPrinter
   TestEvent: succeed                            ${printer2().e1}
   the duration must be defined                  ${printer2().e2}
   contexts must appear in the name of the event ${printer2().e3}

"""
  val factory = fragmentFactory

  case class printer1() { outer =>

    def e1 =
      printer
        .print((new HelloWorldSpec { override def is = "title".title ^ "\ntext" }).structure)
        .runAction(ownEnv.executionEnv)
      eventually(logger.messages must contain(beMatching("\\[INFO\\].*title.*")))

    def e2 =
      val executed = DefaultExecutor.executeSpec((new HelloWorldSpec).is, ownEnv)

      print(executed).replaceAll("""(\d+ seconds?, )?\d+ ms""", "0 ms").showSpaces ===
        """|HelloWorldSpec
           |⎵
           | This is a specification to check the 'Hello world' string
           |⎵
           | The 'Hello world' string should
           |   + contain 11 characters
           |   + start with 'Hello'
           |   + end with 'world'
           |⎵
           |Total for specification HelloWorldSpec
           |Finished in 0 ms
           |3 examples, 0 failure, 0 error
           | """.stripMargin.showSpaces

    def print(spec: SpecStructure) =
      printer.print(spec).runAction(ownEnv.executionEnv)
      stringOutputLogger.flush()
      stringOutputLogger.messages.mkString("\n")

    val handler = createHandler
    val logger = createLogger

    val stringOutputLogger = new Logger with StringOutput {
      def ansiCodesSupported = false
      def warn(msg: String): Unit = { append(msg) }
      def error(msg: String): Unit = { append(msg) }
      def debug(msg: String): Unit = { append(msg) }
      def trace(t: Throwable): Unit = { append(t.getMessage) }
      def info(msg: String): Unit = { append(msg) }
    }

    lazy val events = new SbtEvents {
      lazy val handler = outer.handler
      lazy val taskDef = new TaskDef("", Fingerprints.fp1, true, Array())
    }
    val env1 = ownEnv.copy(arguments = Arguments("nocolor"))
    val printer = SbtPrinter(env1, Array(logger, stringOutputLogger), events)

  }

  case class printer2() { outer =>
    val logger = createLogger
    val handler = createHandler
    lazy val events = MySbtEvents()

    case class MySbtEvents() extends SbtEvents:
      lazy val handler = outer.handler
      lazy val taskDef = new TaskDef("", Fingerprints.fp1, true, Array())

    val printer = SbtPrinter(env, Array(logger), events)

    def e1 =
      executeAndPrintHelloWorldUnitSpec
      handler.events must contain(eventWithStatus(Status.Success))

    def e2 =
      executeAndPrintHelloWorldUnitSpec
      handler.events must contain(eventWithDurationGreaterThanOrEqualTo(0))

    def e3 =
      executeAndPrintHelloWorldUnitSpec
      handler.events must contain(eventWithNameMatching("HW::The 'Hello world' string should::contain 11 characters"))

    def executeAndPrintHelloWorldUnitSpec =
      val executed = DefaultExecutor.executeSpec((new HelloWorldUnitSpec).is.fragments, ownEnv)
      printer.print(executed).runAction(ownEnv.executionEnv)

  }

  def createHandler = MyEventHandler()

  case class MyEventHandler() extends EventHandler:
    val events = new ListBuffer[Event]
    def handle(event: Event): Unit =
      events.append(event)

  def createLogger = MyLogger()

  case class MyLogger() extends Logger with StringOutput:
    def ansiCodesSupported = false
    def warn(msg: String): Unit = { append("[WARN] " + msg) }
    def error(msg: String): Unit = { append("[ERROR] " + msg) }
    def debug(msg: String): Unit = { append("[DEBUG] " + msg) }
    def trace(t: Throwable): Unit = { append("[TRACE] " + t.getMessage) }
    def info(msg: String): Unit = { append("[INFO] " + msg) }

  def eventWithStatus(s: Status): Matcher[Event] =
    beEqualTo(s) ^^ ((_: Event).status())

  def eventWithDurationGreaterThanOrEqualTo(d: Long): Matcher[Event] =
    beGreaterThanOrEqualTo(d) ^^ ((_: Event).duration())

  def eventWithNameMatching(n: String): Matcher[Event] =
    beLike[Selector] { case ts: TestSelector => ts.testName must beMatching(n) } ^^ ((_: Event).selector())

}

class HelloWorldSpec extends Specification:
  def is = s2"""

 This is a specification to check the 'Hello world' string

 The 'Hello world' string should
   contain 11 characters $e1
   start with 'Hello' $e2
   end with 'world' $e3
"""

  def e1 = "Hello world" must haveSize(11)
  def e2 = "Hello world" must startWith("Hello")
  def e3 = "Hello world" must endWith("world")

class HelloWorldUnitSpec extends org.specs2.mutable.Specification:
  "HW" >> {
    "The 'Hello world' string" should {
      "contain 11 characters" in {
        "Hello world" must haveSize(11)
      }
      "start with 'Hello'" in {
        "Hello world" must startWith("Hello")
      }
      "end with 'world'" in {
        "Hello world" must endWith("world")
      }
    }
  }
