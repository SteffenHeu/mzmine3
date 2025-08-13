/*
 * Copyright (c) 2004-2025 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.tools.tools_autoparam.optimizer;

import org.moeaframework.algorithm.AGEMOEAII;
import org.moeaframework.algorithm.AbstractAlgorithm;
import org.moeaframework.algorithm.CMAES;
import org.moeaframework.algorithm.DBEA;
import org.moeaframework.algorithm.EpsilonMOEA;
import org.moeaframework.algorithm.EpsilonNSGAII;
import org.moeaframework.algorithm.GDE3;
import org.moeaframework.algorithm.IBEA;
import org.moeaframework.algorithm.MOEAD;
import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.algorithm.NSGAIII;
import org.moeaframework.algorithm.PAES;
import org.moeaframework.algorithm.RVEA;
import org.moeaframework.algorithm.SMSEMOA;
import org.moeaframework.algorithm.SPEA2;
import org.moeaframework.algorithm.UNSGAIII;
import org.moeaframework.algorithm.pso.OMOPSO;
import org.moeaframework.algorithm.pso.SMPSO;
import org.moeaframework.algorithm.sa.AMOSA;
import org.moeaframework.problem.AbstractProblem;

public enum OptimizerOptions {
  MOEAD, NSGA_II, NSGA_III, AGEMOEA_II, AMOSA, DBEA, CMAES, GDE3, PAES, OMOPSO, RVEA, SMPSO, SMSEMOA, SPEA2, UNSGAIII, EpsilonMOEA, EpsilonNSGAII, IBEA;

  public AbstractAlgorithm getOptimizer(AbstractProblem problem) {
    return switch (this) {
      case MOEAD -> new MOEAD(problem);
      case NSGA_II -> new NSGAII(problem);
      case NSGA_III -> new NSGAIII(problem);
      case AGEMOEA_II -> new AGEMOEAII(problem);
      case AMOSA -> new AMOSA(problem);
      case DBEA -> new DBEA(problem);
      case CMAES -> new CMAES(problem);
      case GDE3 -> new GDE3(problem);
      case PAES -> new PAES(problem);
      case OMOPSO -> new OMOPSO(problem);
      case RVEA -> new RVEA(problem);
      case SMPSO -> new SMPSO(problem);
      case SMSEMOA -> new SMSEMOA(problem);
      case SPEA2 -> new SPEA2(problem);
      case UNSGAIII -> new UNSGAIII(problem);
      case EpsilonMOEA -> new EpsilonMOEA(problem);
      case EpsilonNSGAII -> new EpsilonNSGAII(problem);
      case IBEA -> new IBEA(problem);
    };
  }
}
