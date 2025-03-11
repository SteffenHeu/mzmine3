package io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.als;

import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.AnyXYProvider;
import io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.AbstractBaselineCorrectorParameters;
import io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.AbstractResolverBaselineCorrector;
import io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.BaselineCorrectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.BaselineCorrector;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolver;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.util.MemoryMapStorage;
import java.awt.Color;
import java.time.Instant;
import java.util.Arrays;
import java.util.logging.Logger;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ALSBaselineCorrector extends AbstractResolverBaselineCorrector {

  private static final Logger logger = Logger.getLogger(ALSBaselineCorrector.class.getName());

  private final double lambda;
  private final double p;
  private final int iterations;

  public ALSBaselineCorrector() {
    super(null, 5, "als", null);
    lambda = 1E5;
    p = 0.01;
    iterations = 1;
  }

  public ALSBaselineCorrector(@Nullable MemoryMapStorage storage, int numSamples,
      @NotNull String suffix, @Nullable MinimumSearchFeatureResolver resolver, ParameterSet parameters) {
    super(storage, numSamples, suffix, resolver);
    lambda = parameters.getValue(ALSBaselineCorrectorParameters.lambda);
    p = parameters.getValue(ALSBaselineCorrectorParameters.asymmetry);
    iterations = parameters.getValue(ALSBaselineCorrectorParameters.iterations);
  }

  /**
   * Creates a second difference matrix which approximates the second derivative
   *
   * @param size Size of the input vector
   * @return Second difference matrix
   */
  private static RealMatrix createSecondDifferenceMatrix(int size) {
    // Create a matrix for the second difference operator
    double[][] diffMatrix = new double[size - 2][size];

    for (int i = 0; i < size - 2; i++) {
      diffMatrix[i][i] = 1;
      diffMatrix[i][i + 1] = -2;
      diffMatrix[i][i + 2] = 1;
    }

    RealMatrix D = MatrixUtils.createRealMatrix(diffMatrix);
    return D;
  }

  /**
   * Solves a tridiagonal system using the Thomas algorithm
   *
   * @param diag Diagonal elements
   * @param upper Upper diagonal elements
   * @param lower Lower diagonal elements
   * @param rhs Right-hand side vector
   * @return Solution vector
   */
  private static double[] solveTridiagonal(double[] diag, double[] upper, double[] lower, double[] rhs) {
    int n = diag.length;
    double[] solution = new double[n];

    // Forward sweep
    double[] temp = new double[n];
    double[] tempRhs = new double[n];

    temp[0] = upper[0] / diag[0];
    tempRhs[0] = rhs[0] / diag[0];

    for (int i = 1; i < n; i++) {
      double m = diag[i] - lower[i-1] * temp[i-1];
      if (i < n-1) {
        temp[i] = upper[i] / m;
      }
      tempRhs[i] = (rhs[i] - lower[i-1] * tempRhs[i-1]) / m;
    }

    // Back substitution
    solution[n-1] = tempRhs[n-1];
    for (int i = n-2; i >= 0; i--) {
      solution[i] = tempRhs[i] - temp[i] * solution[i+1];
    }

    return solution;
  }

  public static double[][] baselineALSEfficientGPT(double[] y, double lambda, double p, int niter) {
    int n = y.length;
    double[] z = new double[n];
    double[] w = new double[n];

    // Initialize weights and baseline z to zeros
    for (int i = 0; i < n; i++) {
      w[i] = 1.0;
      z[i] = 0.0; // Start with zero as baseline
    }

    // Store diagonal elements and upper/lower bands for tridiagonal system
    double[] diag = new double[n];
    double[] upper = new double[n - 1];
    double[] lower = new double[n - 1];

    // Iteratively refine the baseline
    for (int iter = 0; iter < niter; iter++) {
      // Create the tridiagonal system
      // The system is (W + λD'D)z = Wy
      // D'D is approximated with specific patterns fitting the problem size and characteristics
      for (int i = 0; i < n; i++) {
        diag[i] = w[i] + 2 * lambda; // Adjust for regularization (6 is typical depending on second derivative approx)

        // Boundary adjustments
        if (i == 0 || i == n - 1) {
          diag[i] -= 2 * lambda;
        }
      }

      // Upper and lower diagonals (-lambda values)
      for (int i = 0; i < n - 1; i++) {
        upper[i] = -lambda;
        lower[i] = -lambda;

        // Adjust boundary region weights if necessary
        if (i == 0 || i == n - 2) {
          upper[i] += lambda;
          lower[i] += lambda;
        }
      }

      // Create right-hand side: Wy
      double[] rhs = new double[n];
      for (int i = 0; i < n; i++) {
        rhs[i] = w[i] * y[i]; // Wy computation
      }

      // Solve the tridiagonal system to update z
      z = solveTridiagonal(diag, upper, lower, rhs);

      // Update weights using asymmetric weighting
      for (int i = 0; i < n; i++) {
        if (y[i] > z[i]) {
          w[i] = p; // Below baseline treatment
        } else {
          w[i] = 1 - p; // Above baseline treatment
        }
      }
    }

    // Calculate corrected signal
    double[] correctedY = new double[n];
    for (int i = 0; i < n; i++) {
      correctedY[i] = y[i] - z[i]; // Original minus baseline
    }

    return new double[][] { z, correctedY };
  }

  @Override
  protected void subSampleAndCorrect(double[] xDataToCorrect, double[] yDataToCorrect,
      int numValues, double[] xDataFiltered, double[] yDataFiltered, int numValuesFiltered,
      boolean addPreview) {

    final long start = System.nanoTime();
    final double[][] baselinePenta = baselineALSEfficientPentadiagonal(yDataToCorrect, lambda, p, iterations);
    final long endPenta = System.nanoTime();
    final double[] fastBaselineALS = fastBaselineALS(yDataToCorrect, lambda, p, iterations);
    final long endFast = System.nanoTime();
    final double[] robust = RobustAsymmetricLeastSquares.correctBaseline(yDataToCorrect, lambda, p,
        iterations);
    final long endRobust = System.nanoTime();
    logger.info(() -> "Pentadiagonal correction took " + (endPenta - start) + " ns");
    logger.info(() -> "Fast tridiagonal correction took " + (endFast - endPenta) + " ns");
    logger.info(() -> "Robust correction took " + (endRobust - endFast) + " ns");
    System.arraycopy(baselinePenta[1], 0, yDataToCorrect, 0, numValues);

    if(addPreview) {
//      additionalData.add(new AnyXYProvider(Color.RED, "baseline gpt", baselineGpt[0].length, j ->xDataToCorrect[j], j -> baselineGpt[0][j]));
//      additionalData.add(new AnyXYProvider(Color.green, "baseline default", baseline[0].length, j ->xDataToCorrect[j], j -> baseline[0][j]));
      additionalData.add(new AnyXYProvider(Color.BLUE, "baseline penta", baselinePenta[0].length, j ->xDataToCorrect[j], j -> baselinePenta[0][j]));
      additionalData.add(new AnyXYProvider(Color.RED, "baseline fast", fastBaselineALS.length, j ->xDataToCorrect[j], j -> fastBaselineALS[j]));
      additionalData.add(new AnyXYProvider(Color.GREEN, "Robust", robust.length, j ->xDataToCorrect[j], j -> robust[j]));
    }
  }

  @Override
  public BaselineCorrector newInstance(ParameterSet parameters, MemoryMapStorage storage,
      FeatureList flist) {
    final ParameterSet embedded = parameters.getParameter(
        BaselineCorrectionParameters.correctionAlgorithm).getEmbeddedParameters();
    final MinimumSearchFeatureResolver resolver =
        embedded.getValue(AbstractBaselineCorrectorParameters.applyPeakRemoval)
            ? initializeLocalMinResolver((ModularFeatureList) flist) : null;

    return new ALSBaselineCorrector(storage, 5, "als",resolver, embedded);
  }

  @Override
  public @NotNull String getName() {
    return "Asymmetric least squares (ALS)";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return ALSBaselineCorrectorParameters.class;
  }

  /**
   * An efficient implementation for large datasets that avoids large matrix operations
   *
   * @param y Input signal
   * @param lambda Smoothness parameter
   * @param p Asymmetry parameter
   * @param niter Number of iterations
   * @return double[][] Array containing [baseline, correctedY]
   */
  public static double[][] baselineALSEfficientPentadiagonal(double[] y, double lambda, double p, int niter) {
    int n = y.length;

    // Initial weights - all ones
    double[] w = new double[n];
    for (int i = 0; i < n; i++) {
      w[i] = 1.0;
    }

    // Initialize z as the average of y to start
    double[] z = new double[n];
    double sum = 0;
    for (int i = 0; i < n; i++) {
      sum += y[i];
    }
    double avg = sum / n;
    for (int i = 0; i < n; i++) {
      z[i] = avg;
    }

    // Pre-compute patterns for D'D (the pentadiagonal second derivative matrix)
    double[] mainDiag = new double[n];
    double[] offDiag1 = new double[n-1];
    double[] offDiag2 = new double[n-2];

    // Fill the diagonals for D'D
    for (int i = 0; i < n; i++) {
      mainDiag[i] = 6.0;  // Main diagonal is 6

      // Adjustments for boundary conditions
      if (i == 0 || i == n-1) {
        mainDiag[i] = 1.0;
      } else if (i == 1 || i == n-2) {
        mainDiag[i] = 5.0;
      }
    }

    for (int i = 0; i < n-1; i++) {
      offDiag1[i] = -4.0;  // First off-diagonal is -4

      // Adjustments for boundary conditions
      if (i == 0 || i == n-2) {
        offDiag1[i] = -2.0;
      }
    }

    for (int i = 0; i < n-2; i++) {
      offDiag2[i] = 1.0;  // Second off-diagonal is 1
    }

    // Temporary arrays for solving the system
    double[] diagCopy = new double[n];
    double[] offDiag1Copy = new double[n-1];
    double[] offDiag2Copy = new double[n-2];
    double[] rhs = new double[n];

    // Iteratively refine the baseline
    for (int iter = 0; iter < niter; iter++) {
      // Copy the pattern matrices and scale by lambda
      for (int i = 0; i < n; i++) {
        diagCopy[i] = w[i] + lambda * mainDiag[i];
      }
      for (int i = 0; i < n-1; i++) {
        offDiag1Copy[i] = lambda * offDiag1[i];
      }
      for (int i = 0; i < n-2; i++) {
        offDiag2Copy[i] = lambda * offDiag2[i];
      }

      // Create right-hand side: Wy
      for (int i = 0; i < n; i++) {
        rhs[i] = w[i] * y[i];
      }

      // Solve the pentadiagonal system
      z = solvePentadiagonal(diagCopy, offDiag1Copy, offDiag1Copy,
          offDiag2Copy, offDiag2Copy, rhs);

      // Update weights using asymmetric weighting
      for (int i = 0; i < n; i++) {
        w[i] = (y[i] > z[i]) ? p : (1-p);
      }
    }

    // Calculate corrected signal
    double[] correctedY = new double[n];
    for (int i = 0; i < n; i++) {
      correctedY[i] = y[i] - z[i];
    }

    return new double[][] { z, correctedY };
  }

  /**
   * Solves a pentadiagonal system using LU decomposition
   * This is a simplified implementation for the specific case of ALS
   */
  private static double[] solvePentadiagonal(double[] mainDiag, double[] upperDiag1, double[] lowerDiag1,
      double[] upperDiag2, double[] lowerDiag2, double[] rhs) {
    int n = mainDiag.length;

    // Create the full matrix - inefficient but reliable
    Array2DRowRealMatrix A = new Array2DRowRealMatrix(n, n);

    // Set main diagonal
    for (int i = 0; i < n; i++) {
      A.setEntry(i, i, mainDiag[i]);
    }

    // Set first off-diagonals
    for (int i = 0; i < n-1; i++) {
      A.setEntry(i, i+1, upperDiag1[i]);
      A.setEntry(i+1, i, lowerDiag1[i]);
    }

    // Set second off-diagonals
    for (int i = 0; i < n-2; i++) {
      A.setEntry(i, i+2, upperDiag2[i]);
      A.setEntry(i+2, i, lowerDiag2[i]);
    }

    // Solve the system
    DecompositionSolver solver = new LUDecomposition(A).getSolver();
    RealVector solution = solver.solve(new ArrayRealVector(rhs));

    // Convert to array
    double[] result = new double[n];
    for (int i = 0; i < n; i++) {
      result[i] = solution.getEntry(i);
    }

    return result;
  }


  // ---------------------
  /**
   * Memory-efficient version for very large datasets
   * Uses iterative approach without storing large matrices
   */
  public static double[] fastBaselineALS(double[] y, double lambda, double p, int maxIterations) {
    int n = y.length;
    double[] z = Arrays.copyOf(y, n);
    double[] w = new double[n];
    Arrays.fill(w, 1.0);

    // Precompute tridiagonal coefficients for solving system
    double[] a = new double[n]; // diagonal
    double[] b = new double[n-1]; // upper diagonal
    double[] c = new double[n-1]; // lower diagonal

    for (int iter = 0; iter < maxIterations; iter++) {
      // Update tridiagonal system
      for (int i = 0; i < n; i++) {
        a[i] = w[i] + lambda * 6; // diagonal
        if (i < n-1) {
          b[i] = c[i] = -lambda * 4; // off-diagonals
        }
      }
      // Adjust boundary coefficients
      a[0] = w[0] + lambda * 2;
      a[1] = w[1] + lambda * 5;
      a[n-2] = w[n-2] + lambda * 5;
      a[n-1] = w[n-1] + lambda * 2;

      if (n > 2) {
        b[0] = -lambda * 2;
        c[n-2] = -lambda * 2;
      }

      // Prepare right-hand side
      double[] d = new double[n];
      for (int i = 0; i < n; i++) {
        d[i] = w[i] * y[i];
      }

      // Solve tridiagonal system
      double[] newZ = solveTridiagonal(a, b, c, d);

      // Update weights
      for (int i = 0; i < n; i++) {
        w[i] = y[i] > newZ[i] ? p : 1 - p;
      }

      // Check convergence
      if (maxAbsDifference(z, newZ) < 1e-6) {
        z = newZ;
        break;
      }

      z = newZ;
    }

    return z;
  }

  /**
   * Calculates the maximum absolute difference between two arrays
   */
  private static double maxAbsDifference(double[] a, double[] b) {
    double maxDiff = 0;
    for (int i = 0; i < a.length; i++) {
      maxDiff = Math.max(maxDiff, Math.abs(a[i] - b[i]));
    }
    return maxDiff;
  }
}
