import cats.effect.std.{Queue, QueueSource}
import cats.effect.{IO, IOApp}
import cats.syntax.all._
import fs2._

object StreamTests extends IOApp.Simple {

  def fromUnterminatedQueue[A](q: QueueSource[IO, A]): Stream[IO, A] = Stream.evalSeq(q.tryTakeN(None)).repeat

  def run: IO[Unit] = {

    val x = Queue.bounded[IO, Long](65536).flatMap { q =>
      val n = 100_000

      val producer =
        IO.realTime.flatMap { ts =>
          q.offer(ts.toMillis)
        }.replicateA_(n)

      val consumer = Stream.fromQueueUnterminated(q).take(n).compile.drain

      val consumer2 = fromUnterminatedQueue(q).take(n).compile.drain

      (producer, consumer2).parTupled
    }.void

    val go = List.fill(100)(x)
      .parSequence_
      .replicateA_(5)
      .timed
      .map(_._1.toMillis)

    go >> go.flatTap(IO.println) replicateA_ (10)
  }

}
