package gov.usgs.earthquake.nshm.cous2018;

import org.w3c.dom.Element;

import com.google.common.math.DoubleMath;

import gov.usgs.earthquake.nshmp.internal.Parsing;

public class Util {
  
  static final String DISCLAIMER = " This model is an example and for review purposes only ";

  static void addDisclaimer(Element e) {
    Parsing.addComment(DISCLAIMER, e);
  }
  
  /*
   * This actually reproduces something closer to the originally supplied NSHMP
   * mag-depth-weight distribution, but it's not worth going back to the parser
   * to change it. Example outputs that can be parsed as
   * stringToValueValueWeightMap: [6.5::[5.0:1.0]; 10.0::[1.0:1.0]] standard two
   * depth [10.0::[50.0:1.0]] standard single depth
   */
  static String magDepthDataToString(double mag, double[] depths) {
    StringBuffer sb = new StringBuffer("[");
    if (DoubleMath.fuzzyEquals(depths[0], depths[1], 0.000001)) {
      sb.append("10.0::[").append(depths[0]);
      sb.append(":1.0]]");
    } else {
      sb.append(mag).append("::[");
      sb.append(depths[0]).append(":1.0]; 10.0::[");
      sb.append(depths[1]).append(":1.0]]");
    }
    return sb.toString();
  }



}
