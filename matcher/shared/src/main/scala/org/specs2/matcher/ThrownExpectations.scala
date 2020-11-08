package org.specs2
package matcher

import execute._
import execute.Skipped
import execute.Pending
import execute.Failure
import execute.PendingException
import execute.SkipException
import execute.FailureException

/**
 * Thrown expectations will throw a FailureException if a match fails
 *
 * This trait can be extended to be used in another framework like ScalaTest:
 *
 *   trait ScalaTestExpectations extends ThrownExpectations {
 *     override protected def checkMatchResultFailure[T](m: =>MatchResult[T]) = {
 *       m match {
 *         case f @ MatchFailure(ok, ko, _, _, _) => throw new TestFailedException(f.message, f.exception, 0)
 *         case _ => ()
 *       }
 *       m
 *     }
 *   }
 */
trait ThrownExpectations extends ThrownExpectationsCreation with ThrownStandardResults with ThrownStandardMatchResults

/**
 * Lightweight ThrownExpectations trait with less implicit methods
 */
trait ThrownExpectationsCreation extends ThrownExpectables

trait ThrownExpectables extends ExpectationsCreation:

  /** @return an Expectable with a description function */
  override def createExpectable[T](t: =>T, alias: Option[String => String]): Expectable[T] =
    val checker = new Checker:
      def check[T](result: MatchResult[T]): MatchResult[T] =
        checkMatchResultFailure(result)

    Expectable(() => t, checker, alias)

  override protected def checkResultFailure(result: =>Result) =
    lazy val r = result
    r match
      case f@Failure(_, _, _, _) => throw new FailureException(f)
      case s@Skipped(_, _) => throw new SkipException(s)
      case s@Pending(_) => throw new PendingException(s)
      case e@Error(_, _) => throw new ErrorException(e)
      case d@DecoratedResult(_, r) => if !r.isSuccess then throw new DecoratedResultException(d) else ()
      case _ => ()
    r

  /** this method can be overridden to throw exceptions when checking the match result */
  override protected def checkMatchResultFailure[T](m: MatchResult[T]) =
    m match
      case f@MatchFailure(_, _, _, _, _) => throw new MatchFailureException(f)
      case s@MatchSkip(_, _) => throw new MatchSkipException(s)
      case p@MatchPending(_, _) => throw new MatchPendingException(p)
      case _ => ()
    m

trait ThrownStandardResults extends StandardResults with ExpectationsCreation:
  override def failure: Failure = { checkResultFailure(throw new FailureException(StandardResults.failure)); StandardResults.failure }
  override def todo: Pending = { checkResultFailure(throw new PendingException(super.todo)); super.todo }
  override def anError: Error = { checkResultFailure(throw new ErrorException(super.anError)); super.anError }

  override lazy val success = Success("success")

  protected def success(m: String): Success = { checkResultFailure(Success(m)); Success(m) }
  override def failure(m: String): Failure = failure(Failure(m))

  protected def failure(f: Failure): Failure = { checkResultFailure(throw new FailureException(f)); f }

  override def pending: Pending = pending("PENDING")
  override def pending(m: String): Pending = pending(Pending(m))
  protected def pending(p: Pending): Pending = { checkResultFailure(throw new PendingException(p)); p }

  override def skipped: Skipped = skipped("skipped")
  override def skipped(m: String): Skipped = skipped(Skipped(m))
  protected def skipped(s: Skipped): Skipped = { checkResultFailure(throw new SkipException(s)); s }

trait ThrownStandardMatchResults extends StandardMatchResults with ExpectationsCreation:
  override lazy val ko: MatchResult[Any] =
    checkMatchResultFailure(throw new MatchFailureException(
      MatchFailure("ok", "ko", createExpectable(None))))

  /** @return the value without any side-effects for expectations */
  override def sandboxMatchResult[T](mr: =>MatchResult[T]): MatchResult[T] =
    try mr
    catch
      case MatchFailureException(e) => e.asInstanceOf[MatchResult[T]]
      case MatchSkipException(e)    => e.asInstanceOf[MatchResult[T]]
      case MatchPendingException(e) => e.asInstanceOf[MatchResult[T]]
      case other: Throwable         => throw other


object ThrownExpectations extends ThrownExpectations

/**
 * This trait can be used to cancel the effect of thrown expectations.
 *
 * For example it can be mixed-in a mutable.Specification so that no exception is thrown on failure
 */
trait NoThrownExpectations extends Expectations:
  override protected def checkResultFailure(r: =>Result) = r
  override protected def checkMatchResultFailure[T](m: MatchResult[T]): MatchResult[T] = m

/**
 * This trait can be used to integrate failures and skip messages into specs2
 */
trait ThrownMessages { this: ThrownExpectations =>
  def fail(m: String): Nothing = throw new FailureException(Failure(m))
  def skip(m: String): Nothing = throw new SkipException(Skipped(m))
}

/** this class allows to throw a match failure result in an Exception */
class MatchFailureException[T](val failure: MatchFailure[T]) extends FailureException(failure.toFailure) with MatchResultException[T]:
  lazy val matchResult = failure

  override def getMessage = f.message
  override def getCause = f.exception
  override def getStackTrace = f.exception.getStackTrace
object MatchFailureException:
  def unapply[T](m: MatchFailureException[T]): Option[MatchFailure[T]] = Some(m.failure)
/** this class allows to throw a skipped match result in an Exception */
class MatchSkipException[T](val s: MatchSkip[T]) extends SkipException(s.toSkipped) with MatchResultException[T]:
  lazy val matchResult = s
object MatchSkipException:
  def unapply[T](m: MatchSkipException[T]): Option[MatchSkip[T]] = Some(m.s)

/** this class allows to throw a pending result in an Exception */
class MatchPendingException[T](val p: MatchPending[T]) extends PendingException(p.toPending) with MatchResultException[T]:
  lazy val matchResult = p
object MatchPendingException:
  def unapply[T](m: MatchPendingException[T]): Option[MatchPending[T]] = Some(m.p)

trait MatchResultException[T]:
  def matchResult: MatchResult[T]
