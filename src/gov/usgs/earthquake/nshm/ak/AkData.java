package gov.usgs.earthquake.nshm.ak;

import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

class AkData {

  /*
   * Arrays of AK fault traces and maps of slip rates where keys are trace
   * indices (locations) between which slip rates are interpolated.
   * 
   * Denali - Totschunda fault traces are all west to east in direction of
   * bifurcation. Castle Mtn. has been reveresed (now east to west) from
   * original fortran input to properly define its north dip according to the
   * right-hand-rule, instead of using a negative dip value.
   */

  static final RuptureScaling AK_SCALING = RuptureScaling.NSHM_FAULT_WC94_LENGTH;

  static final double AK_M_MIN = 6.5;
  static final double AK_B_VALUE = 0.87;
  static final double AK_M_DELTA = 0.1;
  static final double AK_GR_M_MIN = 6.5 + AK_M_DELTA / 2.0;
  static final double AK_GR_WEIGHT = 0.5;
  static final double AK_CH_WEIGHT = 0.5;
  static final double AK_ASEIS = 0.0;
  static final double AK_DEPTH = 0.0;
  static final double AK_RAKE = 0.0;

  static final double DENALI_CH_M = 7.9;
  static final double DENALI_GR_M_MAX = DENALI_CH_M - AK_M_DELTA / 2.0;
  static final double DENALI_DIP = 90.0;
  static final double DENALI_WIDTH = 15.0;
  static final double DENALI_LOWER_DEPTH = 15.0;

  static final class Uncertainty {
    final double[] epiDeltas = { -0.2, 0.0, 0.2 };
    final double[] epiWeights = { 0.2, 0.6, 0.2 };
    final double epiCutoff = AK_M_MIN;
    final int epiCount = epiDeltas.length;
    final double aleaSigma = 0.12;
    final double aleaCutoff = AK_M_MIN;
    final int aleaCount = 11;
  }

  static final Uncertainty AK_M_UNCERTAINTY = new Uncertainty();

  /* Central Denali */
  static final double[][] DENALI_CENTER_COORDS = {
      { 62.220, -154.740 }, // 0.6 0.4
      { 62.420, -154.000 },
      { 62.500, -153.500 },
      { 62.680, -152.880 },
      { 62.770, -152.310 },
      { 62.950, -151.610 },
      { 63.200, -150.900 },
      { 63.302, -150.222 }, // 5.5 3.9
      { 63.332, -149.958 },
      { 63.370, -149.688 },
      { 63.402, -149.327 },
      { 63.430, -149.077 },
      { 63.445, -148.917 },
      { 63.467, -148.664 },
      { 63.468, -148.462 },
      { 63.484, -148.167 },
      { 63.488, -147.993 },
      { 63.497, -147.797 },
      { 63.512, -147.605 },
      { 63.523, -147.372 },
      { 63.526, -147.208 },
      { 63.527, -147.079 },
      { 63.524, -146.974 },
      { 63.512, -146.819 },
      { 63.498, -146.694 },
      { 63.494, -146.546 },
      { 63.484, -146.417 },
      { 63.477, -146.228 },
      { 63.454, -146.096 },
      { 63.426, -145.924 },
      { 63.392, -145.756 },
      { 63.357, -145.640 },
      { 63.325, -145.511 },
      { 63.300, -145.346 },
      { 63.268, -145.162 },
      { 63.245, -145.005 },
      { 63.211, -144.851 },
      { 63.187, -144.698 },
      { 63.134, -144.508 },
      { 63.103, -144.363 },
      { 63.070, -144.218 },
      { 63.029, -144.079 },
      { 62.997, -143.936 },
      { 62.956, -143.792 },
      { 62.918, -143.624 },
      { 62.883, -143.490 },
      { 62.857, -143.417 } // 8.4 6.0
  };

  static final LocationList DENALI_CENTER_TRACE = traceToLocList(DENALI_CENTER_COORDS);
  static final double DENALI_CENTER_DIP = DENALI_DIP;
  static final double DENALI_CENTER_WIDTH = DENALI_WIDTH;
  static final double DENALI_CENTER_LOWER_DEPTH = DENALI_LOWER_DEPTH;

  static final double DENALI_CENTER_EAST_CH_M_MAX = DENALI_CH_M;
  static final double DENALI_CENTER_EAST_GR_M_MAX = DENALI_GR_M_MAX;
  static final Map<Integer, Double> DENALI_CENTER_EAST_SLIP = ImmutableMap.of(
      0, 0.6,
      7, 5.5,
      46, 8.4);

