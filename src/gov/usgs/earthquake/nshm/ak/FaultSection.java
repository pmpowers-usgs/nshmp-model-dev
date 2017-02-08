package gov.usgs.earthquake.nshm.ak;

import static org.opensha2.internal.Parsing.addAttribute;
import static org.opensha2.internal.Parsing.addElement;
import static org.opensha2.internal.SourceAttribute.ASEIS;
import static org.opensha2.internal.SourceAttribute.DEPTH;
import static org.opensha2.internal.SourceAttribute.DIP;
import static org.opensha2.internal.SourceAttribute.DIP_DIR;
import static org.opensha2.internal.SourceAttribute.INDEX;
import static org.opensha2.internal.SourceAttribute.LOWER_DEPTH;
import static org.opensha2.internal.SourceAttribute.NAME;
import static org.opensha2.internal.SourceElement.GEOMETRY;
import static org.opensha2.internal.SourceElement.SECTION;
import static org.opensha2.internal.SourceElement.TRACE;

import org.opensha2.geo.LocationList;

import org.w3c.dom.Element;

class FaultSection {
  
  int index;
  String name;
  LocationList trace;
  double aseis;
  double depth;
  double lowerDepth;
  double width;
  double dip;
  double dipDir;
  double slipRate;
  
  Element appendTo(Element parent) {
    Element sectionElement = addElement(SECTION, parent);
    addAttribute(NAME, name, sectionElement);
    addAttribute(INDEX, index, sectionElement);
    Element geomElement = addElement(GEOMETRY, sectionElement);
    addAttribute(DIP, dip, "%.1f", geomElement);
    addAttribute(DIP_DIR, dipDir, "%.3f", geomElement);
    addAttribute(DEPTH, depth, "%.5f", geomElement);
    addAttribute(LOWER_DEPTH, lowerDepth, "%.5f", geomElement);
    addAttribute(ASEIS, aseis, "%.4f", geomElement);
    Element traceElement = addElement(TRACE, geomElement);
    traceElement.setTextContent(trace.toString());
    return sectionElement;
  }
}
