package gov.usgs.earthquake.nshm.ak;

import static gov.usgs.earthquake.internal.Parsing.addAttribute;
import static gov.usgs.earthquake.internal.Parsing.addElement;
import static gov.usgs.earthquake.internal.SourceAttribute.DEPTH;
import static gov.usgs.earthquake.internal.SourceAttribute.DIP;
import static gov.usgs.earthquake.internal.SourceAttribute.INDICES;
import static gov.usgs.earthquake.internal.SourceAttribute.RAKE;
import static gov.usgs.earthquake.internal.SourceAttribute.WIDTH;
import static gov.usgs.earthquake.internal.SourceElement.GEOMETRY;
import static gov.usgs.earthquake.internal.SourceElement.SOURCE;

import org.w3c.dom.Element;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.util.List;

import gov.usgs.earthquake.nshm.convert.CH_Data;

class Rupture {

  String indices;
  double mag;
  double rate;
  double depth;
  double dip;
  double width;
  double rake;

  Element appendTo(Element parent, CH_Data refCH) {
    Element sourceElem = addElement(SOURCE, parent);
    CH_Data mfdData = CH_Data.create(mag, rate, 1.0, false);
    mfdData.appendTo(sourceElem, refCH);
    Element geomElem = addElement(GEOMETRY, sourceElem);
    addAttribute(DIP, dip, "%.1f", geomElem);
    addAttribute(INDICES, indices, geomElem);
    addAttribute(WIDTH, width, "%.3f", geomElem);
    addAttribute(DEPTH, depth, "%.3f", geomElem);
    addAttribute(RAKE, rake, "%.1f", geomElem);
    return sourceElem;
  }

  Rupture copy() {
    Rupture rupture = new Rupture();
    rupture.indices = this.indices;
    rupture.mag = this.mag;
    rupture.rate = this.rate;
    rupture.depth = this.depth;
    rupture.dip = this.dip;
    rupture.width = this.width;
    rupture.rake = this.rake;
    return rupture;
  }

  static List<Integer> toIndices(List<FaultSection> sections) {
    return FluentIterable.from(sections)
        .transform(new Function<FaultSection, Integer>() {
          @Override
          public Integer apply(FaultSection section) {
            return section.index;
          }
        })
        .toList();
  }

}
