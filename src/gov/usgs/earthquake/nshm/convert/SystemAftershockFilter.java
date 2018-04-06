package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;

import gov.usgs.earthquake.function.EvenlyDiscretizedFunc;

import gov.usgs.earthquake.mfd.GutenbergRichterMfd;

/*
 * Adapted from UCERF3 GardnerKnopoffAftershockFilter to scale rates of gridded
 * sources (Gardiner-Knopoff style) or fault sources (UCERF2) style.
 */
class SystemAftershockFilter extends EvenlyDiscretizedFunc {

  // fraction of events that are mainshocks >= M 5
  // from Table 21 of Felzer's UCERF2 Appendix I
  // (http://pubs.usgs.gov/of/2007/1437/i/of2007-1437i.pdf)
  private static final double FRACT_MAINSH_GTM5 = 4.17 / 7.5; // = 0.556

  // aftershock rate reduction from UCERF2 used for UCERF3
  // supra sesmogenic ruptures
  private static final double SUPRA_SEIS_SCALE = 0.97;

  private static SystemAftershockFilter instance;

  GutenbergRichterMfd allGR;
  GutenbergRichterMfd mainGR;

  static {
    instance = new SystemAftershockFilter(0.05, 100, 0.1);
  }

  static double scaleGridRate(double m, double rate) {
    checkArgument(m > instance.getMinX() && m < instance.getMaxX());
    double scale = instance.getClosestY(m);
    return rate * scale;
  }

  static double scaleFaultRate(double m, double rate) {
    return SUPRA_SEIS_SCALE * rate;
  }

  SystemAftershockFilter(double min, int num, double delta) {
    super(min, num, delta);

    allGR = new GutenbergRichterMfd(min, num, delta);
    mainGR = new GutenbergRichterMfd(min, num, delta);

    allGR.setAllButTotCumRate(allGR.getMinX(), allGR.getMaxX(), 1.0, 1.0);
    mainGR.setAllButTotCumRate(allGR.getMinX(), allGR.getMaxX(), 1.0, 0.8);
    int mag5index = allGR.getClosestXIndex(5.0 + allGR.getDelta() / 2);
    allGR.scaleToCumRate(mag5index, 1.0);
    mainGR.scaleToCumRate(mag5index, FRACT_MAINSH_GTM5);

    for (int i = 0; i < num; i++) {
      double fract = mainGR.getY(i) / allGR.getY(i);
      if (fract <= 1)
        set(i, fract);
      else
        set(i, 1.0);
    }
  }

}
