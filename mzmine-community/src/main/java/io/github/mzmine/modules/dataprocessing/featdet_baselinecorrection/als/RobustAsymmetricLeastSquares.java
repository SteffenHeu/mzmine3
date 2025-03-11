package io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.als;

import java.util.Arrays;

/**
 * Robust implementation of Asymmetric Least Squares baseline correction
 * Based on Eilers & Boelens algorithm with optimizations for large data sets
 */
public class RobustAsymmetricLeastSquares {

  /**
   * Performs baseline correction using a direct implementation optimized for speed
   *
   * @param y Input signal data
   * @param lambda Smoothing parameter (10^2 to 10^7)
   * @param p Asymmetry parameter (0.001 to 0.1)
   * @param maxIterations Maximum number of iterations
   * @return The estimated baseline
   */
  public static double[] correctBaseline(double[] y, double lambda, double p, int maxIterations) {
    int n = y.length;

    // 1. Data normalization - critical for proper baseline detection
    double min = Double.MAX_VALUE;
    double max = Double.MIN_VALUE;
    for (double val : y) {
      min = Math.min(min, val);
      max = Math.max(max, val);
    }

    // Working copy of data - normalized
    double[] signal = new double[n];
    for (int i = 0; i < n; i++) {
      signal[i] = y[i] - min;
    }

    // 2. Initialize weights and baseline estimate
    double[] w = new double[n];
    Arrays.fill(w, 0.5); // neutral starting point

    // Initial baseline estimate (moving average of bottom 20%)
    double[] z = estimateInitialBaseline(signal);

    // 3. Pre-compute fixed matrices for the second derivative operator
    // Coefficients represent the central part of D'D matrix
    double[] diag = new double[n];    // Main diagonal values for regularization
    double[] ldiag1 = new double[n-1]; // Lower first diagonal
    double[] udiag1 = new double[n-1]; // Upper first diagonal
    double[] ldiag2 = new double[n-2]; // Lower second diagonal
    double[] udiag2 = new double[n-2]; // Upper second diagonal

    // Fill diagonal with standard values: D'D has 6 on main diagonal
    Arrays.fill(diag, 6.0 * lambda);
    Arrays.fill(ldiag1, -4.0 * lambda);
    Arrays.fill(udiag1, -4.0 * lambda);
    Arrays.fill(ldiag2, lambda);
    Arrays.fill(udiag2, lambda);

    // Adjust boundary conditions
    diag[0] = diag[n-1] = lambda;
    diag[1] = diag[n-2] = 5.0 * lambda;

    if (n > 2) {
      ldiag1[0] = udiag1[n-2] = -2.0 * lambda;
    }

    // 4. Main iterative improvement loop
    double[] alpha = new double[n];   // Modified diagonal during iterations
    double[] beta = new double[n];    // Right-hand side during iterations

    for (int iter = 0; iter < maxIterations; iter++) {
      // Create working copies of matrices for this iteration
      double[] a = Arrays.copyOf(diag, n);
      double[] b = Arrays.copyOf(udiag1, n-1);
      double[] c = Arrays.copyOf(ldiag1, n-1);
      double[] d = Arrays.copyOf(udiag2, n-2);
      double[] e = Arrays.copyOf(ldiag2, n-2);

      // Update weight matrix based on previous iteration
      for (int i = 0; i < n; i++) {
        w[i] = signal[i] > z[i] ? p : 1.0 - p;
        a[i] += w[i];             // Add weights to diagonal
        beta[i] = w[i] * signal[i]; // Right-hand side
      }

      // Solve the pentadiagonal system (W + λD'D)z = Wy
      double[] newZ = solvePentadiagonal(a, b, c, d, e, beta, n);

      // Calculate change from previous iteration
      double maxChange = 0.0;
      for (int i = 0; i < n; i++) {
        maxChange = Math.max(maxChange, Math.abs(z[i] - newZ[i]));
      }

      // Copy new values
      System.arraycopy(newZ, 0, z, 0, n);

      // Check convergence
      if (maxChange < 1e-6 * max) {
        break;
      }
    }

    // 5. Re-add the minimum value to return to original scale
    double[] baseline = new double[n];
    for (int i = 0; i < n; i++) {
      baseline[i] = z[i] + min;
    }

    return baseline;
  }

  /**
   * Creates an initial baseline estimate using a moving minimum filter
   */
  private static double[] estimateInitialBaseline(double[] signal) {
    int n = signal.length;
    int windowSize = Math.max(n / 20, 5); // 5% of data or at least 5 points

    double[] z = new double[n];

    // Moving minimum with larger window
    for (int i = 0; i < n; i++) {
      int start = Math.max(0, i - windowSize/2);
      int end = Math.min(n-1, i + windowSize/2);

      // Find minimum in window
      double minVal = Double.MAX_VALUE;
      for (int j = start; j <= end; j++) {
        minVal = Math.min(minVal, signal[j]);
      }

      // Find 20th percentile value in window
      int count = end - start + 1;
      double[] windowValues = new double[count];
      int idx = 0;
      for (int j = start; j <= end; j++) {
        windowValues[idx++] = signal[j];
      }
      Arrays.sort(windowValues);
      int percentileIdx = (int)(count * 0.2);
      double percentileVal = windowValues[percentileIdx];

      z[i] = percentileVal;
    }

    return z;
  }

