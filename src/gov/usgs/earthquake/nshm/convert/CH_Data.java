package gov.usgs.earthquake.nshm.convert;

import static gov.usgs.earthquake.nshmp.internal.Parsing.addAttribute;
import static gov.usgs.earthquake.nshmp.internal.Parsing.addElement;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.FLOATS;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.M;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.RATE;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.TYPE;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.WEIGHT;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.INCREMENTAL_MFD;
import static gov.usgs.earthquake.nshmp.mfd.MfdType.SINGLE;

import gov.usgs.earthquake.nshmp.data.Data;
import org.w3c.dom.Element;

/*
 * Wrapper for characteristic (single) MFD data.
 * 
 * @author Peter Powers
 */
public class CH_Data implements MFD_Data {

  double mag;
  double rate;
  double weight;
  boolean floats;

  private CH_Data(double mag, double rate, double weight, boolean floats) {
    this.mag = mag;
    this.rate = rate;
    this.weight = weight;
    this.floats = floats;
  }

  public static CH_Data create(double mag, double rate, double weight, boolean floats) {
    return new CH_Data(mag, rate, weight, floats);
  }

  @Override
  public Element appendTo(Element parent, MFD_Data ref) {
    Element e = addElement(INCREMENTAL_MFD, parent);
    // always include type
    addAttribute(TYPE, SINGLE, e);
    if (ref != null) {
      CH_Data refCH = (CH_Data) ref;
      if (rate != refCH.rate) addAttribute(RATE, rate, "%.8g", e);
      if (mag != refCH.mag) addAttribute(M, mag, "%.3f", e);
      if (floats != refCH.floats) addAttribute(FLOATS, floats, e);
      if (weight != refCH.weight) addAttribute(WEIGHT, weight, e);
    } else {
      addAttribute(RATE, rate, "%.8g", e);
      addAttribute(M, mag, "%.3f", e);
      addAttribute(FLOATS, floats, e);
      addAttribute(WEIGHT, weight, e);
    }
    return e;
  }

}
