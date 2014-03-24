package gov.usgs.earthquake.nshm.convert;

import static org.opensha.eq.forecast.MFD_Attribute.*;

import java.util.List;

import org.opensha.eq.forecast.SourceElement;
import org.opensha.mfd.MFD_Type;
import org.opensha.util.Parsing;
import org.w3c.dom.Element;

import com.google.common.math.DoubleMath;


/*
 * Wrapper for characteristic MFD data.
 */
class CH_Data implements MFD_Data {
	
	double mag;
	double rate;
	double weight;
	boolean floats;
		
	private CH_Data(double mag, double rate, double weight) {
		this.mag = mag;
		this.rate = rate;
		this.weight = weight;
	}
	
	static CH_Data create(double mag, double rate, double weight) {
		return new CH_Data(mag, rate, weight);
	}

	@Override
	public Element appendTo(Element parent) {
		Element e = Parsing.addElement(SourceElement.MAG_FREQ_DIST, parent);
		e.setAttribute(TYPE.toString(), MFD_Type.SINGLE.name());
		e.setAttribute(A.toString(), Parsing.stripZeros(String.format("%.8g", rate)));
		e.setAttribute(M.toString(), Parsing.stripZeros(String.format("%.3f", mag)));
		e.setAttribute(FLOATS.toString(),Boolean.toString(floats));
		e.setAttribute(WEIGHT.toString(), Double.toString(weight));
		return e;
	}

	@Override
	public Element appendDefaultTo(Element parent) {
		Element e = Parsing.addElement(SourceElement.MAG_FREQ_DIST, parent);
		e.setAttribute(TYPE.toString(), MFD_Type.SINGLE.name());
		e.setAttribute(M.toString(), Parsing.stripZeros(String.format("%.3f", mag)));
		return e;
	}

}
