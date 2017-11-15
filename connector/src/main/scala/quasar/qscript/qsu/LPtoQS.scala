/*
 * Copyright 2014–2017 SlamData Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.qscript.qsu

import slamdata.Predef.AnyVal
import quasar.NameGenerator
import quasar.Planner.PlannerErrorME
import quasar.frontend.logicalplan.LogicalPlan

import matryoshka.{BirecursiveT, EqualT}
import scalaz.{Functor, Kleisli => K, Monad}

final class LPtoQS[T[_[_]]: BirecursiveT: EqualT] extends QSUTTypes[T] {
  import LPtoQS.MapSyntax

  def apply[F[_]: Monad: PlannerErrorME: NameGenerator](lp: T[LogicalPlan])
      : F[T[QScriptEducated]] = {

    val lpToQs =
      K(ReadLP[T].apply[F])          >-
      EliminateUnary[T].apply        >-
      RecognizeDistinct[T].apply     >==>
      ExtractFreeMap[T, F]           >==>
      MinimizeAutoJoins[T].apply[F]  >==>
      ApplyProvenance[T].apply[F]    >=>
      K(ReifyProvenance[T].apply[F]) >-
      (_.graph)                      >==>
      Graduate[T].apply[F]

    lpToQs(lp)
  }
}

object LPtoQS {
  def apply[T[_[_]]: BirecursiveT: EqualT]: LPtoQS[T] = new LPtoQS[T]

  final implicit class MapSyntax[F[_], A](val self: F[A]) extends AnyVal {
    def >-[B](f: A => B)(implicit F: Functor[F]): F[B] =
      F.map(self)(f)
  }
}