  static final double DENALI_CENTER_TOTSCHUNDA_CH_M_MAX = DENALI_CH_M;
  static final double DENALI_CENTER_TOTSCHUNDA_GR_M_MAX = DENALI_GR_M_MAX;
  static final Map<Integer, Double> DENALI_CENTER_TOTSCHUNDA_SLIP = ImmutableMap.of(
      0, 0.4,
      7, 3.9,
      46, 6.0);

  static final double[][] DENALI_EAST_COORDS = {
      { 62.857, -143.417 }, // 8.4
      { 62.810, -143.265 },
      { 62.765, -143.128 },
      { 62.715, -142.966 },
      { 62.650, -142.737 },
      { 62.621, -142.636 },
      { 62.584, -142.500 },
      { 62.501, -142.262 },
      { 62.465, -142.152 },
      { 62.426, -142.000 },
      { 62.358, -141.825 },
      { 62.320, -141.692 },
      { 62.299, -141.540 },
      { 62.259, -141.417 },
      { 62.222, -141.279 },
      { 62.193, -141.181 },
      { 62.159, -141.025 },
      { 62.100, -140.873 },
      { 62.042, -140.687 },
      { 61.971, -140.517 },
      { 61.927, -140.401 },
      { 61.851, -140.232 },
      { 61.811, -140.135 },
      { 61.773, -140.056 },
      { 61.733, -139.960 },
      { 61.683, -139.857 },
      { 61.627, -139.708 },
      { 61.572, -139.563 },
      { 61.492, -139.397 },
      { 61.408, -139.231 },
      { 61.355, -139.102 },
      { 61.315, -139.022 },
      { 61.276, -138.912 },
      { 61.210, -138.800 },
      { 61.155, -138.674 },
      { 61.080, -138.518 },
      { 61.008, -138.385 },
      { 60.953, -138.259 },
      { 60.871, -138.082 },
      { 60.797, -137.982 },
      { 60.731, -137.886 },
      { 60.653, -137.805 }, // 2.0
      { 60.564, -137.721 },
      { 60.497, -137.619 },
      { 60.446, -137.501 },
      { 60.368, -137.365 },
      { 60.289, -137.250 },
      { 60.208, -137.128 },
      { 60.131, -137.047 },
      { 60.023, -136.910 },
      { 59.933, -136.786 },
      { 59.855, -136.665 },
      { 59.779, -136.522 },
      { 59.715, -136.400 },
      { 59.646, -136.291 },
      { 59.544, -136.126 },
      { 59.456, -135.988 },
      { 59.404, -135.906 },
      { 59.323, -135.781 },
      { 59.265, -135.673 },
      { 59.196, -135.490 },
      { 59.168, -135.440 },
      { 59.141, -135.399 },
      { 59.124, -135.378 },
      { 59.052, -135.305 },
      { 59.030, -135.294 },
      { 58.967, -135.267 },
      { 58.916, -135.247 },
      { 58.873, -135.222 },
      { 58.829, -135.198 },
      { 58.786, -135.188 },
      { 58.744, -135.166 },
      { 58.696, -135.151 },
      { 58.642, -135.133 },
      { 58.578, -135.098 },
      { 58.526, -135.079 },
      { 58.475, -135.053 },
      { 58.421, -135.036 },
      { 58.388, -135.034 },
      { 58.333, -135.011 },
      { 58.294, -134.996 },
      { 58.248, -134.984 },
      { 58.205, -134.976 },
      { 58.152, -134.964 },
      { 58.117, -134.942 },
      { 58.070, -134.920 },
      { 58.032, -134.905 },
      { 57.932, -134.860 },
      { 57.892, -134.853 },
      { 57.842, -134.853 },
      { 57.809, -134.842 },
      { 57.758, -134.839 },
      { 57.713, -134.830 },
      { 57.662, -134.816 },
      { 57.634, -134.809 },
      { 57.575, -134.790 },
      { 57.537, -134.781 },
      { 57.483, -134.772 },
      { 57.440, -134.752 },
      { 57.401, -134.739 },
      { 57.353, -134.730 },
      { 57.304, -134.730 },
      { 57.249, -134.731 },
      { 57.195, -134.735 },
      { 57.122, -134.716 },
      { 57.078, -134.711 },
      { 57.038, -134.702 },
      { 56.992, -134.697 },
      { 56.946, -134.682 },
      { 56.897, -134.666 },
      { 56.853, -134.650 },
      { 56.817, -134.632 },
      { 56.758, -134.627 },
      { 56.722, -134.613 },
      { 56.671, -134.590 },
      { 56.629, -134.575 },
      { 56.583, -134.562 },
      { 56.550, -134.554 },
      { 56.503, -134.551 },
      { 56.452, -134.537 },
      { 56.398, -134.541 },
      { 56.364, -134.554 },
      { 56.306, -134.546 },
      { 56.271, -134.547 },
      { 56.212, -134.541 },
      { 56.117, -134.543 } // 2.0
  };

