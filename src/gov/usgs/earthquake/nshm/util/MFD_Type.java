package gov.usgs.earthquake.nshm.util;

/**
 * NSHMP Fault MFD identifiers.
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum MFD_Type {

  CH(1, "Characteristic"),
  GR(2, "Gutenberg-Richter"),
  GRB0(-2, "Gutenberg-Richter (w/ B=0)");

  private int id;
  private String label;

  private MFD_Type(int id, String label) {
    this.id = id;
    this.label = label;
  }

  public static MFD_Type typeForID(int id) {
    for (MFD_Type ft : MFD_Type.values()) {
      if (ft.id == id) return ft;
    }
    return null;
  }

  @Override
  public String toString() {
    return label;
  }
}
