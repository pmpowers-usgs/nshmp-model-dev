package gov.usgs.earthquake.nshm.convert;

import java.util.List;

import gov.usgs.earthquake.nshmp.eq.fault.surface.GriddedSurface;
import gov.usgs.earthquake.nshmp.eq.fault.surface.RuptureSurface;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.geo.Locations;

/*
 * Reduction of CompoundSurface bidiness logic to bare minimum .
 */
class SystemFaultSurface {

  List<? extends GriddedSurface> surfaces;

  // individual trace reversal
  private boolean[] reverseSurfTrace;

  // Aki and Richards total reversal
  private boolean reverseOrderOfSurfaces = false;

  private double rupDip;
  private double rupArea;
  private double rupLength;
  private double rupDepth;
  private double rupWidth;
  private LocationList trace;

  SystemFaultSurface(List<? extends GriddedSurface> surfaces) {
    this.surfaces = surfaces;
    init();

    rupLength = calcRupLength();
    rupDepth = calcRupDepth();
    rupWidth = calcRupWidth();
    trace = deriveTrace();
  }

  private void init() {

    reverseSurfTrace = new boolean[surfaces.size()];

    // determine if either of the first two sections need to be reversed
    GriddedSurface surf1 = surfaces.get(0);
    GriddedSurface surf2 = surfaces.get(1);
    double[] dist = new double[4];
    dist[0] =
        Locations.horzDistanceFast(surf1.getUpperEdge().first(), surf2.getUpperEdge().first());
    dist[1] = Locations.horzDistanceFast(surf1.getUpperEdge().first(), surf2.getUpperEdge().last());
    dist[2] = Locations.horzDistanceFast(surf1.getUpperEdge().last(), surf2.getUpperEdge().first());
    dist[3] = Locations.horzDistanceFast(surf1.getUpperEdge().last(), surf2.getUpperEdge().last());

    double min = dist[0];
    int minIndex = 0;
    for (int i = 1; i < 4; i++) {
      if (dist[i] < min) {
        minIndex = i;
        min = dist[i];
      }
    }

    if (minIndex == 0) { // first_first
      reverseSurfTrace[0] = true;
      reverseSurfTrace[1] = false;
    } else if (minIndex == 1) { // first_last
      reverseSurfTrace[0] = true;
      reverseSurfTrace[1] = true;
    } else if (minIndex == 2) { // last_first
      reverseSurfTrace[0] = false;
      reverseSurfTrace[1] = false;
    } else { // minIndex==3 // last_last
      reverseSurfTrace[0] = false;
      reverseSurfTrace[1] = true;
    }

    // determine which subsequent sections need to be reversed
    for (int i = 1; i < surfaces.size() - 1; i++) {
      surf1 = surfaces.get(i);
      surf2 = surfaces.get(i + 1);
      double d1 =
          Locations.horzDistanceFast(surf1.getUpperEdge().last(), surf2.getUpperEdge().first());
      double d2 =
          Locations.horzDistanceFast(surf1.getUpperEdge().last(), surf2.getUpperEdge().last());
      if (d1 < d2)
        reverseSurfTrace[i + 1] = false;
      else
        reverseSurfTrace[i + 1] = true;
    }

    // compute average dip (wt averaged by area) & total area
    rupDip = 0;
    rupArea = 0;
    for (int s = 0; s < surfaces.size(); s++) {
      RuptureSurface surf = surfaces.get(s);
      double area = surf.area();
      double dip;
      try {
        dip = surf.dip();
      } catch (Exception e) {
        dip = Double.NaN;
      }
      rupArea += area;
      if (reverseSurfTrace[s])
        rupDip += (180 - dip) * area;
      else
        rupDip += dip * area;
    }
    rupDip /= rupArea; // wt averaged by area
    if (rupDip > 90.0) {
      rupDip = 180 - rupDip;
      reverseOrderOfSurfaces = true;
    }

  }

  double rupArea() {
    return rupArea;
  }

  double rupDip() {
    return rupDip;
  }

  double rupLength() {
    return rupLength;
  }

  private double calcRupLength() {
    double length = 0;
    for (RuptureSurface surf : surfaces) {
      length += surf.length();
    }
    return length;
  }

  double rupDepth() {
    return rupDepth;
  }

  private double calcRupDepth() {
    double depth = 0;
    for (RuptureSurface surf : surfaces) {
      depth += surf.depth() * surf.area();
    }
    depth /= rupArea();
    return depth;
  }

  //
  // double rupStrike() {
  // return Faults.strike(trace);
  // }

  double rupWidth() {
    return rupWidth;
  }

  private double calcRupWidth() {
    double width = 0;
    for (RuptureSurface surf : surfaces) {
      width += surf.width() * surf.area();
    }
    width /= rupArea();
    return width;
  }

  LocationList deriveTrace() {
    LocationList.Builder traceBuilder = LocationList.builder();
    for (int s = 0; s < surfaces.size(); s++) {
      LocationList trace = surfaces.get(s).getUpperEdge();
      traceBuilder.addAll(reverseSurfTrace[s] ? trace.reverse() : trace);
    }
    LocationList trace = traceBuilder.build();
    return reverseOrderOfSurfaces ? trace.reverse() : trace;
  }

  //// if(upperEdge == null) {
  // LocationList.Builder upperEdgeBuilder = LocationList.builder();
  //// upperEdge = new FaultTrace(null);
  // if(reverseOrderOfSurfaces) {
  // for(int s=surfaces.size()-1; s>=0;s--) {
  // LocationList trace = surfaces.get(s).getUpperEdge();
  // upperEdgeBuilder.add(reverseSurfTrace[s]
  // ? trace
  // : LocationList.reverseOf(trace));
  //// if(reverseSurfTrace[s]) {
  //// for(int i=0; i<trace.size();i++)
  //// upperEdge.add(trace.get(i));
  //// } else {
  //// for(int i=trace.size()-1; i>=0;i--)
  //// upperEdge.add(trace.get(i));
  //// }
  // }
  // } else { // don't reverse order of surfaces
  // for(int s=0; s<surfaces.size();s++) {
  // LocationList trace = surfaces.get(s).getUpperEdge();
  // upperEdgeBuilder.add(reverseSurfTrace[s]
  // ? LocationList.reverseOf(trace)
  // : trace);
  //// if(reverseSurfTrace[s]) {
  //// for(int i=trace.size()-1; i>=0;i--)
  //// upperEdge.add(trace.get(i));
  //// }
  //// else {
  //// for(int i=0; i<trace.size();i++)
  //// upperEdge.add(trace.get(i));
  //// }
  // }
  // }
  //// }
  // return upperEdge;
  // }

}
