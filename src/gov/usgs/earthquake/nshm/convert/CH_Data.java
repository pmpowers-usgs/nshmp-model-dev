package gov.usgs.earthquake.nshm.convert;

import static org.opensha.eq.forecast.SourceAttribute.A;
import static org.opensha.eq.forecast.SourceAttribute.FLOATS;
import static org.opensha.eq.forecast.SourceAttribute.M;
import static org.opensha.eq.forecast.SourceAttribute.MAG_SCALING;
import static org.opensha.eq.forecast.SourceAttribute.TYPE;
import static org.opensha.eq.forecast.SourceAttribute.WEIGHT;
import static org.opensha.eq.forecast.SourceElement.MAG_FREQ_DIST;
import static org.opensha.mfd.MFD_Type.SINGLE;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addElement;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.eq.forecast.SourceElement;
import org.w3c.dom.Element;

/*
 * Wrapper for characteristic (single) MFD data.
 * @author Peter Powers
 */
class CH_Data implements MFD_Data {
	
	double mag;
	double rate;
	double weight;
	boolean floats;
	MagScalingType scaling;
		
	private CH_Data(double mag, double rate, double weight) {
		this.mag = mag;
		this.rate = rate;
		this.weight = weight;
	}
	
	static CH_Data create(double mag, double rate, double weight) {
		return new CH_Data(mag, rate, weight);
	}

	@Override
	public Element appendTo(Element parent, MFD_Data ref) {
		Element e = addElement(MAG_FREQ_DIST, parent);
		// always include type
		addAttribute(TYPE, SINGLE.name(), e);
		// always include rate
		addAttribute(A, rate, "%.8g", e);
		// always include magnitude
		addAttribute(M, mag, "%.3f", e);
		if (ref != null) {
			CH_Data refCH = (CH_Data) ref;
			if (floats != refCH.floats) addAttribute(FLOATS, floats, e);
			if (weight != refCH.weight) addAttribute(WEIGHT, weight, e);
		} else {
			addAttribute(FLOATS, floats, e);
			addAttribute(WEIGHT, weight, e);
			// at present, magScaling does not vary by source group
			addAttribute(MAG_SCALING, scaling.name(), e);
		}
		return e;
	}
	
	@Override
	public Element appendDefaultTo(Element parent) {
		Element e = addElement(SourceElement.MAG_FREQ_DIST, parent);
		addAttribute(TYPE, SINGLE.name(), e);
		addAttribute(M, mag, "%.3f", e);
		return e;
	}

}
