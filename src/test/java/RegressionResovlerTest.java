import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.regressionresolver.RegressionResolver;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.regressionresolver.RegressionResolverParameters;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RegressionResovlerTest {

  double[] rt = {1.706128955, 1.710796595, 1.715800524, 1.833219051, 1.837790012, 1.842849851,
      1.847906351, 2.057122231, 2.062201023, 2.067248821, 2.072146177, 2.082173109, 2.087166071,
      2.092256784, 2.097306013, 2.171011209, 2.176383257, 2.181271315, 2.186257124, 2.19119978,
      2.19596982, 2.201463461, 2.206347704, 2.211220264, 2.216295004, 2.221358538, 2.225895882,
      2.23100543, 2.236040354, 2.24101305, 2.246031046, 2.251060486, 2.256129265, 2.261177063,
      2.266198158, 2.271241665, 2.27628541, 2.281356335, 2.28633213, 2.291386604, 2.296429396,
      2.306329012, 2.310981989, 2.31600666, 2.321041822, 2.325991392, 2.486229897, 2.491295338,
      2.496356726, 2.521630764, 2.526693106, 2.531746387, 2.64041996, 2.64543891, 2.6503613};

  double[] intensity = {0, 103.8516722, 0, 0, 249.0542629, 206.8649841, 0, 0, 263.4314462,
      212.0979117, 0, 0, 340.5154345, 282.0808083, 0, 0, 30196.08321, 173657.644, 402488.2797,
      592913.0907, 634280.1, 516036.3438, 337947.7434, 179464.287, 74406.8639, 25595.47452,
      10004.4593, 4359.936837, 2411.686077, 1661.07052, 1199.146225, 989.078583, 871.7839586,
      715.1473749, 604.4156555, 504.0933318, 473.237041, 421.4021818, 423.5427438, 266.4949614, 0,
      0, 102.8764197, 0, 159.169183, 0, 0, 118.6962154, 0, 0, 109.9559018, 0, 0, 110.6064096, 0};

  @Test
  void testRegressionResolver() {
    RegressionResolverParameters param = (RegressionResolverParameters) new RegressionResolverParameters().cloneParameterSet();

    param.setParameter(RegressionResolverParameters.minHeight, 1E3);
    param.setParameter(RegressionResolverParameters.minSlope, 0.1d);
    param.setParameter(RegressionResolverParameters.minNumPoints, 5);
    param.setParameter(RegressionResolverParameters.slopeCalcPoints, 4);
    param.setParameter(RegressionResolverParameters.topToEdgeRatio, 2d);
    param.getParameter(RegressionResolverParameters.chromatographicThreshold).getEmbeddedParameter()
        .setValue(0d);
    param.setParameter(RegressionResolverParameters.chromatographicThreshold, true);

    final ModularFeatureList flist = new ModularFeatureList("name", null, List.of());
    RegressionResolver r = new RegressionResolver(param, flist);

    r.resolve(rt, intensity);
  }
}