  static final LocationList DENALI_EAST_TRACE = traceToLocList(DENALI_EAST_COORDS);
  static final double DENALI_EAST_DIP = DENALI_DIP;
  static final double DENALI_EAST_WIDTH = DENALI_WIDTH;
  static final double DENALI_EAST_LOWER_DEPTH = DENALI_LOWER_DEPTH;

  static final Map<Integer, Double> DENALI_EAST_SLIP = ImmutableMap.of(
      0, 8.4,
      41, 2.0,
      125, 2.0);

  static final double[][] TOTSCHUNDA_COORDS = {
      { 62.857, -143.417 }, // 6.0
      { 62.778, -143.326 },
      { 62.741, -143.280 },
      { 62.703, -143.213 },
      { 62.646, -143.104 },
      { 62.621, -143.037 },
      { 62.574, -142.951 },
      { 62.532, -142.872 },
      { 62.498, -142.795 },
      { 62.473, -142.766 },
      { 62.439, -142.715 },
      { 62.403, -142.675 },
      { 62.351, -142.628 },
      { 62.286, -142.543 },
      { 62.244, -142.494 },
      { 62.203, -142.408 },
      { 62.161, -142.361 },
      { 62.104, -142.301 },
      { 62.040, -142.224 },
      { 61.982, -142.137 },
      { 61.917, -142.042 },
      { 61.846, -141.944 },
      { 61.811, -141.889 },
      { 61.774, -141.834 },
      { 61.743, -141.755 },
      { 61.711, -141.706 },
      { 61.680, -141.667 },
      { 61.653, -141.607 },
      { 61.633, -141.562 },
      { 61.596, -141.485 },
      { 61.555, -141.404 },
      { 61.526, -141.335 },
      { 61.503, -141.268 },
      { 61.483, -141.195 },
      { 61.475, -141.136 },
      { 61.473, -141.025 },
      { 61.463, -140.997 } // 6.0
  };

  static final LocationList TOTSCHUNDA_TRACE = traceToLocList(TOTSCHUNDA_COORDS);
  static final double TOTSCHUNDA_DIP = DENALI_DIP;
  static final double TOTSCHUNDA_WIDTH = DENALI_WIDTH;
  static final double TOTSCHUNDA_LOWER_DEPTH = DENALI_LOWER_DEPTH;

  static final Map<Integer, Double> TOTSCHUNDA_SLIP = ImmutableMap.of(
      0, 6.0,
      36, 6.0);

  static final double[][] CASTLE_MTN_COORDS = {
      { 61.815, -147.726 }, // 0.5
      { 61.820, -147.843 },
      { 61.829, -147.911 },
      { 61.833, -147.989 },
      { 61.845, -148.076 },
      { 61.854, -148.155 },
      { 61.868, -148.260 },
      { 61.878, -148.360 },
      { 61.869, -148.440 },
      { 61.843, -148.562 },
      { 61.824, -148.689 },
      { 61.792, -148.832 },
      { 61.765, -148.982 },
      { 61.728, -149.113 },
      { 61.706, -149.250 },
      { 61.682, -149.390 },
      { 61.655, -149.553 }, // 2.9
      { 61.634, -149.678 },
      { 61.619, -149.777 },
      { 61.602, -149.896 },
      { 61.585, -150.000 },
      { 61.569, -150.089 },
      { 61.552, -150.191 },
      { 61.520, -150.310 },
      { 61.496, -150.391 },
      { 61.457, -150.523 },
      { 61.435, -150.624 }, // 2.9
      { 61.412, -150.707 },
      { 61.381, -150.838 },
      { 61.362, -150.974 },
      { 61.330, -151.040 } // 0.5
  };

  static final LocationList CASTLE_MTN_TRACE = traceToLocList(CASTLE_MTN_COORDS);
  static final double CASTLE_MTN_DIP = 75.0;
  static final double CASTLE_MTN_WIDTH = 15.529;
  static final double CASTLE_MTN_LOWER_DEPTH = 15.0;

  static final double CASTLE_MTN_CH_M = 7.1;
  static final double CASTLE_MTN_GR_M_MAX = CASTLE_MTN_CH_M - AK_M_DELTA / 2.0;
  static final Map<Integer, Double> CASTLE_MTN_SLIP = ImmutableMap.of(
      0, 0.5,
      16, 2.9,
      26, 2.9,
      30, 0.5);

  static LocationList traceToLocList(double[][] data) {
    return LocationList.builder()
        .addAll(FluentIterable.from(data)
            .transform(new Function<double[], Location>() {
              @Override
              public Location apply(double[] coord) {
                return Location.create(coord[0], coord[1]);
              }
            }))
        .build();
  }

}
