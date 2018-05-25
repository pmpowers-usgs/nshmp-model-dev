package gov.usgs.earthquake.nshm.cous2018;

import org.w3c.dom.Element;

import gov.usgs.earthquake.nshmp.internal.Parsing;

public class Util {
  
  static final String DISCLAIMER = " This model is an example and for review purposes only ";

  static void addDisclaimer(Element e) {
    Parsing.addComment(DISCLAIMER, e);
  }

}
