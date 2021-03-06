package gov.usgs.earthquake.nshm.util;

/**
 * NSHMP source region identifier.
 * @author Peter Powers
 */
public enum SourceRegion {

  /** Central and Eastern US region. */
  CEUS("Central & Eastern US"),

  /** Wester US region. */
  WUS("Western US"),

  /** Cascadia region. */
  CASC("Cascadia"),

  /** California region. */
  CA("California");

  private String label;

  private SourceRegion(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }

}
