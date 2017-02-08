package gov.usgs.earthquake.nshm.convert;

import org.w3c.dom.Element;

/*
 * MFD data writing interface.
 * 
 * @author Peter Powers
 */
public interface MFD_Data {

  public Element appendTo(Element parent, MFD_Data ref);

}
