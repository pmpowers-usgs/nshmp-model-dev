package gov.usgs.earthquake.nshm.cous2018;

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import gov.usgs.earthquake.nshmp.geo.Location;

/**
 * Convenience class for working with grid source nodes.
 */
class Node implements Comparable<Node> {

  static final String HEADER = "lon,lat,type,a" + LINE_SEPARATOR.value();
  static final String FORMAT = "%.1f,%.1f,GR,%.8g";

  Location loc;
  double a;

  @Override
  public int compareTo(Node other) {
    return loc.compareTo(other.loc);
  }

  @Override
  public String toString() {
    return String.format(FORMAT, loc.lon(), loc.lat(), a);
  }
}
