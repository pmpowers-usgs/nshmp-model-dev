package gov.usgs.earthquake.nshm.convert;

import static org.opensha.eq.model.SourceAttribute.A;
import static org.opensha.eq.model.SourceAttribute.FLOATS;
import static org.opensha.eq.model.SourceAttribute.M;
import static org.opensha.eq.model.SourceAttribute.MAG_SCALING;
import static org.opensha.eq.model.SourceAttribute.TYPE;
import static org.opensha.eq.model.SourceAttribute.WEIGHT;
import static org.opensha.eq.model.SourceElement.INCREMENTAL_MFD;
import static org.opensha.mfd.MfdType.SINGLE;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addElement;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.eq.model.SourceElement;
import org.w3c.dom.Element;

/*
 * Wrapper for characteristic (single) MFD data.
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
			if (rate != refCH.rate) addAttribute(A, rate, "%.8g", e);
			if (mag != refCH.mag) addAttribute(M, mag, "%.3f", e);
			if (floats != refCH.floats) addAttribute(FLOATS, floats, e);
			if (weight != refCH.weight) addAttribute(WEIGHT, weight, e);
		} else {
			addAttribute(A, rate, "%.8g", e);
			addAttribute(M, mag, "%.3f", e);
			addAttribute(FLOATS, floats, e);
			addAttribute(WEIGHT, weight, e);
		}
		return e;
	}
	
}