  /**
   * Solves a pentadiagonal system using an optimized algorithm
   * Based on the generalized Thomas algorithm
   */
  private static double[] solvePentadiagonal(double[] a, double[] b, double[] c,
      double[] d, double[] e, double[] f, int n) {
    if (n <= 5) {
      // For small systems, use direct solution
      return solveTridiagonal(a, b, c, f);
    }

    // Temporary arrays for the forward sweep
    double[] alpha = new double[n];
    double[] gamma = new double[n];
    double[] delta = new double[n];
    double[] beta = new double[n];

    // Forward elimination
    alpha[0] = a[0];
    gamma[0] = b[0] / alpha[0];
    delta[0] = d[0] / alpha[0];
    beta[0] = f[0] / alpha[0];

    alpha[1] = a[1] - c[0] * gamma[0];
    gamma[1] = (b[1] - c[0] * delta[0]) / alpha[1];
    delta[1] = d[1] / alpha[1];
    beta[1] = (f[1] - c[0] * beta[0]) / alpha[1];

    for (int i = 2; i < n-2; i++) {
      alpha[i] = a[i] - c[i-1] * gamma[i-1] - e[i-2] * delta[i-2];
      gamma[i] = (b[i] - c[i-1] * delta[i-1]) / alpha[i];
      delta[i] = d[i] / alpha[i];
      beta[i] = (f[i] - c[i-1] * beta[i-1] - e[i-2] * beta[i-2]) / alpha[i];
    }

    if (n > 3) {
      alpha[n-2] = a[n-2] - c[n-3] * gamma[n-3] - e[n-4] * delta[n-4];
      gamma[n-2] = (b[n-2] - c[n-3] * delta[n-3]) / alpha[n-2];
      beta[n-2] = (f[n-2] - c[n-3] * beta[n-3] - e[n-4] * beta[n-4]) / alpha[n-2];
    }

    if (n > 2) {
      alpha[n-1] = a[n-1] - c[n-2] * gamma[n-2] - e[n-3] * delta[n-3];
      beta[n-1] = (f[n-1] - c[n-2] * beta[n-2] - e[n-3] * beta[n-3]) / alpha[n-1];
    }

    // Back substitution
    double[] x = new double[n];
    x[n-1] = beta[n-1];

    if (n > 1) {
      x[n-2] = beta[n-2] - gamma[n-2] * x[n-1];
    }

    for (int i = n-3; i >= 0; i--) {
      x[i] = beta[i] - gamma[i] * x[i+1] - delta[i] * x[i+2];
    }

    return x;
  }

  /**
   * Solves a tridiagonal system using the Thomas algorithm
   * Much faster for large systems
   */
  private static double[] solveTridiagonal(double[] a, double[] b, double[] c, double[] d) {
    int n = a.length;
    double[] x = new double[n];

    // Forward sweep - modified Thomas algorithm
    double[] cp = new double[n-1];
    double[] dp = new double[n];

    cp[0] = b[0] / a[0];
    dp[0] = d[0] / a[0];

    for (int i = 1; i < n-1; i++) {
      double m = a[i] - c[i-1] * cp[i-1];
      cp[i] = b[i] / m;
      dp[i] = (d[i] - c[i-1] * dp[i-1]) / m;
    }

    dp[n-1] = (d[n-1] - c[n-2] * dp[n-2]) / (a[n-1] - c[n-2] * cp[n-2]);

    // Back substitution
    x[n-1] = dp[n-1];
    for (int i = n-2; i >= 0; i--) {
      x[i] = dp[i] - cp[i] * x[i+1];
    }

    return x;
  }

  /**
   * Example usage
   */
  public static void main(String[] args) {
    // Generate test data with baseline and peaks
    int n = 10000;
    double[] data = new double[n];

    // Create a complex baseline with drift and humps
    for (int i = 0; i < n; i++) {
      double x = (double)i / n;

      // Baseline components
      double linearDrift = 5.0 * x;
      double sineDrift = 2.0 * Math.sin(2.0 * Math.PI * x);
      double expHump = 3.0 * Math.exp(-(Math.pow(x-0.7, 2))/0.01);

      // Actual signal peaks (not part of baseline)
      double peak1 = 10.0 * Math.exp(-(Math.pow(x-0.2, 2))/0.001);
      double peak2 = 8.0 * Math.exp(-(Math.pow(x-0.5, 2))/0.002);
      double peak3 = 12.0 * Math.exp(-(Math.pow(x-0.8, 2))/0.0005);

      // Combine everything
      data[i] = linearDrift + sineDrift + expHump + peak1 + peak2 + peak3;
    }

    // Measure execution time
    long startTime = System.nanoTime();

    // Baseline correction with proper parameters
    double[] baseline = correctBaseline(data, 1e6, 0.001, 15);

    long endTime = System.nanoTime();
    double elapsedMs = (endTime - startTime) / 1e6;

    System.out.printf("Processed %d points in %.2f ms (%.2f points/ms)%n",
        n, elapsedMs, n/elapsedMs);

    // Calculate the corrected signal
    double[] correctedSignal = new double[n];
    for (int i = 0; i < n; i++) {
      correctedSignal[i] = data[i] - baseline[i];
    }

    // Print some statistics for verification
    double baselineSum = 0;
    for (double v : baseline) baselineSum += v;
    double baselineAvg = baselineSum / n;

    System.out.printf("Baseline statistics: Avg=%.2f, Min=%.2f, Max=%.2f%n",
        baselineAvg, Arrays.stream(baseline).min().getAsDouble(),
        Arrays.stream(baseline).max().getAsDouble());
  }
}
